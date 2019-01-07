val akkaVersion = "2.5.17"
val breezeVersion = "0.13.2"
val sttpVersion = "1.3.3"

name := "scalaml"

scalaVersion := "2.12.7"

libraryDependencies  ++= Seq(
  "org.scalanlp" %% "breeze" % breezeVersion,
  "org.scalanlp" %% "breeze-natives" % breezeVersion,
  "org.scalanlp" %% "breeze-viz" % breezeVersion,
  "com.softwaremill.macwire" %% "macros" % "2.3.1" % Provided,
  "com.softwaremill.sttp" %% "akka-http-backend" % sttpVersion,
  "com.softwaremill.sttp" %% "core" % sttpVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.play" %% "play-json" % "2.6.13"
)

resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
