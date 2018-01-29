import sbt.ExclusionRule

// *****************************************************************************
// Projects
// *****************************************************************************

lazy val `woken-validation` =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin, GitVersioning, GitBranchPrompt)
    .settings(settings)
    .settings(
      Seq(
        mainClass in Runtime := Some("eu.hbp.mip.woken.validation.Main"),
        libraryDependencies ++= Seq(
          library.akkaActor,
          library.akkaRemote,
          library.akkaCluster,
          library.akkaClusterTools,
          library.akkaSlf4j,
          //library.akkaTracingCore,
          library.akkaHttp,
          library.akkaHttpJson,
          library.sprayJson,
          library.slf4j,
          library.jclOverSlf4j,
          library.log4jSlf4j,
          library.disruptor,
          library.catsCore,
          library.hadrian,
          library.sparkMllib,
          library.sparkSql,
          library.wokenMessages,
          library.scalaCheck   % Test,
          library.scalaTest    % Test,
          library.akkaTestkit  % Test
        ),
        includeFilter in (Compile, unmanagedResources) := "*.xml",
        includeFilter in (Test, unmanagedResources) := "*.json",
        assemblyJarName in assembly := "woken-validation-all.jar",
        assemblyMergeStrategy in assembly := {
          case PathList("io", "hydrosphere", xs @ _*) => MergeStrategy.first
          case PathList("org","aopalliance", xs @ _*) => MergeStrategy.last
          case PathList("javax", "inject", xs @ _*) => MergeStrategy.last
          case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
          case PathList("javax", "activation", xs @ _*) => MergeStrategy.last
          case PathList("org", "apache", xs @ _*) => MergeStrategy.last
          case PathList("org", "apache", "spark", "unused", xs @ _*) => MergeStrategy.first
          case PathList("com", "google", xs @ _*) => MergeStrategy.last
          case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
          case PathList("com", "codahale", xs @ _*) => MergeStrategy.last
          case PathList("com", "yammer", xs @ _*) => MergeStrategy.last
          case "about.html" => MergeStrategy.discard
          case "overview.html" => MergeStrategy.discard
          case "parquet.thrift" => MergeStrategy.last
          case "META-INF/ECLIPSEF.RSA" => MergeStrategy.discard
          case "META-INF/mailcap" => MergeStrategy.last
          case "META-INF/mimetypes.default" => MergeStrategy.last
          case "plugin.properties" => MergeStrategy.last
          case "log4j.properties" => MergeStrategy.last
          case x =>
            val oldStrategy = (assemblyMergeStrategy in assembly).value
            oldStrategy(x)
        }
      )
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val scalaCheck     = "1.13.5"
      val scalaTest      = "3.0.3"
      val akka           = "2.5.9"
      val akkaTracing    = "0.6.1"
      val akkaHttp       = "10.1.0-RC1"
      val sprayJson      = "1.3.4"
      val cats           = "1.0.1"
      val slf4j          = "1.7.25"
      val log4j          = "2.10.0"
      val disruptor      = "3.3.7"
      val hadrian        = "0.8.5"
      // TODO: Spark can be upgraded, but there is some work here
      val spark          = "2.0.2"
      val wokenMessages  = "2.3.1"
    }
    object ExclusionRules {
      val excludeIvy = ExclusionRule(organization = "org.apache.ivy")
      val excludeMail = ExclusionRule(organization = "javax.mail")
      val excludeNettyIo = ExclusionRule(organization = "io.netty", name = "netty-all")
      val excludeQQ = ExclusionRule(organization = "org.scalamacros")
      val excludeParquet = ExclusionRule(organization = "org.apache.parquet")
      val excludeNlp = ExclusionRule(organization = "org.scalanlp")
      val excludeHadoop = ExclusionRule(organization = "org.apache.hadoop")
      val excludeSlf4jLog4j12 = ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12")
      val sparkExclusions = Seq(excludeIvy, excludeMail, excludeSlf4jLog4j12)
    }
    val scalaCheck: ModuleID  = "org.scalacheck"    %% "scalacheck"   % Version.scalaCheck
    val scalaTest: ModuleID   = "org.scalatest"     %% "scalatest"    % Version.scalaTest
    val akkaActor: ModuleID   = "com.typesafe.akka" %% "akka-actor"   % Version.akka
    val akkaRemote: ModuleID  = "com.typesafe.akka" %% "akka-remote"  % Version.akka
    val akkaCluster: ModuleID = "com.typesafe.akka" %% "akka-cluster" % Version.akka
    val akkaClusterTools: ModuleID = "com.typesafe.akka" %% "akka-cluster-tools" % Version.akka
    val akkaSlf4j: ModuleID   = "com.typesafe.akka" %% "akka-slf4j"   % Version.akka
    val akkaTestkit: ModuleID = "com.typesafe.akka" %% "akka-testkit" % Version.akka
    val akkaTracingCore: ModuleID = "com.github.levkhomich" %% "akka-tracing-core" % Version.akkaTracing
    val akkaHttp: ModuleID = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp
    val akkaHttpJson: ModuleID = "com.typesafe.akka" %% "akka-http-spray-json" % Version.akkaHttp
    val sprayJson: ModuleID   = "io.spray"          %% "spray-json"   % Version.sprayJson
    val slf4j: ModuleID       = "org.slf4j"          % "slf4j-api"    % Version.slf4j
    val jclOverSlf4j: ModuleID = "org.slf4j"         % "jcl-over-slf4j" % Version.slf4j
    val log4jSlf4j: ModuleID  = "org.apache.logging.log4j" % "log4j-slf4j-impl" % Version.log4j
    val disruptor: ModuleID   = "com.lmax"           % "disruptor"    % Version.disruptor
    val catsCore: ModuleID    = "org.typelevel"     %% "cats-core"    % Version.cats
    val hadrian: ModuleID     = "com.opendatagroup" % "hadrian"       % Version.hadrian
    // spark 2.2.x
    val sparkMllib: ModuleID  = "org.apache.spark"  %% "spark-mllib"  % Version.spark excludeAll(ExclusionRules.sparkExclusions :_*)
    val sparkSql: ModuleID    = "org.apache.spark"  %% "spark-sql"    % Version.spark excludeAll(ExclusionRules.sparkExclusions :_*)
    val wokenMessages: ModuleID = "eu.humanbrainproject.mip" %% "woken-messages" % Version.wokenMessages
  }

resolvers += "HBPMedical Bintray Repo" at "http://dl.bintray.com/hbpmedical/maven/"
resolvers += "opendatagroup maven" at "http://repository.opendatagroup.com/maven"

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings = commonSettings ++ gitSettings ++ scalafmtSettings

lazy val commonSettings =
  Seq(
    scalaVersion := "2.11.11",
    organization in ThisBuild := "eu.humanbrainproject.mip",
    organizationName in ThisBuild := "Human Brain Project MIP by LREN CHUV",
    homepage in ThisBuild := Some(url(s"https://github.com/HBPMedical/${name.value}/#readme")),
    licenses in ThisBuild := Seq("Apache-2.0" ->
      url(s"https://github.com/sbt/${name.value}/blob/${version.value}/LICENSE")),
    startYear in ThisBuild := Some(2017),
    description in ThisBuild := "Cross validation module for Woken",
    developers in ThisBuild := List(
      Developer("ludovicc", "Ludovic Claude", "@ludovicc", url("https://github.com/ludovicc"))
    ),
    scmInfo in ThisBuild := Some(ScmInfo(url(s"https://github.com/HBPMedical/${name.value}"), s"git@github.com:HBPMedical/${name.value}.git")),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Ywarn-value-discard",
      "-Ypartial-unification",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding",
      "UTF-8"
    ),
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
    unmanagedSourceDirectories.in(Compile) := Seq(scalaSource.in(Compile).value),
    unmanagedSourceDirectories.in(Test) := Seq(scalaSource.in(Test).value),
    wartremoverWarnings in (Compile, compile) ++= Warts.unsafe,
    fork in run := true,
    test in assembly := {},
    fork in Test := false,
    parallelExecution in Test := false
  )

lazy val gitSettings =
  Seq(
    git.gitTagToVersionNumber := { tag: String =>
      if (tag matches "[0-9]+\\..*") Some(tag)
      else None
    },
    git.useGitDescribe := true
  )

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true,
    scalafmtOnCompile.in(Sbt) := false,
    scalafmtVersion := "1.4.0"
  )
