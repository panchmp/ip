FROM openjdk:8-jre-alpine
#FROM openjdk:8-jre
#FROM azul/zulu-openjdk-alpine:8-jre
#FROM azul/zulu-openjdk-alpine:11-jre

MAINTAINER Michael Panchenko <panchmp@gmail.com>

COPY ./target/*.jar /app/
COPY ./target/dependency/*.jar /app/

WORKDIR /app

EXPOSE 8080 5005

ENTRYPOINT ["java","-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", "-Djava.security.egd=file:/dev/./urandom","-cp",".:./*","com.github.panchmp.ip.Application"]
