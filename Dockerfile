# Verified with http://hadolint.lukasmartinelli.ch/

FROM hbpmip/scala-base-build:1.2.6-3 as scala-build-env

ARG BINTRAY_USER
ARG BINTRAY_PASS

ENV BINTRAY_USER=$BINTRAY_USER \
    BINTRAY_PASS=$BINTRAY_PASS

RUN apt-get update && apt-get install -y python2 python-pip \
    && pip2 install titus \
    && rm -rf /var/lib/apt/lists/*

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

FROM hbpmip/java-base:11.0.1-1

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION

COPY docker/run.sh /
COPY docker/weaver-agent.sh /opt/woken-validation/

RUN addgroup woken \
    && adduser --system --disabled-password --uid 1000 --ingroup woken woken

RUN apt-get update && apt-get install -y python2 python-pip curl \
    && apt-get install -y build-essential gfortran python-dev \
    && pip2 install numpy==1.15.4 titus==0.8.4 bugsnag==3.4.3 \
    && apt-get remove -y build-essential gfortran python-dev \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

RUN chmod +x /opt/woken-validation/weaver-agent.sh \
    && /opt/woken-validation/weaver-agent.sh

COPY src/main/python/pfa_eval.py /app/pfa/pfa_eval.py
COPY --from=scala-build-env /build/target/scala-2.11/woken-validation-all.jar /opt/woken-validation/woken-validation.jar

ENV APP_NAME="Woken Worker" \
    APP_TYPE="Scala" \
    VERSION=$VERSION \
    BUILD_DATE=$BUILD_DATE \
    BUGSNAG_KEY=c023faf8a616d9f2847f539b6cf241a9 \
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
