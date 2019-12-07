package com.github.panchmp.ip


import com.github.panchmp.ip.verticle.{MaxMindVerticle, ServerVerticle}
import io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME
import io.vertx.core.logging.SLF4JLogDelegateFactory
import io.vertx.lang.scala.ScalaVerticle.nameForVerticle
import io.vertx.lang.scala.VertxExecutionContext
import io.vertx.scala.core._
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

object Application {
  System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, classOf[SLF4JLogDelegateFactory].getName)

  private val log = LoggerFactory.getLogger(Application.getClass)

  val options: VertxOptions = VertxOptions().setFileResolverCachingEnabled(true)
  val vertx: Vertx = Vertx.vertx(options)
  implicit val vertxExecutionContext: VertxExecutionContext = VertxExecutionContext(vertx.getOrCreateContext())

  def main(args: Array[String]): Unit = {

    Future.sequence(Seq(
      vertx.deployVerticleFuture(nameForVerticle[ServerVerticle], DeploymentOptions().setInstances(2)),
      vertx.deployVerticleFuture(nameForVerticle[MaxMindVerticle]),
    )).onComplete {
      case Success(_) => log.info("Vertx is started")
      case Failure(t) =>
        log.error("Can't start Vertx", t)
        vertx.closeFuture().onComplete {
          System.exit(1)
          return
        }
    }
  }
}