#!/bin/sh

cd /opt/woken-validation

JAVA="java"

# -javaagent:/opt/woken/aspectjweaver.jar
exec ${JAVA} ${JAVA_OPTIONS} \
          -javaagent:/opt/woken-validation/aspectjweaver.jar \
          -Djava.library.path=/lib \
          -DLog4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
          -Daeron.term.buffer.length=100m \
	  -jar woken-validation.jar
