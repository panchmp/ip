package com.github.panchmp.ip.verticle

import java.io.File
import java.net.InetAddress

import com.maxmind.db.Reader
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.eventbus.Message
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

class MaxMindService extends ScalaVerticle {
  private val log = LoggerFactory.getLogger(classOf[MaxMindService])

  private var reader: Option[Reader] = Option.empty

  override def start(): Unit = {
    vertx.eventBus().localConsumer[String]("maxmind/ip", event => getIp(event))
    vertx.eventBus().localConsumer[String]("maxmind/update", event => updateReader(event))
  }

  private def getIp(event: Message[String]): Unit = {
    Try({
      reader.map((value: Reader) => {
        val ip = event.body()
        log.debug("Request ip {}", ip)

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
        log.info(s"Update by $v")
      case Failure(ex) =>
        event.fail(0, ex.getMessage)
    }
  }
}
