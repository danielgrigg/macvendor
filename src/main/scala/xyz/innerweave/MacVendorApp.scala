package xyz.innerweave

import java.nio.file.{Files, Paths}

import akka.NotUsed
import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, Sink}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.matching.Regex
import scala.util.{Failure, Success}
import akka.pattern.{aka, pipe}

case object OuiUpdateOp
case class OuiGetOp(macPrefix: Long)


class OuiDb extends Actor with akka.actor.ActorLogging {
  import MacVendorApp.system.dispatcher

  def receive = {
    case OuiUpdateOp =>
      val cachedDbFuture = if (shouldUpdate) {
        Http().singleRequest(HttpRequest(uri = url))
          .flatMap(r =>
            r.entity.dataBytes
              .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024, allowTruncation = true))
              .map(_.utf8String)
              .map(s => regex.findFirstMatchIn(s) match {
                case Some(regex(prefix, fullName)) => s"$prefix\t$fullName\n"
                case None => ""
              })
              .runWith(lineSink(csv("oui")))
          ).map(_.wasSuccessful)
      } else {
        Future.successful(true)
      }
//      cachedDbFuture.map(result => )
  }

  def lineSink(filename: String): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s))
      .toMat(FileIO.toPath(Paths.get(filename)))(Keep.right)

  val name = "oui"
  val db = name + ".csv"
  val regex = """([a-zA-Z0-9]+)[\s\t]*\(base 16\)[\s\t]*(.*)""".r
  val url = "http://localhost:8080/oui.txt"

  def shouldUpdate: Boolean = {
    val path = Paths.get(db)
    val oneWeek = 7L * 24L * 3600L * 1000L
    !Files.exists(path) ||
      (DateTime.now.clicks - Files.getLastModifiedTime(path).toMillis) > oneWeek
  }
}

object MacVendorApp extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  def logFlow = Flow[String].map { s => println(s); s }



  f.onComplete {
    case Success(x) => println(s"$x successful")
    case Failure(ex) => println("Failed: " + ex.getMessage)
  }

  StdIn.readLine()
  system.terminate()
}
