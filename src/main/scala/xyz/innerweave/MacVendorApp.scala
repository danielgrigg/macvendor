package xyz.innerweave

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.{Backoff, BackoffSupervisor, ask}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import akka.event.Logging

object MacVendorApp extends App {

  implicit val system = ActorSystem("MacVendor")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(1.seconds)

  val log = Logging.getLogger(system, this)

  scala.sys.addShutdownHook {
    finish()
  }

  val childProps = Props(classOf[OuiDbActor])
  val supervisorProps = BackoffSupervisor.props(
    Backoff.onFailure(
      childProps,
      childName = "OuiDb",
      minBackoff = 3.seconds,
      maxBackoff = 1.hour,
      randomFactor = 0.2))

  val supervisor = system.actorOf(supervisorProps, "supervisor")

  val route: Route =
    rejectEmptyResponse {
      path("vendor") {
        get {
          parameters('prefix.as[String]) { (prefixLike) =>
            val prefix: Option[Int] = parseVendorPrefix(prefixLike)
            validate(prefix.isDefined,
              "prefix must be a hexadecimal vendor prefix with an optional ':' delimiter, eg AA:BB:CC or AABBCC112233.") {
              complete {
                supervisor
                  .ask(OuiGet(prefix.get))
                  .mapTo[OuiGetResponse]
                  .map(_.vendor)
              }
            }
          }
        }
      } ~
      // Some basic service discovery
        complete("Try /vendor?prefix=$prefix")
    }

  val config = ConfigFactory.load()
  val (iface, port) = (config.getString("http.interface"), config.getInt("http.port"))

  Http().bindAndHandle(route, iface, port).onComplete {
    case scala.util.Success(binding) =>
      log.info(s"Server online at $iface:$port")
    case scala.util.Failure(ex) =>
      log.error(s"Failed to bind to $iface:$port: ${ex.getMessage}")
      finish()
  }

  // Extract the vendor from a mac-like string, eg aa:bb:cc or aabbccddeeff
  def parseVendorPrefix(macLike: String): Option[Int] = {
    Try(Integer.parseInt(macLike.filterNot(_ == ':').take(6), 16)).toOption
  }

  def finish() = {
    log.info("Application shutting down")
    system.terminate()
    Await.result(system.whenTerminated, 30.seconds)
    log.info("Application Terminated")
  }
}
