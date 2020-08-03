#FROM azul/zulu-openjdk-alpine:11-jre
FROM adoptopenjdk/openjdk11:alpine

MAINTAINER Michael Panchenko <panchmp@gmail.com>

EXPOSE 8080 5005

WORKDIR /opt/ip/
VOLUME /opt/ip/conf
VOLUME /opt/ip/data

COPY ./target/dependency/*.jar /opt/ip/lib/
COPY ./target/*.jar /opt/ip/

ENV JAVA_TOOL_OPTIONS "-Xms512m -Xmx512m -XX:+AlwaysActAsServerClassMachine"

ENTRYPOINT ["java", "-cp",".:./*:./lib/*","com.github.panchmp.ip.Application"]
CMD ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -XX:+UseContainerSupport -XX:+PrintFlagsFinal"]