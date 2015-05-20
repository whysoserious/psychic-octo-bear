package com.deeprecursion.mft

import akka.actor.Actor
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.github.levkhomich.akka.tracing.ActorTracing
import net.liftweb.json.DefaultFormats

import scala.concurrent.duration._

class FrontendActor extends Actor with ActorTracing {

  implicit val askTimeout: Timeout = 1000.milliseconds
  implicit val formats = DefaultFormats

  val serviceActorPath = "akka.tcp://frontend@127.0.0.1:2553/user/service"

  override def preStart(): Unit = {
    println("Starting WebActor")
  }

  def receive = {

    case incomingMsg @ Put(id) =>

      trace.sample(incomingMsg, "FrontendActor")
      trace.recordKeyValue(incomingMsg, self.path.name, incomingMsg.name + " " + id)
      trace.record(incomingMsg, id)

      val serviceActor = context.actorSelection(serviceActorPath)

      println("\t" + self.path.name + " received Put: " + id)
      println("\t" + self.path.name + " calls " + serviceActorPath + ": " + id)

      import context.dispatcher

      val serviceQuery = serviceActor ? Put(id).asChildOf(incomingMsg)
      serviceQuery.mapTo[Ack] map handleAck(incomingMsg) recover exceptionHandler(incomingMsg) pipeTo sender()

  }

  def handleAck(msg: Put)(ack: Ack): Ack = {
    println("\t" + self.path.name + " received Ack with code: "+ ack.responseCode + " and id: " + ack.id)
    // close trace by marking response
    println("\t" + self.path.name + " acks: " + ack.id)
    ack.asResponseTo(msg)
  }

  def exceptionHandler(msg: Put): PartialFunction[Throwable, Ack] = {
    case e: Exception =>
      // trace exception
      trace.record(msg, e.toString)
      println("\tAck error: " + msg.id)
      Ack(msg.id, 500).asResponseTo(msg)
  }

}
