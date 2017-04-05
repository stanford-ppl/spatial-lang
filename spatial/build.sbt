name := "spatial"

organization := "stanford-ppl"

scalaVersion in ThisBuild := "2.12.1"

version := "1.0"

isSnapshot := true

excludeFilter in unmanagedSources := "*template-level*" || "*app-level*" || "*resources*"

val scalatestVersion = "3.0.1"
val paradiseVersion = "2.1.0"

libraryDependencies += "org.scalatest" %% "scalatest" % scalatestVersion % "test"

scalaSource in Compile := baseDirectory(_/ "src").value
scalaSource in Test := baseDirectory(_/"test").value
resourceDirectory in Compile :=  baseDirectory(_/ "resources").value

//paradise
resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)


/*
lazy val spatial = (project in file("."))
  .settings(Seq(
    libraryDependencies += "stanford-ppl" %% "argon" % version.value,
    libraryDependencies += "stanford-ppl" %% "forge" % version.value
  ))
*/
