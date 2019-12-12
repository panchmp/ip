package com.github.panchmp.ip.verticle

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.concurrent.TimeUnit

import com.github.panchmp.ip.utils.CloseableUtils.using
import com.maxmind.db.Reader
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.impl.Utils
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.eventbus.Message
import io.vertx.scala.ext.web.client.{HttpResponse, WebClient, WebClientOptions}
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

case class ReaderHolder(lastModified: Long, reader: Option[Reader])

class MaxMindVerticle extends ScalaVerticle {
  private val log = LoggerFactory.getLogger(classOf[MaxMindVerticle])

  private val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")
  private var readerHolder: ReaderHolder = ReaderHolder(0, None)

  override def start(): Unit = {
    val updateInterval = config.getLong("maxmind.db.update.interval", TimeUnit.HOURS.toMillis(4))
    val updateRepeatInterval = config.getLong("maxmind.db.update.repeat.interval", TimeUnit.MINUTES.toMillis(5))

    vertx.eventBus().localConsumer[String]("maxmind/update", (_: Message[String]) => {
      downloadMaxmind().onComplete {
        case Success(v) =>
          vertx.setTimer(updateInterval, _ => update())
        case Failure(ex) =>
          log.warn("Can't update mixmind db", ex)
          vertx.setTimer(updateRepeatInterval, _ => update())
      }
    })

    update()

    def update(): Unit = {
      vertx.eventBus().publish("maxmind/update", None)
    }

    vertx.eventBus().localConsumer[String]("maxmind/ip", (event: Message[String]) => {
      Try({
        readerHolder.reader.map((value: Reader) => {
          val ip = event.body()
          val address = InetAddress.getByName(ip)
          value.get(address)
        }).getOrElse(throw new IllegalStateException("not initialized"))
      }) match {
        case Success(v) =>
          event.reply(v.toString)
        case Failure(ex) =>
          event.fail(0, ex.getMessage)
      }
    })
  }

  private def downloadMaxmind() = {
    val appName = config.getString("application.name")
    val appVersion = config.getString("application.version")

    val webClientOptions = WebClientOptions().setUserAgent(appName + ':' + appVersion)
    val webClient = WebClient.create(vertx, webClientOptions)
    try {

      val apiKey = config.getString("maxmind.license.key", "")
      val dbUrl = config.getString("maxmind.db.url")
      val url = dbUrl.replace("LICENSE_KEY", apiKey)

      log.info("Download {}", url);


      webClient.getAbs(url)
        .putHeader(HttpHeaders.IF_MODIFIED_SINCE.toString, dateHeader(readerHolder.lastModified))
        .sendFuture().map((httpResponse: HttpResponse[Buffer]) => {
        if (HttpResponseStatus.NOT_MODIFIED.code() == httpResponse.statusCode) {
          log.info("{} not modified", url)
        } else if (HttpResponseStatus.OK.code() == httpResponse.statusCode) {

          val lastModified = httpResponse.getHeader(HttpHeaders.LAST_MODIFIED.toString)
            .fold(0L)(v => Utils.parseRFC1123DateTime(v))

          val buffer = httpResponse.bodyAsBuffer().getOrElse(throw new IllegalStateException(s"Nothing download for $url "))
          val bytes: Array[Byte] = extractDBFile(buffer)

          readerHolder = ReaderHolder(lastModified, Option(new Reader(new ByteArrayInputStream(bytes))));
        } else {
          val code = httpResponse.statusCode
          val message = httpResponse.bodyAsString().getOrElse("")
          throw new IllegalStateException(s"Can't download $url. Response code $code: $message");
        }
      })
    } finally
      webClient.close()
  }

  /**
   *Utils.formatRFC1123DateTime() has issue with "one-digit day-of-month"
   * 5 Feb 2020 12:28:52 GMT
   * instead of
   * 05 Feb 2020 12:28:52 GMT
   */
  private def dateHeader(date: Long) = {
    formatter.format(Instant.ofEpochMilli(date).atZone(ZoneOffset.UTC))
  }

  private def extractDBFile(buffer: Buffer): Array[Byte] = {
    using(new TarArchiveInputStream(new GzipCompressorInputStream(new ByteArrayInputStream(buffer.getBytes)))) {
      archiveInputStream => {
        var entry = archiveInputStream.getNextEntry
        while (entry != null) {
          if (archiveInputStream.canReadEntryData(entry)) {
            log.debug("Find file {}", entry.getName)
            if (entry.getName.endsWith(".mmdb")) {
              return IOUtils.toByteArray(archiveInputStream);
            }
          } else {
            log.warn("Can't read entry {}", entry.getName)
          }
          entry = archiveInputStream.getNextEntry
        }
        throw new IllegalStateException("Cant find file *.mmmd")
      }
    }
  }
}
