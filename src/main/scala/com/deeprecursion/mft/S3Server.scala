package com.deeprecursion.mft

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.charset.Charset

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.github.levkhomich.akka.tracing.ActorTracing
import com.twitter.finagle.Service
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http.Http
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

import scala.concurrent.duration._
import scala.concurrent.{Await => AkkaAwait, Future => AkkaFuture}
import scala.util.Random

object S3Server {

  val utf8 = Charset.forName("UTF-8")

  def server(actorSystem: ActorSystem): Server = {

    import actorSystem.dispatcher

    implicit val timeout: Timeout = 1.second

    val s3Actor = actorSystem.actorOf(Props[S3Actor], name = "s3")

    val rootService = new Service[HttpRequest, HttpResponse] {

      def apply(request: HttpRequest): Future[HttpResponse] = {

        println(s"\t\t\t S3Server received ${request.getUri}")

        //remove trailing slash
        val id: String = request.getUri.drop(1)

        val response: AkkaFuture[HttpResponse] = (s3Actor ? Put(id)).mapTo[Ack] map { ack =>
          val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
          response.setContent(ChannelBuffers.copiedBuffer(ack.id, utf8))
          response
        } recover {
          case _ =>  new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
        }

        Future.value(AkkaAwait.result(response, 1.second))

      }
    }

    // Serve our service on a port
    val address: SocketAddress = new InetSocketAddress(7838)

    println("Starting S3 HttpServer")

    ServerBuilder()
      .codec(Http())
      .bindTo(address)
      .name("HttpServer")
      .build(rootService)
  }

  class S3Actor extends Actor with ActorTracing {

    override def preStart(): Unit = {
      println("Starting S3Actor")
    }

    def receive = {

      case incomingMsg @ Put(id) =>

        trace.sample(incomingMsg, "S3Actor")
        trace.recordKeyValue(incomingMsg, self.path.name, incomingMsg.name + " " + id)
        trace.record(incomingMsg, id)

        val randomSleepTime = Random.nextInt(400)
        println(s"\t\t\t\tS3Actor ${self.path.name} received Put: $id")
        println(s"\t\t\t\tS3Actor Sleeping for $randomSleepTime ms")
        Thread.sleep(randomSleepTime)

        sender() ! Ack(incomingMsg.id, 200).asResponseTo(incomingMsg)

      case x => unhandled(x)

    }

  }

}
