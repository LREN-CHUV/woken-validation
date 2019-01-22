
# Changelog

* Add Akka management

## 2.2.0 - 2018-02-16

* Docker image contains the configuration of the application. Environment variables can be used to tune the configuration
* Add health checks on /health endpoint and a web server
* __dev__: Migrate classes to package ch.chuv.lren.woken.validation.*
* __dev__: Update to Akka 2.5.9

## 2.1.0 - 2017-12-11

* Add scoring of models using Spark MLLib.

## 2.0.0 - 2017-11-07

* Migrate classes to package eu.hbp.mip.woken.*
* Add domain objects, in particular Error which can be send to clients in some cases.

## 1.0.0 - 2016-12-07

* First stable version
