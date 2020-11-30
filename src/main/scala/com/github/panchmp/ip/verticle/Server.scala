package com.github.panchmp.ip.verticle

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.http.{HttpHeaders, HttpMethod}
import io.vertx.ext.web.{RoutingContext => JRoutingContext}
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.micrometer.PrometheusScrapingHandler
import io.vertx.scala.core.eventbus.Message
import io.vertx.scala.ext.web.handler.ErrorHandler
import io.vertx.scala.ext.web.{Router, RoutingContext}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class Server extends ScalaVerticle with StrictLogging {
  override def startFuture(): Future[Unit] = {

    val server = vertx.createHttpServer()
    val router: Router = Router.router(vertx)

    router.route.failureHandler(ErrorHandler.create(true))

    router.route(HttpMethod.GET, "/healthz").handler(ctx =>
      ctx.response.putHeader(HttpHeaders.CONTENT_TYPE.toString, "text/plain").end("OK")
    )

    router.route("/metrics").handler(ctx => {
      PrometheusScrapingHandler.create.handle(ctx.asJava.asInstanceOf[JRoutingContext])
    })

    val apiRouter: Router = Router.router(vertx)
    apiRouter.get("/ip/:ip").handler(ip)

    router.mountSubRouter("/api/v1", apiRouter)

    val port = config.getInteger("server.http.port", 8080)
    server
      .requestHandler(router.accept)
      .listenFuture(port)
      .map(_ => ())
  }

  def ip(ctx: RoutingContext): Unit = {
    val ip: Option[String] = ctx.request().getParam("ip")
    vertx.eventBus().sendFuture[String]("maxmind/ip", ip).onComplete {
      case Success(msg: Message[String]) =>
        ctx.response()
          .putHeader(HttpHeaders.CONTENT_TYPE.toString, "application/json; charset=utf-8")
          .end(msg.body())
      case Failure(ex) =>
        logger.error("Can't process response for ip: {} due {}", ip, ex.getMessage)
        ctx.response()
          .setStatusCode(500)
          .setStatusMessage(ex.getMessage)
          .end()
    }
  }

}
