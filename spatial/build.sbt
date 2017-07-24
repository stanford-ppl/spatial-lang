scalaVersion in ThisBuild := "2.12.1"

organization in ThisBuild := "stanford-ppl"

version in ThisBuild := "1.0"

isSnapshot in ThisBuild := true

val scalatestVersion = "3.0.1"
val paradiseVersion = "2.1.0"

publishArtifact := false

lazy val spatial = (project in file("core"))
  .settings(Seq(

    libraryDependencies += "stanford-ppl" %% "argon" % version.value,
    libraryDependencies += "stanford-ppl" %% "forge" % version.value,
    libraryDependencies += "org.scalatest" %% "scalatest" % scalatestVersion % "test",
    //paradise
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += Resolver.sonatypeRepo("releases"),
    addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)

  ))
