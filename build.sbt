import sbt.ExclusionRule
import sbtassembly.MergeStrategy

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
        mainClass in Runtime := Some("ch.chuv.lren.woken.validation.Main"),
        libraryDependencies ++= Seq(
          library.akkaActor,
          library.akkaRemote,
          library.akkaCluster,
          library.akkaClusterTools,
          library.akkaSlf4j,
          library.akkaHttp,
          library.akkaHttpCors,
          library.akkaHttpJson,
          library.sprayJson,
          library.slf4j,
          library.jclOverSlf4j,
          library.log4jSlf4j,
          library.disruptor,
          library.scalaLogging,
          library.catsCore,
          library.hadrian,
          library.sparkMllib,
          library.sparkSql,
          library.wokenMessages,
          library.kamon,
          library.kamonAkka,
          library.kamonAkkaHttp,
          library.kamonAkkaRemote,
          library.kamonPrometheus,
          library.kamonZipkin,
          library.kamonSystemMetrics,
          library.kamonSigar,
          library.bugsnag,
          library.scalaCheck   % Test,
          library.scalaTest    % Test,
          library.akkaTestkit  % Test
        ),
        includeFilter in (Compile, unmanagedResources) := "*.xml" || "*.conf",
        includeFilter in (Test, unmanagedResources) := "*.json",
        // Use the customMergeStrategy in your settings
        assemblyMergeStrategy in assembly := customMergeStrategy,
        assemblyJarName in assembly := "woken-validation-all.jar"
      )
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val scalaCheck     = "1.14.0"
      val scalaTest      = "3.0.5"
      val akka           = "2.5.18"
      val akkaHttp       = "10.1.5"
      val akkaHttpCors   = "0.3.1"
      val sprayJson      = "1.3.5"
      val cats           = "1.4.0"
      val slf4j          = "1.7.25"
      val log4j          = "2.11.0"
      val disruptor      = "3.4.2"
      val scalaLogging   = "3.9.0"
      val hadrian        = "0.8.5.1"
      // TODO: Spark can be upgraded, but there is some work here
      val spark          = "2.0.2"
      val wokenMessages = "2.8.2"

      val kamon           = "1.1.3"
      val kamonAkka       = "1.0.1"
      val kamonAkkaRemote = "1.0.1"
      val kamonAkkaHttp   = "1.1.0"
      val kamonReporter   = "1.0.0"
      val kamonSystemMetrics = "1.0.0"
      val kamonSigar      = "1.6.6-rev002"
      val bugsnag         = "3.4.1"
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
      val excludeLogback = ExclusionRule(organization = "ch.qos.logback", name = "logback-classic")
      val excludeJackson = ExclusionRule(organization = "com.fasterxml.jackson.core")
    }
    val scalaCheck: ModuleID  = "org.scalacheck"    %% "scalacheck"   % Version.scalaCheck
    val scalaTest: ModuleID   = "org.scalatest"     %% "scalatest"    % Version.scalaTest
    val akkaActor: ModuleID   = "com.typesafe.akka" %% "akka-actor"   % Version.akka
    val akkaRemote: ModuleID  = "com.typesafe.akka" %% "akka-remote"  % Version.akka
    val akkaCluster: ModuleID = "com.typesafe.akka" %% "akka-cluster" % Version.akka
    val akkaClusterTools: ModuleID = "com.typesafe.akka" %% "akka-cluster-tools" % Version.akka
    val akkaSlf4j: ModuleID   = "com.typesafe.akka" %% "akka-slf4j"   % Version.akka
    val akkaTestkit: ModuleID = "com.typesafe.akka" %% "akka-testkit" % Version.akka
    val akkaHttp: ModuleID = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp
    val akkaHttpJson: ModuleID = "com.typesafe.akka" %% "akka-http-spray-json" % Version.akkaHttp
    val akkaHttpCors: ModuleID = "ch.megard"         %% "akka-http-cors" % Version.akkaHttpCors
    val sprayJson: ModuleID   = "io.spray"          %% "spray-json"   % Version.sprayJson
    val slf4j: ModuleID       = "org.slf4j"          % "slf4j-api"    % Version.slf4j
    val jclOverSlf4j: ModuleID = "org.slf4j"         % "jcl-over-slf4j" % Version.slf4j
    val log4jSlf4j: ModuleID  = "org.apache.logging.log4j" % "log4j-slf4j-impl" % Version.log4j
    val disruptor: ModuleID   = "com.lmax"           % "disruptor"    % Version.disruptor
    val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging" % Version.scalaLogging
    val catsCore: ModuleID    = "org.typelevel"     %% "cats-core"    % Version.cats
    val hadrian: ModuleID     = "com.opendatagroup" % "hadrian"       % Version.hadrian
    // spark 2.2.x
    val sparkMllib: ModuleID  = "org.apache.spark"  %% "spark-mllib"  % Version.spark excludeAll(ExclusionRules.sparkExclusions :_*)
    val sparkSql: ModuleID    = "org.apache.spark"  %% "spark-sql"    % Version.spark excludeAll(ExclusionRules.sparkExclusions :_*)
    val wokenMessages: ModuleID = "ch.chuv.lren.woken" %% "woken-messages" % Version.wokenMessages

    // Kamon
    val kamon: ModuleID        = "io.kamon" %% "kamon-core" % Version.kamon excludeAll ExclusionRules.excludeLogback
    val kamonAkka: ModuleID    = "io.kamon" %% "kamon-akka-2.5" % Version.kamonAkka excludeAll ExclusionRules.excludeLogback
    val kamonAkkaRemote: ModuleID = "io.kamon" %% "kamon-akka-remote-2.5" % Version.kamonAkkaRemote excludeAll ExclusionRules.excludeLogback
    val kamonAkkaHttp: ModuleID = "io.kamon" %% "kamon-akka-http-2.5" % Version.kamonAkkaHttp excludeAll ExclusionRules.excludeLogback
    val kamonSystemMetrics: ModuleID = "io.kamon" %% "kamon-system-metrics" % Version.kamonSystemMetrics excludeAll ExclusionRules.excludeLogback
    val kamonPrometheus: ModuleID = "io.kamon" %% "kamon-prometheus" % Version.kamonReporter excludeAll ExclusionRules.excludeLogback
    val kamonZipkin: ModuleID  =  "io.kamon" %% "kamon-zipkin" % Version.kamonReporter excludeAll ExclusionRules.excludeLogback
    val kamonSigar: ModuleID   = "io.kamon"           % "sigar-loader" % Version.kamonSigar

    val bugsnag: ModuleID      = "com.bugsnag"       % "bugsnag"      % Version.bugsnag excludeAll ExclusionRules.excludeJackson
  }

resolvers += "HBPMedical Bintray Repo" at "https://dl.bintray.com/hbpmedical/maven/"
resolvers += "opendatagroup maven" at "http://repository.opendatagroup.com/maven"

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings = commonSettings ++ gitSettings ++ scalafmtSettings

lazy val commonSettings =
  Seq(
    scalaVersion := "2.11.12",
    ensimeScalaVersion in ThisBuild := "2.11.12",
    organization in ThisBuild := "ch.chuv.lren.woken",
    organizationName in ThisBuild := "LREN CHUV for Human Brain Project",
    homepage in ThisBuild := Some(url(s"https://github.com/HBPMedical/${name.value}/#readme")),
    licenses in ThisBuild := Seq("AGPL-3.0" ->
      url(s"https://github.com/LREN-CHUV/${name.value}/blob/${version.value}/LICENSE")),
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
    fork in Test := true,
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

// Create a new MergeStrategy for aop.xml files
val aopMerge: MergeStrategy = new MergeStrategy {
  val name = "aopMerge"
  import scala.xml._
  import scala.xml.dtd._

  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val dt = DocType("aspectj", PublicID("-//AspectJ//DTD//EN", "http://www.eclipse.org/aspectj/dtd/aspectj.dtd"), Nil)
    val file = MergeStrategy.createMergeTarget(tempDir, path)
    val xmls: Seq[Elem] = files.map(XML.loadFile)
    val aspectsChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "aspects" \ "_")
    val weaverChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "weaver" \ "_")
    val options: String = xmls.map(x => (x \\ "aspectj" \ "weaver" \ "@options").text).mkString(" ").trim
    val weaverAttr = if (options.isEmpty) Null else new UnprefixedAttribute("options", options, Null)
    val aspects = new Elem(null, "aspects", Null, TopScope, false, aspectsChildren: _*)
    val weaver = new Elem(null, "weaver", weaverAttr, TopScope, false, weaverChildren: _*)
    val aspectj = new Elem(null, "aspectj", Null, TopScope, false, aspects, weaver)
    XML.save(file.toString, aspectj, "UTF-8", xmlDecl = false, dt)
    IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
    Right(Seq(file -> path))
  }
}



val customMergeStrategy: String => MergeStrategy = {
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
  case PathList("META-INF", "aop.xml") => aopMerge
  case s => MergeStrategy.defaultMergeStrategy(s)
}
