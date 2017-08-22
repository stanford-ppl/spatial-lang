scalaVersion in ThisBuild := "2.12.1"

organization in ThisBuild := "stanford-ppl"

version in ThisBuild := "1.0"

name := "spatial-lang"

isSnapshot in ThisBuild := true

val scalatestVersion = "3.0.1"
val paradiseVersion = "2.1.0"

val assemblySettings = Seq(
  test in assembly := {}
)
val commonSettings = assemblySettings ++ Seq(
  incOptions := incOptions.value.withRecompileOnMacroDef(false),
  libraryDependencies += "org.scalatest" %% "scalatest" % scalatestVersion % "test",
  incOptions := incOptions.value.withRecompileOnMacroDef(false),

  scalacOptions ++= Seq("-language:implicitConversions", "-language:higherKinds"),
  scalacOptions ++= Seq("-explaintypes", "-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
  //scalacOptions ++= Seq("-Ytyper-debug"),

  //paradise
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)
)

publishArtifact := false

lazy val virtualized = (project in file("scala-virtualized"))
  .settings(assemblySettings)

lazy val forge = (project in file("argon/forge"))
  .dependsOn(virtualized)
  .settings(commonSettings)

lazy val argon = (project in file("argon/core"))
  .dependsOn(forge, virtualized)
  .settings(commonSettings)

lazy val spatial = (project in file("spatial/core"))
  .dependsOn(argon, forge, virtualized)
  .settings(commonSettings)
  .settings(assemblyJarName in assembly := "spatial-lang.jar")

lazy val apps = project
  .dependsOn(spatial, virtualized)
  .settings(assemblySettings)
  .settings(assemblyJarName in assembly := "apps.jar")

addCommandAlias("spatial", "; apps/run-main")
addCommandAlias("make", "; apps/compile")
