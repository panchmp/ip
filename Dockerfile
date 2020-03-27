#FROM openjdk:8-jre-alpine
#FROM openjdk:8-jre
#FROM azul/zulu-openjdk-alpine:8-jre
#FROM azul/zulu-openjdk-alpine:11-jre
FROM adoptopenjdk/openjdk11:alpine

MAINTAINER Michael Panchenko <panchmp@gmail.com>

COPY ./target/*.jar /opt/ip/
COPY ./target/dependency/*.jar /opt/ip/lib/

WORKDIR /opt/ip/

EXPOSE 8080 5005

ENTRYPOINT ["java","-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", "-Djava.security.egd=file:/dev/./urandom","-cp",".:./*:./lib/*","com.github.panchmp.ip.Application"]
