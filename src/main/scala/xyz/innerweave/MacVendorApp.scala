package xyz.innerweave

import java.nio.file.{Files, Path, Paths}

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.{ByteString, Timeout}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.matching.Regex
import scala.util.{Failure, Success}
import akka.pattern.{ask, pipe}

case object OuiUpdate
case class OuiGet(macPrefix: Int)
case class OuiGetResponse(vendor: Option[String])
case class OuiUpdateResult(cache: Map[Int, String])


class OuiDb extends Actor with akka.actor.ActorLogging {
  import context.dispatcher
  final implicit val materializer = ActorMaterializer()

  def receive = {
    case OuiUpdate =>
      val cachedDbFuture = if (shouldUpdate) {
        Http(context.system).singleRequest(HttpRequest(uri = url))
          .flatMap(r =>
            r.entity.dataBytes
              .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024, allowTruncation = true))
              .map(_.utf8String)
              .map(s => regex.findFirstMatchIn(s) match {
                case Some(regex(prefix, fullName)) => s"$prefix\t$fullName\n"
                case None => ""
              })
              .runWith(lineSink(dbPath))
          ).map(_.wasSuccessful)
      } else {
        Future.successful(true)
      }
      cachedDbFuture.flatMap{result =>
        FileIO.fromPath(dbPath)
          .via(Framing.delimiter( ByteString("\n"), maximumFrameLength = 1024, allowTruncation = true))
          .map(_.utf8String)
          .runFold(Map[Int, String]()){ case (acc, s) =>
            csvRegex.findFirstIn(s) match {
              case None => acc
              case Some(csvRegex(macStr, vendor)) =>
                val mac = Integer.parseUnsignedInt(macStr, 16)
                acc + (mac -> vendor)
            }
          }
      }.map(OuiUpdateResult).pipeTo(self)

    case OuiUpdateResult(newCache) =>
      cache = newCache
      println("updated")

    case OuiGet(prefix) =>
      sender() ! OuiGetResponse(cache.get(prefix))
  }

  var cache: Map[Int, String] = Map.empty

  def lineSink(path: Path): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s))
      .toMat(FileIO.toPath(path))(Keep.right)

  val name = "oui"
  val dbPath = Paths.get(name + ".csv")
  val regex = """([a-zA-Z0-9]+)[\s\t]*\(base 16\)[\s\t]*(.*)""".r
  val csvRegex = """(.*)\t(.*)""".r
  val url = "http://localhost:8080/oui.txt"

  def shouldUpdate: Boolean = {
    val oneWeek = 7L * 24L * 3600L * 1000L
    !Files.exists(dbPath) ||
      (DateTime.now.clicks - Files.getLastModifiedTime(dbPath).toMillis) > oneWeek
  }
}

class FooActor extends Actor with akka.actor.ActorLogging {
  import context.dispatcher

  final implicit val materializer = ActorMaterializer()
  val url = "http://localhost:8080/oui.txt"

  def receive = {
    case OuiUpdate =>
      Http(context.system).singleRequest(HttpRequest(uri = url))
      println("OuiUpdate done")
  }
}

object MacVendorApp extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(10.seconds)

//  def logFlow = Flow[String].map { s => println(s); s }

  val foo = system.actorOf(Props(new FooActor), "foo")
  foo ! OuiUpdate

//  val actor = system.actorOf(Props(new OuiDb))
//  actor ! OuiUpdate
//  StdIn.readLine()
//  actor.ask(OuiGet(0xE043DB)).mapTo[OuiGetResponse].onComplete {
//    case Success(r) => println(r)
//    case Failure(ex) => println(ex.getMessage)
//  }

  StdIn.readLine()
  system.terminate()
}
