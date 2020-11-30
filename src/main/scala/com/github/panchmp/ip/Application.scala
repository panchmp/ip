package com.github.panchmp.ip


import java.util

import com.github.panchmp.ip.verticle.{MaxMindService, MaxMindUpdater, Server}
import com.typesafe.scalalogging.StrictLogging
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.{PrometheusConfig, PrometheusMeterRegistry}
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME
import io.vertx.core.logging.SLF4JLogDelegateFactory
import io.vertx.lang.scala.ScalaVerticle.nameForVerticle
import io.vertx.lang.scala.VertxExecutionContext
import io.vertx.micrometer.{Label, MicrometerMetricsOptions, VertxPrometheusOptions}
import io.vertx.scala.config.{ConfigRetriever, ConfigRetrieverOptions, ConfigStoreOptions}
import io.vertx.scala.core._
import io.vertx.scala.core.metrics.MetricsOptions

import scala.util.{Failure, Success}

object Application extends StrictLogging {

  def main(args: Array[String]): Unit = {
    System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, Predef.classOf[SLF4JLogDelegateFactory].getName)

    val options: VertxOptions = VertxOptions()
      .setFileResolverCachingEnabled(true)
      .setMetricsOptions(getMetricsOptions)

    val vertx: Vertx = Vertx.vertx(options)

    implicit val vertxExecutionContext: VertxExecutionContext = VertxExecutionContext.apply(vertx.getOrCreateContext())

    val configRetrieverOptions = buildConfigRetrieverOptions().setScanPeriod(-1)

    ConfigRetriever.create(vertx, configRetrieverOptions).getConfigFuture()
      .flatMap((config: JsonObject) => {
        vertx.deployVerticleFuture(nameForVerticle[Server],
          DeploymentOptions().setConfig(config).setInstances(config.getInteger("verticle.server.instances")))

        vertx.deployVerticleFuture(nameForVerticle[MaxMindService],
          DeploymentOptions().setConfig(config).setInstances(config.getInteger("verticle.maxmind.instances")))

        vertx.deployVerticleFuture(nameForVerticle[MaxMindUpdater],
          DeploymentOptions().setConfig(config).setInstances(1 /*always*/))

      }).onComplete {
      case Success(_) => logger.info("Vertx is started")
      case Failure(t) =>
        logger.error("Can't start Vertx", t)
        vertx.closeFuture().onComplete {
          System.exit(1)
          return
        }
    }
  }

  private def getMetricsOptions = {
    val meterRegistry: MeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val micrometerMetricsOptions = new MicrometerMetricsOptions()
      .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
      .setMicrometerRegistry(meterRegistry)
      .setJvmMetricsEnabled(true)
      .setLabels(util.EnumSet.of(Label.LOCAL, Label.HTTP_CODE))
      .setEnabled(true)

    MetricsOptions(micrometerMetricsOptions)
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