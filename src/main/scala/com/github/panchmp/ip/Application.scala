package com.github.panchmp.ip


import com.github.panchmp.ip.verticle.{MaxMindService, MaxMindUpdater, Server}
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME
import io.vertx.core.logging.SLF4JLogDelegateFactory
import io.vertx.lang.scala.ScalaVerticle.nameForVerticle
import io.vertx.lang.scala.VertxExecutionContext
import io.vertx.scala.config.{ConfigRetriever, ConfigRetrieverOptions, ConfigStoreOptions}
import io.vertx.scala.core._
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

object Application {
  System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, classOf[SLF4JLogDelegateFactory].getName)
  val options: VertxOptions = VertxOptions().setFileResolverCachingEnabled(true)
  val vertx: Vertx = Vertx.vertx(options)
  private val log = LoggerFactory.getLogger(Application.getClass)

  implicit val vertxExecutionContext: VertxExecutionContext = VertxExecutionContext(vertx.getOrCreateContext())

  def main(args: Array[String]): Unit = {

    val configRetrieverOptions = buildConfigRetrieverOptions()
    ConfigRetriever.create(vertx, configRetrieverOptions).getConfigFuture()
      .flatMap((jsonObject: JsonObject) => {
        deployVerticles(jsonObject)
      }).onComplete {
      case Success(_) => log.info("Vertx is started")
      case Failure(t) =>
        log.error("Can't start Vertx", t)
        vertx.closeFuture().onComplete {
          System.exit(1)
          return
        }
    }
  }

  private def deployVerticles(config: JsonObject) = {
    Future.sequence(
      Seq(
        {
          val instances = config.getInteger("verticle.server.instances")
          val deploymentOptions = DeploymentOptions().setConfig(config).setInstances(instances)
          vertx.deployVerticleFuture(nameForVerticle[Server], deploymentOptions)
        },
        {
          val deploymentOptions = DeploymentOptions().setConfig(config)
          vertx.deployVerticleFuture(nameForVerticle[MaxMindUpdater], deploymentOptions)
        },
        {
          val instances = config.getInteger("verticle.maxmind.instances")
          val deploymentOptions = DeploymentOptions().setConfig(config).setInstances(instances)
          vertx.deployVerticleFuture(nameForVerticle[MaxMindService], deploymentOptions)
        }
      )
    )
  }

  private def buildConfigRetrieverOptions() = {
    ConfigRetrieverOptions()
      .setIncludeDefaultStores(true)
      .addStore(
        ConfigStoreOptions() //default configuration
          .setType("file")
          .setFormat("properties")
          .setConfig(new JsonObject().put("path", "default.properties"))
      )
      .addStore(
        ConfigStoreOptions()
          .setType("file")
          .setFormat("properties")
          .setOptional(true)
          .setConfig(new JsonObject().put("path", "config/application.properties"))
      )
      .addStore(ConfigStoreOptions().setType("sys"))
      .addStore(ConfigStoreOptions().setType("env"))
  }
}