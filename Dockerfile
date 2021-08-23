# syntax=docker/dockerfile:1
FROM openjdk:16
VOLUME /application
COPY target/config-service-1.0.0.jar /application/config-service.jar
ARG JAVA_OPTS
EXPOSE 8888/tcp
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /application/config-service.jar ${0} ${@}"]
