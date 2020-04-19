package com.github.panchmp.ip.gatling

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.language.postfixOps

class BasicSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl("http://localhost:8080/api/ip/")
    .acceptHeader("application/json")
    .doNotTrackHeader("1")

  private val feeder = csv("data/ip.zip").unzip.random.circular

  private val scn: ScenarioBuilder = scenario("BasicSimulation")
    .feed(feeder)
    .exec(
      http("get_api")
        .get("${ip}"))
    .pause(100 millisecond)

  setUp(
    scn.inject(
      //rampUsers(10) during (10 seconds), // 3
      //constantUsersPerSec(10) during (20 seconds), // 4
      rampConcurrentUsers(1) to (60) during (60 seconds),
      constantConcurrentUsers(60) during (10 minutes)
    )
  ).protocols(httpProtocol)
}