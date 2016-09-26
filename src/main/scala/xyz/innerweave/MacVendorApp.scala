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

object MacVendorApp extends App {

  implicit val system = ActorSystem("MacVendor")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(1.seconds)

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
    path("vendor") {
      get {
        parameters('mac.as[String]) { (macStr) =>
          val mac = Integer.parseUnsignedInt(macStr.replaceAll("[:-]", ""), 16)
          onSuccess(supervisor.ask(OuiGet(mac))) {
            case OuiGetResponse(Some(vendor)) =>
              complete(s"""{"vendor":"$vendor"}""")
            case OuiGetResponse(None) =>  ???
            case _ => ???
          }
        }
      }
    }

  val config = ConfigFactory.load()
  val (iface, port) = (config.getString("http.interface"), config.getInt("http.port"))

  Http().bindAndHandle(route, iface, port).onComplete {
    case scala.util.Success(binding) =>
      println(s"Server online at $iface:$port")
    case scala.util.Failure(ex) =>
      println(s"Failed to bind to $iface:$port: ${ex.getMessage}")
      finish()
  }

  def finish() = {
    println("Shutting down")
    system.terminate()
    Await.result(system.whenTerminated, 30.seconds)
    println("Terminated")
  }
}
