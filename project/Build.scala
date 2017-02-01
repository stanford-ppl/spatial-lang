import sbt._
import Keys._

object SpatialBuild extends Build {
  lazy val ARGON_HOME = sys.env.get("ARGON_HOME").getOrElse(error("Please set the ARGON_HOME environment variable."))
  
  val compileArgon = TaskKey[Unit]("compileArgon", "Compiles Argon")
  val compileArgonSettings = compileArgon := {
    import sys.process._

    val proc = scala.sys.process.Process(Seq("sbt", "compile"), new java.io.File(ARGON_HOME))
    val output = proc.run()
    val exitCode = output.exitValue()
    if (exitCode != 0) {
     exit(1)
    }
  }

  lazy val virtBuildSettings = Defaults.defaultSettings ++ Seq(
    organization := "stanford-ppl",
    scalaVersion := "2.11.2",
    publishArtifact in (Compile, packageDoc) := false,
    libraryDependencies += "org.virtualized" %% "virtualized" % "0.1",
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "compile",
    libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.3.2",

    retrieveManaged := true,
    scalacOptions += "-Yno-generic-signatures",

    excludeFilter in unmanagedSources := "*template-level*" || "*app-level*",

    // More strict error/warning checking
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
    // It would be very annoying to have to import these everywhere in this project
    scalacOptions ++= Seq("-language:higherKinds", "-language:implicitConversions"),

    addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full),
  
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    scalaSource in Test <<= baseDirectory(_ / "tests"),

    parallelExecution in Test := false,
    concurrentRestrictions in Global += Tags.limitAll(1), //we need tests to run in isolation across all projects

    compileArgonSettings,
    compile in Compile <<= (compile in Compile).dependsOn(compileArgon)
  )


  
  val scalacp = "/target/scala-2.11/classes/"
  lazy val argoncp = file(ARGON_HOME + scalacp)

  var deps = Seq(
    unmanagedClasspath in Compile <+= (baseDirectory) map { bd => Attributed.blank(argoncp) },
    unmanagedClasspath in Test <+= (baseDirectory) map { bd => Attributed.blank(argoncp) }
  )

  lazy val spatial = Project("spatial", file("."), settings = virtBuildSettings ++ deps)

  lazy val apps = Project("apps", file("apps"), settings = virtBuildSettings ++ deps) dependsOn (spatial)
}
