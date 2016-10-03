package xyz.innerweave

import java.nio.file.{Files, Path, Paths}

import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, Sink}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success


/**
  * Request a refresh of the mac-vendor database
  */
case object OuiDbUpdate

/**
  * Load the cached db
  */
case object OuiDbLoad

/**
  * Database is current
  *
  * Sent in response to an OuiDbUpdate request.  Signals that the current
  * cached db is valid.
  */
case object OuiDbCurrent

/**
  * A mac-vendor db
  *
  * Sent in response to OuiDbLoad and OuiDbUpdate requests.  Contains
  * all mac to vendor assocations.
  */
case class OuiDb(cache: Map[Int, String])

/**
  * Maintains an associative cache of MAC vendor prefixes
  * to vendor names.
  *
  * The cache refreshes itself periodically after
  * a short initial delay.
  */
class OuiDbActor(diskCacheExpiry: Duration) extends Actor with akka.actor.ActorLogging {

  import context._

  final implicit val materializer = ActorMaterializer()(context)

  def receive = {
    case OuiDbUpdate =>
      if (diskCacheExpired) {
        cacheOuiDbToDisk().flatMap{
          _.status match {
            case Success(_) => cacheOuiDbToMemory()
            case util.Failure(ex) => Future.failed(ex)
          }
        }.pipeTo(sender)
      } else {
        sender ! OuiDbCurrent
      }

    case OuiDbLoad => cacheOuiDbToMemory().pipeTo(sender)
  }

  final val DiskCachePath = Paths.get("oui.csv")
  final val OuiRegex = """([a-zA-Z0-9]+)[\s\t]*\(base 16\)[\s\t]*(.*)""".r
  final val DiskCacheRegex = """(.*)\t(.*)""".r
  final val SourceUrl = "http://standards-oui.ieee.org/oui.txt"

  def cacheOuiDbToMemory() = {
    log.info("Caching disk db to memory")
    FileIO.fromPath(DiskCachePath)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024, allowTruncation = true))
      .map(_.utf8String)
      .runFold(Map[Int, String]()) { case (acc, s) =>
        DiskCacheRegex.findFirstIn(s) match {
          case None => acc
          case Some(DiskCacheRegex(macStr, vendor)) =>
            val mac = Integer.parseUnsignedInt(macStr, 16)
            acc + (mac -> vendor)
        }
      }
      .map(OuiDb)
  }

  def cacheOuiDbToDisk(): Future[IOResult] = {
    log.info("Caching db to disk")
    Http(context.system).singleRequest(HttpRequest(uri = SourceUrl))
      .flatMap(r =>
        r.entity.dataBytes
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024, allowTruncation = true))
          .map(_.utf8String)
          .map(s => OuiRegex.findFirstMatchIn(s) match {
            case Some(OuiRegex(prefix, fullName)) => s"$prefix\t$fullName\n"
            case None => ""
          })
          .runWith(lineSink(DiskCachePath))
      )
  }

  def lineSink(path: Path): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s))
      .toMat(FileIO.toPath(path))(Keep.right)

  def diskCacheExpired: Boolean = {
    lazy val ageMillis = DateTime.now.clicks - Files.getLastModifiedTime(DiskCachePath).toMillis
    !Files.exists(DiskCachePath) || ageMillis > diskCacheExpiry.toMillis
  }
}

