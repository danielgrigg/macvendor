package xyz.innerweave

import java.nio.file.{Files, Path, Paths}

import akka.actor.Actor
import akka.actor.Status.Failure
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, Sink}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString

import scala.concurrent.Future


/**
  * Request a refresh of the mac-vendor database
  */
case object OuiUpdate

/**
  * A refreshed cache
  * @param cache
  */
case class OuiUpdateResult(cache: Map[Int, String])

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


class OuiDbActor extends Actor with akka.actor.ActorLogging {

  import context.dispatcher

  final implicit val materializer = ActorMaterializer()

  def receive = {
    case OuiUpdate =>
      val updateResultFuture = for {
        _ <- if (refreshDue) cacheOuiDbToDisk() else Future.successful(true)
        newCache <- cacheOuiDbToMemory()
      } yield OuiUpdateResult(newCache)
      updateResultFuture.pipeTo(self)

      // Re-throw any Failure updating the db
    case Failure(ex) => throw ex

    case OuiUpdateResult(newCache) => {
      log.info("Oui cache updated")
      cache = newCache
    }

    case OuiGet(prefix) => sender() ! OuiGetResponse(cache.get(prefix))
  }

  var cache: Map[Int, String] = Map.empty
  val diskCachePath = Paths.get("oui.csv")
  val ouiRegex = """([a-zA-Z0-9]+)[\s\t]*\(base 16\)[\s\t]*(.*)""".r
  val diskCacheRegex = """(.*)\t(.*)""".r
  val sourceUrl = "http://standards-oui.ieee.org/oui.txt"

  def cacheOuiDbToMemory() = {
    FileIO.fromPath(diskCachePath)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024, allowTruncation = true))
      .map(_.utf8String)
      .runFold(Map[Int, String]()) { case (acc, s) =>
        diskCacheRegex.findFirstIn(s) match {
          case None => acc
          case Some(diskCacheRegex(macStr, vendor)) =>
            val mac = Integer.parseUnsignedInt(macStr, 16)
            acc + (mac -> vendor)
        }
      }
  }

  def cacheOuiDbToDisk(): Future[Boolean] = {
    Http(context.system).singleRequest(HttpRequest(uri = sourceUrl))
      .flatMap(r =>
        r.entity.dataBytes
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024, allowTruncation = true))
          .map(_.utf8String)
          .map(s => ouiRegex.findFirstMatchIn(s) match {
            case Some(ouiRegex(prefix, fullName)) => s"$prefix\t$fullName\n"
            case None => ""
          })
          .runWith(lineSink(diskCachePath))
      ).map(_.wasSuccessful)
  }

  def lineSink(path: Path): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s))
      .toMat(FileIO.toPath(path))(Keep.right)

  /**
    * Refresh if the disk cache doesn't exist or is older than a week.
    */
  def refreshDue: Boolean = {
    val oneWeekMillis = 7L * 24L * 3600L * 1000L
    lazy val ageMillis = DateTime.now.clicks - Files.getLastModifiedTime(diskCachePath).toMillis
    !Files.exists(diskCachePath) || ageMillis > oneWeekMillis
  }

  // Ensure the cache is refreshed when the actor starts
  self ! OuiUpdate
}

