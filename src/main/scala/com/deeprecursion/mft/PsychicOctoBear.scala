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


object PsychicOctoBear extends App {
  implicit val askTimeout: Timeout = 1.second

  val config = ConfigFactory.load("psychic-octo-bear.conf")

  // Start first ActorSystem

  val frontendSystem = ActorSystem("frontend", config.getConfig("frontend").withFallback(config))

  val web = frontendSystem.actorOf(Props[FrontendActor], name = "frontend")
  val service = frontendSystem.actorOf(Props[ServiceActor], name = "service")

  // Start second ActorSystem

  val backendSystem = ActorSystem("backend", config.getConfig("backend").withFallback(config))

  // Start HTTP server

  val server = S3Server.server()

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

  server.close(Time.Bottom)
  
  backendSystem.awaitTermination(1.second)
  frontendSystem.awaitTermination(1.second)

  backendSystem.shutdown()
  frontendSystem.shutdown()

}
