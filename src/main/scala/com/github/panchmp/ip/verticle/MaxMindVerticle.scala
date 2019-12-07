package com.github.panchmp.ip.verticle

import java.io.{ByteArrayInputStream, Closeable}
import java.net.InetAddress

import com.maxmind.db.Reader
import io.vertx.core.buffer.Buffer
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.eventbus.Message
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

class MaxMindVerticle extends ScalaVerticle {
  private val log = LoggerFactory.getLogger(classOf[MaxMindVerticle])

  var reader: Reader = _

  override def start(): Unit = {

    vertx.fileSystem().readFileFuture("./data/GeoLite2-City.tar.gz").map((buffer: Buffer) => {
      val archiveInputStream: TarArchiveInputStream = new TarArchiveInputStream(new GzipCompressorInputStream(new ByteArrayInputStream(buffer.getBytes)))
      val bytes = using(archiveInputStream) {
        is => {
          extractFile(is)
        }
      }
      new Reader(new ByteArrayInputStream(bytes))
    }).onComplete {
      case Success(v) =>
        reader = v
      case Failure(ex) =>
        throw ex
    }

    vertx.eventBus().localConsumer[String]("maxmind/ip", (event: Message[String]) => {
      Try({
        val str = event.body()
        val address = InetAddress.getByName(str)
        reader.get(address)
      }) match {
        case Success(v) =>
          event.reply(v.toString)
        case Failure(ex) =>
          event.fail(0, ex.getMessage)
      }
    })
  }

  private def extractFile(archiveInputStream: ArchiveInputStream): Array[Byte] = {
    var entry = archiveInputStream.getNextEntry
    while (entry != null) {
      if (archiveInputStream.canReadEntryData(entry)) {
        log.info("entry {}", entry.getName)
        if (entry.getName.endsWith(".mmdb")) {
          return IOUtils.toByteArray(archiveInputStream);
        }
      } else {
        log.warn("Can't read entry {}", entry.getName)
      }
      entry = archiveInputStream.getNextEntry
    }
    throw new IllegalStateException("Archive doesn't contain *.mmdb files");
  }

  def using[A, B <: Closeable](closeable: B)(f: B => A): A = {
    require(closeable != null, "resource is null")
    try {
      f(closeable)
    } finally {
      closeable.close()
    }
  }
}
