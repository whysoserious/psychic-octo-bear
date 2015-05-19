name := "psychic-octo-bear"

version := "1.0"

scalaVersion := "2.10.5"

scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers ++= Seq(
  "Typesafe repo"      at "http://repo.typesafe.com/typesafe/releases/",
  "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "sonatype releases"  at "https://oss.sonatype.org/content/repositories/releases",
  "twitter"            at "http://maven.twttr.com",
  "jclarity"           at "https://repo.jclarity.com/content/groups/public/",
  "clojars"            at "https://clojars.org/repo")

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

val akkaVersion = "2.3.11"

libraryDependencies ++= Seq(
  "com.typesafe.akka"     %% "akka-actor"         % akkaVersion,
  "com.typesafe.akka"     %% "akka-kernel"        % akkaVersion,
  "com.typesafe.akka"     %% "akka-slf4j"         % akkaVersion,
  "com.typesafe.akka"     %% "akka-testkit"       % akkaVersion,
  "com.typesafe.akka"     %% "akka-remote"        % akkaVersion,
  "net.liftweb"           %% "lift-json"          % "2.6",
  "com.github.levkhomich" %% "akka-tracing-core"  % "0.4")

fork in run := true
