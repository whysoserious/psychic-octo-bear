package com.deeprecursion.mft

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.charset.Charset

import com.twitter.finagle.Service
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http.Http
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

import scala.util.Random

object S3Server {

  val utf8 = Charset.forName("UTF-8")

  def server: Server = {

    val rootService = new Service[HttpRequest, HttpResponse] {
      def apply(request: HttpRequest): Future[HttpResponse] = {
        val randomSleepTime = Random.nextInt(400)
        println(s"\t\t\tReceived ${request.getUri}")
        println(s"\t\t\tSleeping for $randomSleepTime ms")
        Thread.sleep(randomSleepTime)
        //remove trailing slash
        val id: String = request.getUri.drop(1)
        val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        response.setContent(ChannelBuffers.copiedBuffer(id, utf8))
        Future.value(response)
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

}
