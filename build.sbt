name := "macvendor"

version := "0.1.0"

scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += Resolver.sonatypeRepo("releases")

resolvers += Resolver.sonatypeRepo("public")

libraryDependencies ++= {
  val akkaV       = "2.4.7"
  val scalaTestV  = "2.2.5"
  Seq(
    "com.github.scopt" %% "scopt" % "3.5.0",
    "com.typesafe.akka" %% "akka-actor"                           % akkaV,
    "com.typesafe.akka" %% "akka-stream"                          % akkaV,
    "com.typesafe.akka" %% "akka-http-experimental"               % akkaV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental"    % akkaV,
    "com.typesafe.akka" %% "akka-http-testkit"                    % akkaV,
    "org.scalatest"     %% "scalatest"                            % scalaTestV % "test"
  )
}

enablePlugins(DockerPlugin)

dockerfile in docker := {

  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("java")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

buildOptions in docker := BuildOptions(cache = false)

