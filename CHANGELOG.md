
# Changelog

## 2.6.0 - 2019-01-22

* Monitoring: track issues with Bugsnag
* Monitoring: add Akka cluster management
* __dev__: Define location of PFA evaluator script
* __dev__: Update woken-messages to 2.9.1
* __dev__: Update cats-core to 1.4.0
* __dev__: Update Akka to 2.5.8, akka-http to 10.1.5
* __dev__: Update base image to java-base:11.0.1-1
* __dev__: Improve Docker build
* __fix__: Fix bug validation match error
* __fix__: Improve configuration of Kamon

## 2.5.0 - 2018-05-14

* Monitoring: Add dead letter monitor
* Monitoring: Add monitoring with Kamon
* Use Artery TCP for Akka remoting
* __dev__: Align configuration with Woken
* __dev__: Update to Akka 2.5.11
* __dev__: Update woken-messages to 2.7.5
* __dev__: Register actors with the dist pubsub mediator
* __dev__: Easy switch between Netty and Artery remoting
* __test__: Add tests for naive bayes model
* __test__: Test gradient boosting model
* __fix__: Fix json results for string categories
* __fix__: Validate more models using Titus
* __fix__: Use Titus for gradient_boosting model
* __fix__: Remove cluster constraint on Woken
* __fix__: Optimise size of Docker image
* __fix__: Replace ActorLogging by LazyLogging
* __fix__: Increase size of Akka payloads
* __fix__: Add Akka Coordinated shutdown
* __fix__: Health checks should not throw exceptions

## 2.4.0 - 2018-03-28

* __dev__: Update woken-messages to 2.6.3
* __test__: Test model for kNN
* __fix__: Increase pauses in Akka cluster
* __fix__: Use Titus for the evaluation of some PFA models

## 2.3.0 - 2018-03-16

* API: Add /health and /readiness routes
* Display url of the cluster on startup
* Accept Json strings containing double values
* Log errors during scoring
* __dev__: Move common config into reference.conf
* __dev__: Update Scala to 2.11.12
* __dev__: Update woken-messages to 2.6.0
* __fix__: Fix configuration for Akka cluster
* __fix__: Fix JVM options at startup

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
