scalacOptions ++= Seq( "-unchecked", "-deprecation" )

resolvers += Classpaths.sbtPluginReleases

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25" // Needed by sbt-git
libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.25" // Needed by sbt-git

// App Packaging
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")

// Dependency Resolution
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0")

// Code Quality
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0") // scalastyle

addSbtPlugin("org.wartremover"   % "sbt-wartremover" % "2.3.5") // Wartremover

// Code formatter
addSbtPlugin("com.lucidchart"    % "sbt-scalafmt"    % "1.15")

// Copyright headers
addSbtPlugin("de.heikoseeberger" % "sbt-header"      % "4.1.0")

// Versioning
addSbtPlugin("com.typesafe.sbt"  % "sbt-git"         % "0.9.3")
