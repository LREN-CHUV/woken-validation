#!/bin/sh

cd /opt/woken-validation

OPTS="-template config/application.conf.tmpl:config/application.conf"
dockerize ${OPTS} java -jar -Dconfig.file=config/application.conf woken-validation.jar
