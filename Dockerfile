FROM azul/zulu-openjdk-alpine:11-jre
#FROM openjdk:8-jre
#FROM openjdk:8-jre-alpine
#FROM openjdk:11-jre-slim
#FROM oracle/graalvm-ce

MAINTAINER Michael Panchenko <panchmp@gmail.com>

#ADD ./data/GeoLite2-City.mmdb /app/data/maxmind/
ADD https://geolite.maxmind.com/download/geoip/database/GeoLite2-City.tar.gz /app/data/
#RUN mkdir -p /app/data/maxmind/
#RUN tar -xvzf /app/data/GeoLite2-City.tar.gz --strip=1 -C /app/data/maxmind/

COPY ./target/*.jar /app/
COPY ./target/dependency/*.jar /app/

WORKDIR /app

EXPOSE 8080 5005

ENTRYPOINT ["java","-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", "-Djava.security.egd=file:/dev/./urandom","-cp",".:./*","com.github.panchmp.ip.Application"]
