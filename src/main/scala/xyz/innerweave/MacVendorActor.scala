package xyz.innerweave

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.model.DateTime
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._

/**
  * Request the vendor name for a mac prefix
  *
  * @param macPrefix a 3B vendor prefix, eg 0xAABBCC
  */
case class VendorGet(macPrefix: Int)

sealed trait VendorGetResponse

/**
  * The vendor name for a corresponding VendorGet request.
  *
  * @param vendor the Oui name
  */
case class VendorGetOk(vendor: Option[String]) extends VendorGetResponse

case object VendorGetNotReady extends VendorGetResponse

/**
  * The main behaviour of the MacVendor service.
  *
  * MacVendorActor is responsible for answering queries
  * on the vendor associated with a MAC address.
  *
  * Queries are performed by sending an VendorGet message a MAC prefix.
  * Responses are returned in a VendorGetResponse.
  *
  * The actor has two states, running and resuming.  In a running state
  * queries are processed normally.  In a resuming state, the service is
  * considered unavailable and queries will be gracefully rejected.
  */
class MacVendorActor extends Actor with ActorLogging {

  import context._

  final val DiskCacheExpiry = 7.day
  final val UpdateDelay = 2.day
  final val FirstUpdateDelay = 10.seconds

  override def preStart() = {
    ouiDb ! OuiDbLoad
  }

  def resuming: Receive = {
    case VendorGet(prefix) => sender() ! VendorGetNotReady

    case OuiDb(newCache) =>
      cache = newCache
      log.info("Loaded cached db with {} entries. Next update scheduled for {}.",
        newCache.size, DateTime.now.plus(FirstUpdateDelay.toMillis))
      system.scheduler.scheduleOnce(FirstUpdateDelay, ouiDb, OuiDbUpdate)
      become(running)

    case Failure(ex) =>
      log.warning("Failed loading cached db: {}. Scheduling update.", ex.getMessage)
      system.scheduler.scheduleOnce(FirstUpdateDelay, ouiDb, OuiDbUpdate)
      become(running)
  }

  def running: Receive = {
    case VendorGet(prefix) => sender() ! VendorGetOk(cache.get(prefix))

    case OuiDb(newCache) =>
      cache = newCache
      log.info("Updated db with {} entries. Next update scheduled for {}.",
        newCache.size, DateTime.now.plus(UpdateDelay.toMillis))
      system.scheduler.scheduleOnce(UpdateDelay, ouiDb, OuiDbUpdate)

    case OuiDbCurrent =>
      log.info("Continuing with current db. Next update check scheduled for {}.",
        DateTime.now.plus(UpdateDelay.toMillis))
      system.scheduler.scheduleOnce(UpdateDelay, ouiDb, OuiDbUpdate)

    case Failure(ex) => throw ex
  }

  def receive = resuming

  var cache: Map[Int, String] = Map.empty

  final val SourceUrl = "http://linuxnet.ca/ieee/oui.txt.gz"

  val childProps = Props(new OuiDbActor(SourceUrl, DiskCacheExpiry, "."))
  val supervisorProps = BackoffSupervisor.props(
    Backoff.onFailure(
      childProps,
      childName = "OuiDb",
      minBackoff = 3.seconds,
      maxBackoff = 1.hour,
      randomFactor = 0.2))

  val ouiDb = actorOf(supervisorProps, "OuiDbSupervisor")
}
