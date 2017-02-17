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
    
  val compilerVersion = "2.12.1"
  val scalatestVersion = "3.0.1"
  val paradiseVersion = "2.1.0"  // check here: https://github.com/scalamacros/paradise/releases

  lazy val virtBuildSettings = Defaults.defaultSettings ++ Seq(
    organization := "stanford-ppl",
    scalaVersion := compilerVersion,


    publishArtifact in (Compile, packageDoc) := false,
    libraryDependencies += "org.virtualized" %% "virtualized" % "0.1",
    libraryDependencies += "org.scalatest" %% "scalatest" % scalatestVersion % "test",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "compile",
    libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.3.2",

    retrieveManaged := true,
    scalacOptions += "-Yno-generic-signatures",

    excludeFilter in unmanagedSources := "*template-level*" || "*app-level*" || "*resources*",

    // More strict error/warning checking
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
    // It would be very annoying to have to import these everywhere in this project
    scalacOptions ++= Seq("-language:higherKinds", "-language:implicitConversions"),

    scalacOptions in (Compile, doc) ++= Seq(
      "-doc-root-content", 
      baseDirectory.value+"/root-doc.txt",
      "-diagrams",
      "-diagrams-debug",
      //"-diagrams-dot-timeout", "20", "-diagrams-debug",
      "-doc-title", name.value
    ),

    addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full),
  
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    scalaSource in Test <<= baseDirectory(_ / "test"),

    parallelExecution in Test := false,
    concurrentRestrictions in Global += Tags.limitAll(1), //we need tests to run in isolation across all projects

    compileArgonSettings,
    compile in Compile <<= (compile in Compile).dependsOn(compileArgon)
  )


  
  val scalacp = "/target/scala-" + compilerVersion.dropRight(2) + "/classes/"
  lazy val argoncp = file(ARGON_HOME + scalacp)

  var deps = Seq(
    unmanagedClasspath in Compile <+= (baseDirectory) map { bd => Attributed.blank(argoncp) },
    unmanagedClasspath in Test <+= (baseDirectory) map { bd => Attributed.blank(argoncp) }
  )

  lazy val spatial = Project("spatial", file("."), settings = virtBuildSettings ++ deps)

  lazy val apps = Project("apps", file("apps"), settings = virtBuildSettings ++ deps) dependsOn (spatial)
}
