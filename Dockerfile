#FROM azul/zulu-openjdk-alpine:11-jre
FROM adoptopenjdk/openjdk11:alpine

MAINTAINER Michael Panchenko <panchmp@gmail.com>

EXPOSE 8080 5005
WORKDIR /opt/ip/

COPY data/GeoLite2-City_20191203.tar.gz /opt/ip/data/GeoLite2-City.tar.gz

COPY ./target/dependency/*.jar /opt/ip/lib/
COPY ./target/*.jar /opt/ip/

ENTRYPOINT ["java","-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", "-Djava.security.egd=file:/dev/./urandom","-cp",".:./*:./lib/*","com.github.panchmp.ip.Application"]
