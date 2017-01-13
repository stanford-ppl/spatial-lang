import sbt._
import Keys._

object SpatialBuild extends Build {
  lazy val ARGON_HOME = sys.env.get("ARGON_HOME").getOrElse(error("Please set the ARGON_HOME environment variable."))
  
  val virtBuildSettings = Defaults.defaultSettings ++ Seq(
    organization := "stanford-ppl",
    scalaVersion := "2.11.2",
    publishArtifact in (Compile, packageDoc) := false,
    libraryDependencies += "org.virtualized" %% "virtualized" % "0.1",
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "compile",
    libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.3.2",

    retrieveManaged := true,
    scalacOptions += "-Yno-generic-signatures",

    addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full),
  
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    scalaSource in Test <<= baseDirectory(_ / "tests"),

    parallelExecution in Test := false,
    concurrentRestrictions in Global += Tags.limitAll(1) //we need tests to run in isolation across all projects
  )

  
  val scalacp = "/target/scala-2.11/classes/"
  lazy val argon = file(ARGON_HOME + scalacp)

  var deps = Seq(
    unmanagedClasspath in Compile <+= (baseDirectory) map { bd => Attributed.blank(argon) },
    unmanagedClasspath in Test <+= (baseDirectory) map { bd => Attributed.blank(argon) }
  )

  lazy val spatial = Project("spatial", file("."), settings = virtBuildSettings ++ deps)
}
