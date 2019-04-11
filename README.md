[![CHUV](https://img.shields.io/badge/CHUV-LREN-AF4C64.svg)](https://www.unil.ch/lren/en/home.html) [![License](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](https://github.com/LREN-CHUV/woken-validation/blob/master/LICENSE) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/77016dbdd3544d17b849eb5a79a61a37)](https://www.codacy.com/app/hbp-mip/woken-validation?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=HBPMedical/woken-validation&amp;utm_campaign=Badge_Grade) [![CircleCI](https://circleci.com/gh/HBPMedical/woken-validation.svg?style=svg)](https://circleci.com/gh/HBPMedical/woken-validation)

# Woken validation

Benchmarking (model scoring) and cross-validation support add-on module for Woken.

This software requires [Woken](https://github.com/LREN-CHUV/woken) to work, as it establishes an Akka cluster with Woken as the master.

It embeds [Apache Spark](http://spark.apache.org/) which provides the numerical methods for benchmarking and model scoring.

## Usage

```sh

 docker run --rm --env [list of environment variables] --link woken hbpmip/woken-validation:2.7.6

```

where the environment variables are:

* CLUSTER_IP: Name of this server advertised in the Akka cluster
* CLUSTER_PORT: Port of this server advertised in the Akka cluster
* CLUSTER_NAME: Name of Woken cluster, default to 'woken'
* WOKEN_PORT_8088_TCP_ADDR: Address of Woken master server
* WOKEN_PORT_8088_TCP_PORT: Port of Woken master server, default to 8088
* LOG_LEVEL: Level for logs on standard output, default to WARNING
* UDP_ARTERY: if set to true, enables UDP and Akka Artery for communication (experimental, not working yet)
* RELEASE_STAGE: Release stage used when reporting errors to Bugsnag. Values are dev, staging, production
* DATA_CENTER_LOCATION: Location of the datacenter, used when reporting errors to Bugsnag
* CONTAINER_ORCHESTRATION: Container orchestration system used to execute the Docker containers. Values are mesos, docker-compose, kubernetes

## How to build

You need the following software installed:

* [Docker](https://www.docker.com/) 18.09 or better with docker-compose

1. Run the build script

```sh
  ./build.sh
```
It will build the scala project into a Docker container.

## Release

You need the following software installed:

* [Bumpversion](https://github.com/peritus/bumpversion)
* [Precommit](http://pre-commit.com/)

Execute the following commands to distribute Woken as a Docker container:

```sh
  ./publish.sh
```

# Acknowledgements

## Funding

This work has been funded by the European Union Seventh Framework Program (FP7/2007­2013) under grant agreement no. 604102 (HBP)

This work is part of SP8 of the Human Brain Project (SGA1).

## Sponsors

Thanks for the generous support of [<img src="docs/bugsnag_logo_navy.png" height="16" alt="Bugsnag"></img>](https://bugsnag.com)
who offered us a Standard plan allowing us to inspect and report efficiently errors in our software.

## Tools

We use the following tools for development:

* IntelliJ IDEA
* [<img src="docs/bugsnag_logo_navy.png" height="16" alt=Bugsnag></img>](https://bugsnag.com) to report errors in real time to our development team
* [CircleCI](https://circleci.com) for continuous integration
