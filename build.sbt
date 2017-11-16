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
        libraryDependencies ++= Seq(
          library.akkaActor,
          library.akkaRemote,
          library.akkaCluster,
          library.sprayJson,
          library.catsCore,
          library.hadrian,
          library.sparkMlServing,
          library.sparkMllib,
          library.wokenMessages,
          library.scalaCheck   % Test,
          library.scalaTest    % Test,
          library.akkaTestkit  % Test
        ),
        assemblyMergeStrategy in assembly := {
          case PathList("io", "hydrosphere", xs @ _*) => MergeStrategy.first
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
      val akka           = "2.3.16"
      val sprayJson      = "1.3.4"
      val cats           = "1.0.0-RC1"
      val hadrian        = "0.8.5"
      val sparkMlServing = "0.2.0"
      val spark          = "2.2.0"
      val wokenMessages  = "2.0.8"
    }
    object ExclusionRules {
      val excludeIvy = ExclusionRule(organization = "org.apache.ivy")
      val excludeMail = ExclusionRule(organization = "javax.mail")
      val excludeNettyIo = ExclusionRule(organization = "io.netty", artifact = "netty-all")
      val excludeQQ = ExclusionRule(organization = "org.scalamacros")
      val excludeParquet = ExclusionRule(organization = "org.apache.parquet")
      val excludeNlp = ExclusionRule(organization = "org.scalanlp")
      val excludeHadoop = ExclusionRule(organization = "org.apache.hadoop")
      val excludeSparkGraphx = ExclusionRule(organization = "org.apache.spark", artifact = "spark-graphx")
      val sparkExclusions = Seq(excludeIvy, excludeMail, excludeNettyIo, excludeQQ, excludeParquet, excludeNlp,
        excludeHadoop, excludeSparkGraphx)
    }
    val scalaCheck: ModuleID  = "org.scalacheck"    %% "scalacheck"   % Version.scalaCheck
    val scalaTest: ModuleID   = "org.scalatest"     %% "scalatest"    % Version.scalaTest
    val akkaActor: ModuleID   = "com.typesafe.akka" %% "akka-actor"   % Version.akka
    val akkaRemote: ModuleID  = "com.typesafe.akka" %% "akka-remote"  % Version.akka
    val akkaCluster: ModuleID = "com.typesafe.akka" %% "akka-cluster" % Version.akka
    val akkaTestkit: ModuleID = "com.typesafe.akka" %% "akka-testkit" % Version.akka
    val sprayJson: ModuleID   = "io.spray"          %% "spray-json"   % Version.sprayJson
    val catsCore: ModuleID    = "org.typelevel"     %% "cats-core"    % Version.cats
    val hadrian: ModuleID     = "com.opendatagroup" % "hadrian"       % Version.hadrian
    // spark 2.2.x
    val sparkMlServing: ModuleID = "io.hydrosphere" %% "spark-ml-serving-2_2" % Version.sparkMlServing excludeAll(ExclusionRules.sparkExclusions :_*)
    val sparkMllib: ModuleID  = "org.apache.spark"  %% "spark-mllib"  % Version.spark % "provided"
    val sparkSql: ModuleID    = "org.apache.spark"  %% "spark-sql"    % Version.spark % "provided"
    val wokenMessages: ModuleID = "eu.humanbrainproject.mip" %% "woken-messages" % Version.wokenMessages
  }

resolvers += "opendatagroup maven" at "http://repository.opendatagroup.com/maven"
resolvers += Resolver.bintrayRepo("hbpmedical", "maven")

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings = commonSettings ++ gitSettings ++ scalafmtSettings

lazy val commonSettings =
  Seq(
    scalaVersion := "2.11.8",
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
      "-language:_",
      "-target:jvm-1.8",
      "-encoding",
      "UTF-8"
    ),
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
    unmanagedSourceDirectories.in(Compile) := Seq(scalaSource.in(Compile).value),
    unmanagedSourceDirectories.in(Test) := Seq(scalaSource.in(Test).value),
    wartremoverWarnings in (Compile, compile) ++= Warts.unsafe,
    mainClass in Runtime := Some("eu.hbp.mip.woken.validation.Main"),
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
    scalafmtVersion := "1.1.0"
  )
