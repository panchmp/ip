package com.github.panchmp.ip.verticle

import java.io.File
import java.net.InetAddress

import com.maxmind.db.{CHMCache, Reader}
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.eventbus.Message
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

case class ReaderHolder(reader: Option[Reader], path: String)

class MaxMindService extends ScalaVerticle {
  private val log = LoggerFactory.getLogger(classOf[MaxMindService])

  private var readerHolder: ReaderHolder = ReaderHolder(Option.empty, "")

  override def start(): Unit = {
    vertx.eventBus().localConsumer[String]("maxmind/ip", (event: Message[String]) => {
      Try({
        val localMap = vertx.sharedData().getLocalMap[String, String]("maxmind.db")
        val path: String = localMap.get("path")

        if (path != null && !readerHolder.path.equals(path)) {
          val reader = new Reader(new File(path), new CHMCache(2 ^ 16))
          readerHolder = ReaderHolder(Option(reader), path);
          log.info("Update reader by {}", path)
        }
        readerHolder.reader.map((value: Reader) => {
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
    })
  }
}
