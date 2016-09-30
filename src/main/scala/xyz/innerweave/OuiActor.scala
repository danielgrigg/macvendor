package xyz.innerweave

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.model.DateTime
import akka.pattern.{Backoff, BackoffSupervisor}
import scala.concurrent.duration._

/**
  * Request the vendor name for a mac prefix
  * @param macPrefix a 3B vendor prefix, eg 0xAABBCC
  */
case class OuiGet(macPrefix: Int)

/**
  * The vendor name for a corresponding OuiGet request.
  * @param vendor the Oui name
  */
case class OuiGetResponse(vendor: Option[String])

class OuiActor extends Actor with ActorLogging {

  import context._

  final val DiskCacheExpiry = 7.day
  final val UpdateDelay = 2.day

  override def preStart() = {
    ouiDb ! OuiUpdate
  }

  def receive = {
    case OuiGet(prefix) => sender() ! OuiGetResponse(cache.get(prefix))

    case OuiUpdateResult(newCache) =>
      cache = newCache
      log.info("Updated db with {} entries. Next update scheduled for {}.",
        newCache.size, DateTime.now.plus(UpdateDelay.toMillis))
      system.scheduler.scheduleOnce(UpdateDelay, ouiDb, OuiUpdate)
  }

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
