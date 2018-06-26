# Verified with http://hadolint.lukasmartinelli.ch/

FROM hbpmip/scala-base-build:1.1.0-2 as scala-build-env

ARG BINTRAY_USER
ARG BINTRAY_PASS

ENV BINTRAY_USER=$BINTRAY_USER \
    BINTRAY_PASS=$BINTRAY_PASS

RUN apk add --update --no-cache python2 py2-pip \
    && pip2 install titus

COPY src/main/python/pfa_eval.py /pfa_eval.py

# First caching layer: build.sbt and sbt configuration
COPY build.sbt /build/
RUN  mkdir -p /build/project/
COPY project/build.properties project/plugins.sbt project/.gitignore /build/project/

# Run sbt on an empty project and force it to download most of its dependencies to fill the cache
RUN sbt -mem 1500 compile

# Second caching layer: project sources
COPY src/ /build/src/
COPY docker/ /build/docker/
COPY .git/ /build/.git/
COPY .circleci/ /build/.circleci/
COPY .*.cfg .*ignore .*.yaml .*.conf *.md *.sh *.yml *.json Dockerfile LICENSE /build/

RUN /check-sources.sh

RUN sbt -mem 1500 test assembly

FROM hbpmip/java-base:8u151-1

MAINTAINER Ludovic Claude <ludovic.claude@chuv.ch>

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION

COPY docker/run.sh /
ADD  docker/weaver-agent.sh /opt/woken-validation/

RUN adduser -H -D -u 1000 woken \
    && apk add --update --no-cache curl

RUN  chmod +x /opt/woken-validation/weaver-agent.sh \
         && /opt/woken-validation/weaver-agent.sh

RUN apk add --update --no-cache python2 python2-dev gfortran build-base py2-pip \
    && pip2 install numpy titus bugsnag \
    && apk del python2-dev build-base py2-pip

RUN set -ex \
	&& apk add -u --no-cache --virtual .build-deps \
		git gcc libc-dev make cmake libtirpc-dev pax-utils \
	&& mkdir -p /usr/src \
	&& cd /usr/src \
	&& git clone --branch sigar-musl https://github.com/ncopa/sigar.git \
	&& mkdir sigar/build \
	&& cd sigar/build \
	&& CFLAGS="-std=gnu89" cmake .. \
	&& make -j$(getconf _NPROCESSORS_ONLN) \
	&& make install \
	&& runDeps="$( \
		scanelf --needed --nobanner --recursive /usr/local \
			| awk '{ gsub(/,/, "\nso:", $2); print "so:" $2 }' \
			| sort -u \
			| xargs -r apk info --installed \
			| sort -u \
	)" \
	&& apk add --virtual .libsigar-rundeps $runDeps \
	&& apk del .build-deps \
    && rm -rf /usr/src/sigar

COPY src/main/python/pfa_eval.py /app/pfa/pfa_eval.py
COPY --from=scala-build-env /build/target/scala-2.11/woken-validation-all.jar /opt/woken-validation/woken-validation.jar

ENV WOKEN_VALIDATION_BUGSNAG_KEY=c023faf8a616d9f2847f539b6cf241a9 \
  PFA_EVALUATOR_BUGSNAG_KEY=3aa0cd3936adc7a07e086cec12b04e2c \
  PFA_EVALUATOR_ROOT=/app/pfa

USER woken

WORKDIR /opt/woken-validation


ENTRYPOINT ["/run.sh"]

# 8081: Web service API, health checks on http://host:8081/health
# 8082: Akka cluster
# 4040: Spark UI (http://host:4040)
EXPOSE 8081 8082 4040

HEALTHCHECK --start-period=60s CMD curl -v --silent http://localhost:8081/health 2>&1 | grep UP

LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="hbpmip/woken-validation" \
      org.label-schema.description="An orchestration platform for Docker containers running data mining algorithms" \
      org.label-schema.url="https://github.com/LREN-CHUV/woken-validation" \
      org.label-schema.vcs-type="git" \
      org.label-schema.vcs-url="https://github.com/LREN-CHUV/woken-validation" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.version="$VERSION" \
      org.label-schema.vendor="LREN CHUV" \
      org.label-schema.license="AGPLv3" \
      org.label-schema.docker.dockerfile="Dockerfile" \
      org.label-schema.memory-hint="2048" \
      org.label-schema.schema-version="1.0"
