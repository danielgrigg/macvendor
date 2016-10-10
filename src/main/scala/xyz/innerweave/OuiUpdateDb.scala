package xyz.innerweave

import java.nio.file.{Files, Path, Paths}

import akka.NotUsed
import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, Sink}
import akka.stream.{ActorMaterializer, IOResult, Materializer}
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success
import scala.util.matching.Regex


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

  final implicit val materializer: ActorMaterializer = ActorMaterializer()(context)

  def receive: PartialFunction[Any, Unit] = {
    case OuiDbUpdate =>
      if (diskCacheExpired) {
        cacheOuiDbToDisk().flatMap {
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

  final val DiskCachePath: Path = Paths.get("oui.csv")
  final val SourceUrl = "http://standards-oui.ieee.org/oui.txt"

  // Cache our compact csv oui db into memory
  def cacheOuiDbToMemory(): Future[OuiDb] = {
    log.info("Caching disk db to memory")
    FileIO.fromPath(DiskCachePath)
      .runWith(OuiDbActor.cacheMemorySink)
      .map(OuiDb)
  }

  // Cache an ieee oui db to disk
  def cacheOuiDbToDisk(): Future[IOResult] = {
    log.info("Caching db to disk")
    Http(context.system).singleRequest(HttpRequest(uri = SourceUrl))
      .flatMap(r =>
        r.entity.dataBytes
          .via(OuiDbActor.ouiLineFlow)
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


// Separate out the functional components - really just to demonstrate
// testing flows/sinks/sources.
object OuiDbActor {

  type MacVendors = Map[Int, String]
  final val DiskCacheRegex: Regex = """([a-fA-F0-9]+)\t(.*)""".r
  final val OuiRegex: Regex = """([a-fA-F0-9]+)[\s\t]*\(base 16\)[\s\t]*(.*)""".r

  // Sink a stream of csv oui-cache data into a Map of vendor-prefixes to vendors.
  def cacheMemorySink(implicit materializer: Materializer): Sink[ByteString, Future[MacVendors]] = {
    Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024, allowTruncation = true)
      .map(_.utf8String)
      .map {
        case DiskCacheRegex(macS, vendor) => Some(Integer.parseInt(macS, 16) -> vendor)
        case _ => None
      }
      .mapConcat(o => o.toList) // Just ignore unparseable elements
      .toMat(Sink.fold(Map.empty[Int, String])(_ + _))(Keep.right)
  }

  // Transform a stream of byte strings representing a single line of the ieee oui database
  // into a stream of vendor-prefix and vendor-name pairs, encoded as tab-delimited strings.
  def ouiLineFlow(implicit materializer: Materializer): Flow[ByteString, String, NotUsed] = {
    Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024, allowTruncation = true)
      .map(_.utf8String)
      .map {
        case OuiRegex(prefix, fullName) => s"$prefix\t$fullName\n"
        case _ => "" // Just drop unparseables
      }
  }
}

