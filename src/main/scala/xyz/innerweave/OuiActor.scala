package xyz.innerweave

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.model.DateTime
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._
import scala.util
import scala.util.Success

/**
  * Request the vendor name for a mac prefix
  * @param macPrefix a 3B vendor prefix, eg 0xAABBCC
  */
case class OuiGet(macPrefix: Int)

sealed trait OuiGetResponse
/**
  * The vendor name for a corresponding OuiGet request.
  * @param vendor the Oui name
  */
case class OuiGetOk(vendor: Option[String]) extends OuiGetResponse
case object OuiGetNotReady extends OuiGetResponse

class OuiActor extends Actor with ActorLogging {

  import context._

  final val DiskCacheExpiry = 7.day
  final val UpdateDelay = 2.day
  final val FirstUpdateDelay = 10.seconds

  override def preStart() = {
    ouiDb ! OuiDbLoad
  }

  def resuming: Receive = {
    case OuiGet(prefix) => sender() ! OuiGetNotReady

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
    case OuiGet(prefix) => sender() ! OuiGetOk(cache.get(prefix))

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

  val childProps = Props(new OuiDbActor(DiskCacheExpiry))
  val supervisorProps = BackoffSupervisor.props(
    Backoff.onFailure(
      childProps,
      childName = "OuiDb",
      minBackoff = 3.seconds,
      maxBackoff = 1.hour,
      randomFactor = 0.2))

  val ouiDb = actorOf(supervisorProps, "OuiDbSupervisor")
}
