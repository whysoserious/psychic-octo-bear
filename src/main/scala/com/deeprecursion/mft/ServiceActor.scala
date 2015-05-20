package com.deeprecursion.mft

import akka.actor._
import com.github.levkhomich.akka.tracing.ActorTracing
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.Http
import com.twitter.util.{Duration, Future}
import net.liftweb.json.DefaultFormats
import org.jboss.netty.handler.codec.http.HttpMethod.GET
import org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
import org.jboss.netty.handler.codec.http.{DefaultHttpRequest, HttpRequest, HttpResponse}

class ServiceActor extends Actor with ActorTracing {

  implicit val httpQueryTimeout: Duration = Duration.fromMilliseconds(200)
  implicit val formats = DefaultFormats

  val host: String = "localhost:7838"

  lazy val httpClient: Service[HttpRequest, HttpResponse] = {
    ClientBuilder()
      .codec(Http())
      .hosts(host)
      .timeout(httpQueryTimeout)
      .hostConnectionLimit(1)
      .build()
  }

  override def preStart(): Unit = {
    println("Starting ServiceActor")
  }

  def receive = {

    case incomingMsg @ Put(id) =>
      trace.sample(incomingMsg, "ServiceActor")
      trace.recordKeyValue(incomingMsg, self.path.name, incomingMsg.name + " " + id)
      trace.record(incomingMsg, id)

      val uri = s"/$id"

      println("\t\t" + self.path.name + " received Put: " + id)
      println("\t\t" + self.path.name + " uri: " + uri)

      val httpRequest = new DefaultHttpRequest(HTTP_1_1, GET, s"$id")
      handleHttpResponse(httpClient(httpRequest), sender(), incomingMsg)

  }

  def handleHttpResponse(response: Future[HttpResponse], sndr: ActorRef, incomingMsg: Put): Unit = {
    response onSuccess { res =>
      val id = new String(res.getContent.array)
      val ack = Ack(id, 200)
      val label = ack.name + " " + ack.id
      trace.recordKeyValue(incomingMsg, self.path.name, label)
      trace.record(ack, ack.id)
      println("\t\t" + self.path.name + " acks: " + id)
      sndr ! ack.asResponseTo(incomingMsg)
    } onFailure { exc =>
      println("\t\tfailed", exc)
      val ack = Ack(incomingMsg.id, 500)
      trace.record(ack, exc)
      sndr ! ack.asResponseTo(incomingMsg)
    }
  }

}
