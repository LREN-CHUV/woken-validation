# Verified with http://hadolint.lukasmartinelli.ch/

# Pull base image
FROM hbpmip/scala-base-build:0.13.16-5 as scala-build-env

ARG BINTRAY_USER
ARG BINTRAY_PASS

ENV BINTRAY_USER=$BINTRAY_USER \
    BINTRAY_PASS=$BINTRAY_PASS

COPY build.sbt /build/
COPY project/ /build/project/

# Run sbt on an empty project and force it to download most of its dependencies to fill the cache
RUN sbt compile

COPY src/ /build/src/
COPY .git/ /build/.git/
COPY .circleci/ /build/.circleci/
COPY .*.cfg .*ignore .*.yaml .*.conf *.md *.sh *.yml *.json Dockerfile LICENSE /build/

RUN /check-sources.sh

RUN sbt assembly

FROM openjdk:8u131-jdk-alpine

MAINTAINER Ludovic Claude <ludovic.claude@chuv.ch>

RUN mkdir -p /opt/woken-validation/config

RUN adduser -H -D -u 1000 woken \
    && chown -R woken:woken /opt/woken-validation

COPY --from=scala-build-env /my-project/target/scala-2.11/woken-validation-assembly-dev.jar /opt/woken-validation/woken-validation.jar

USER woken

# org.label-schema.build-date=$BUILD_DATE
# org.label-schema.vcs-ref=$VCS_REF
LABEL org.label-schema.schema-version="1.0" \
        org.label-schema.license="Apache 2.0" \
        org.label-schema.name="woken" \
        org.label-schema.description="An orchestration platform for Docker containers running data mining algorithms" \
        org.label-schema.url="https://github.com/LREN-CHUV/woken-validation" \
        org.label-schema.vcs-type="git" \
        org.label-schema.vcs-url="https://github.com/LREN-CHUV/woken-validation" \
        org.label-schema.vendor="LREN CHUV" \
        org.label-schema.version="githash" \
        org.label-schema.docker.dockerfile="Dockerfile" \
        org.label-schema.memory-hint="2048"

WORKDIR /opt/woken-validation

ENTRYPOINT java -jar -Dconfig.file=config/application.conf woken-validation.jar

