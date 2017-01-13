name := "spatial"
version := "1.0"
isSnapshot := true
organization := "stanford-ppl"
scalaVersion := "2.11.2"
scalaSource in Compile <<= baseDirectory(_/ "src")
scalaSource in Test <<= baseDirectory(_/"test")

parallelExecution in Test := false

publishArtifact in (Compile, packageDoc) := false
publishArtifact in (Test, packageBin) := true

val paradiseVersion = "2.0.1"

libraryDependencies += "org.virtualized" %% "virtualized" % "0.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test"
libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "compile"
libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.3.2"

addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)
