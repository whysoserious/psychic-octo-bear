package com.deeprecursion.mft

import java.net.{InetSocketAddress, SocketAddress}
import java.util.UUID

import akka.actor._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.github.levkhomich.akka.tracing.{ActorTracing, TracingSupport}
import com.twitter.finagle.Service
import com.twitter.finagle.builder.{ClientBuilder, Server, ServerBuilder}
import com.twitter.finagle.http.Http
import com.twitter.util.{Time, Future}
import com.typesafe.config._
import net.liftweb.json._
import org.jboss.netty.handler.codec.http.{DefaultHttpRequest, DefaultHttpResponse, HttpMethod, HttpRequest, HttpResponse, HttpResponseStatus, HttpVersion}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Random, Try}

import HttpVersion.HTTP_1_1
import HttpMethod.GET


case class Put(id: String) extends TracingSupport {
  val name = productPrefix
}

case class Ack(id: String, responseCode: Int) extends TracingSupport {
  val name = productPrefix
}

/* The service backend
 */
class ServiceActor extends Actor with ActorTracing {

  implicit val askTimeout: Timeout = 200.milliseconds
  implicit val formats = DefaultFormats

  override def preStart(): Unit = {
    println("Starting ServiceActor")
  }

  def receive = {

    case msg @ Put(id) =>
      trace.sample(msg, "psychic-octo-bear")

      val s3Path = "akka.tcp://backend@127.0.0.1:2554/user/s3"
      val dst = context.actorSelection(s3Path)
      val label = msg.name + " " + id
      trace.recordKeyValue(msg, self.path.name, label)
      trace.record(msg, id)

      println("\t\t" + self.path.name + " received Put: " + id)
      println("\t\t" + self.path.name + " calls " + s3Path + ": " + id)
      // use asChildOf to continue the span
      import context.dispatcher
      dst ? Put(id).asChildOf(msg) recover {
        case e: Exception =>
          // trace exception
          trace.record(msg, e.toString)
          println("\t\t" + self.path.name + " error: " + id)
          Ack(id, 500)
      } map {
        case ack @ Ack(id, responseCode) =>
          println("\t\t" + self.path.name + " received Ack with code: "+
            responseCode + " and id: " + id)
          // close trace by marking response
          println("\t\t" + self.path.name + " acks: " + id)
          ack.asResponseTo(msg)
      } pipeTo sender

    case _  =>

  }
}

class S3Actor extends Actor with ActorTracing {
  implicit val askTimeout: Timeout = 100.milliseconds
  implicit val formats = DefaultFormats

  val httpClient = ClientBuilder()
    .codec(Http())
    .hosts("localhost:7838")
    .build()

  override def preStart(): Unit = {
    println("Starting S3Actor")
  }

  override def postStop(): Unit = {
    httpClient.close(Time.Bottom)
  }

  def receive = {

    case msg @ Put(id) => handleMessage(msg, sender())

  }

  def handleMessage(msg: Put, sndr: ActorRef): Unit = {
    println("\t\t\t" + self.path.name + " received Put: " + msg.id)
    trace.sample(msg, "S3Actor")

    // introduce random timeouts
    val t = Random.nextInt(400)
    println("\t\t\tSleeping for " + t + " milliseconds....")
    Thread.sleep(t)

    val httpRequest = new DefaultHttpRequest(HTTP_1_1, GET, s"/$msg.id}")

    httpClient(httpRequest) onSuccess { res =>
      val id = new String(res.getContent.array)
      val ack = Ack(id, 200)
      val label = ack.name + " " + ack.id
      trace.recordKeyValue(msg, self.path.name, label)
      trace.record(ack, ack.id)
      println("\t\t\t" + self.path.name + " acks: " + id)
      sndr ! ack.asResponseTo(msg)
    } onFailure { exc =>
      println("failed :-(", exc)
      val ack = Ack(msg.id, 500)
      trace.record(ack, exc)
      sndr ! ack.asResponseTo(msg)
    }
  }

}

/* An web app
 */
class WebActor extends Actor with ActorTracing {

  implicit val askTimeout: Timeout = 1000.milliseconds
  implicit val formats = DefaultFormats

  override def preStart(): Unit = {
    println("Starting WebActor")
  }

  def receive = {
    case msg @ Put(id) =>
      trace.sample(msg, "WebActor")

      val servicePath = "akka.tcp://frontend@127.0.0.1:2553/user/service"
      val dst = context.actorSelection(servicePath)
      val label = msg.name + " " + id
      trace.recordKeyValue(msg, self.path.name, label)
      trace.record(msg, id)

      println("\t" + self.path.name + " received Put: " + id)
      println("\t" + self.path.name + " calls " + servicePath + ": " + id)
      import context.dispatcher
      dst ? Put(id).asChildOf(msg) recover {
        case e: Exception =>
          // trace exception
          trace.record(msg, e.toString)
          println("\tAck error: " + id)
          Ack(id, 500)
      } map {
        case ack @ Ack(id, responseCode) =>
          println("\t" + self.path.name + " received Ack with code: "+
            responseCode + " and id: " + id)
          // close trace by marking response
          println("\t" + self.path.name + " acks: " + id)
          ack.asResponseTo(msg)
      } pipeTo sender

    case _ =>

  }
}

object HttpUtils {

  def client: Service[HttpRequest, HttpResponse] = {
    ClientBuilder()
      .codec(Http())
      .hosts("localhost:7838")
      .build()
  }

  def server: Server = {

    // Define our service: OK response for root, 404 for other paths
    val rootService = new Service[HttpRequest, HttpResponse] {
      def apply(request: HttpRequest) = {
        val r = request.getUri match {
          case "/" => new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
          case _ => new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
        }
        Future.value(r)
      }
    }

    // Serve our service on a port
    val address: SocketAddress = new InetSocketAddress(7838)

    ServerBuilder()
      .codec(Http())
      .bindTo(address)
      .name("HttpServer")
      .build(rootService)
  }

}

object PsychicOctoBear extends App {
  implicit val askTimeout: Timeout = 1.second

  val config = ConfigFactory.load("psychic-octo-bear.conf")

  // Start first ActorSystem

  val frontendSystem = ActorSystem("frontend", config.getConfig("frontend").withFallback(config))

  val web = frontendSystem.actorOf(Props[WebActor], name = "web")
  val service = frontendSystem.actorOf(Props[ServiceActor], name = "service")

  // Start second ActorSystem

  val backendSystem = ActorSystem("backend", config.getConfig("backend").withFallback(config))

  val s3 = backendSystem.actorOf(Props[S3Actor], name = "s3")

  Thread.sleep(2000)

  Try {

    // send messages
    for (_ <- 1 to 10) {
      val uuid = UUID.randomUUID().toString
      println("Call Web: " + uuid)
      val future = web ? Put(uuid)
      val result = Await.result(future, askTimeout.duration).asInstanceOf[Ack]
      println("Ack with code: " + result.responseCode + " and id: " + result.id)
      println()
      Thread.sleep(1000)
    }

  }

  backendSystem.awaitTermination(1.second)
  frontendSystem.awaitTermination(1.second)

  backendSystem.shutdown()
  frontendSystem.shutdown()

}
