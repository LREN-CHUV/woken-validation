#!/bin/sh

cd /opt/woken-validation

JAVA="java"

# -javaagent:/opt/woken/aspectjweaver.jar
exec ${JAVA} ${JAVA_OPTIONS} \
          -javaagent:/opt/woken/aspectjweaver.jar \
          -Djava.library.path=/lib \
          -DLog4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
          -jar woken-validation.jar
