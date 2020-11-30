package com.github.panchmp.ip.verticle

import java.io.File
import java.net.InetAddress

import com.maxmind.db.Reader
import com.typesafe.scalalogging.StrictLogging
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.eventbus.Message

import scala.util.{Failure, Success, Try}

class MaxMindService extends ScalaVerticle with StrictLogging {
  private var reader: Option[Reader] = Option.empty

  override def start(): Unit = {
    vertx.eventBus().localConsumer[String]("maxmind/ip", event => ip(event))
    vertx.eventBus().localConsumer[String]("maxmind/update", event => updateReader(event))
  }

  def ip(event: Message[String]): Unit = {
    Try({
      reader.map((value: Reader) => {
        val ip = event.body()
        logger.debug("Request ip {}", ip)

        val address: InetAddress = InetAddress.getByName(ip)
        value.get(address)
      }).getOrElse(throw new IllegalStateException("not initialized"))
    }) match {
      case Success(null) =>
        event.reply("{}")
      case Success(v) =>
        event.reply(v.toString)
      case Failure(ex) =>
        event.fail(0, ex.getMessage)
    }
  }

  def updateReader(event: Message[String]): Unit = {
    Try({
      val path = event.body
      reader = Option(new Reader(new File(path)))
      path
    }) match {
      case Success(v) =>
        logger.info("Update by {}", v)
      case Failure(ex) =>
        event.fail(0, ex.getMessage)
    }
  }
}
