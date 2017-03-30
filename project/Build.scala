import sbt._
import Keys._

object SpatialBuild extends Build {
  lazy val ARGON_HOME = sys.env.get("ARGON_HOME").getOrElse(error("Please set the ARGON_HOME environment variable."))

  val compilerVersion = "2.12.1"
  val scalatestVersion = "3.0.1"
  val paradiseVersion = "2.1.0"  // "3.0.0-M7"

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "stanford-ppl",
    scalaVersion := compilerVersion,

    publishArtifact in (Compile, packageDoc) := false,
    //libraryDependencies += "org.virtualized" %% "virtualized" % virtualizedVersion,
    libraryDependencies += "org.scalatest" %% "scalatest" % scalatestVersion % "test",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "compile",
    libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.3.2",

    retrieveManaged := true,
    scalacOptions += "-Yno-generic-signatures",

    excludeFilter in unmanagedSources := "*template-level*" || "*app-level*" || "*resources*",

    // More strict error/warning checking
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
    // It would be very annoying to have to import these everywhere in this project
    scalacOptions ++= Seq("-language:higherKinds", "-language:implicitConversions", "-language:experimental.macros"),

    scalacOptions in (Compile, doc) ++= Seq(
      "-doc-root-content", 
      baseDirectory.value+"/root-doc.txt",
      "-diagrams",
      "-diagrams-debug",
      //"-diagrams-dot-timeout", "20", "-diagrams-debug",
      "-doc-title", name.value
    ),

    resolvers += Resolver.sonatypeRepo("releases"),
    scalacOptions += "-Xplugin-require:macroparadise",

    /** Macro Paradise **/
    addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full),

    /*
    /** SCALA-META **/
    resolvers += Resolver.bintrayIvyRepo("scalameta", "maven"),
    libraryDependencies += "org.scalameta" %% "scalameta" % metaVersion,
    libraryDependencies += "org.scalameta" %% "contrib" % metaVersion,

    addCompilerPlugin("org.scalameta" % "paradise" % paradiseVersion cross CrossVersion.full),

    scalacOptions += "-Xplugin-require:macroparadise",
    // temporary workaround for https://github.com/scalameta/paradise/issues/10
    scalacOptions in (Compile, console) := Seq(), // macroparadise plugin doesn't work in repl yet.
    // temporary workaround for https://github.com/scalameta/paradise/issues/55
    sources in (Compile, doc) := Nil, // macroparadise doesn't work with scaladoc yet.
    */

    scalaSource in Compile <<= baseDirectory(_ / "src"),
    scalaSource in Test <<= baseDirectory(_ / "test"),

    parallelExecution in Test := false,
    concurrentRestrictions in Global += Tags.limitAll(1)   //we need tests to run in isolation across all projects
  )

  /*
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

  val scalacp = "/target/scala-" + compilerVersion.dropRight(2) + "/classes/"
  lazy val argoncp = file(ARGON_HOME + scalacp)
  lazy val macrocp = file(ARGON_HOME + "/macros" + scalacp)
  lazy val virtucp = file(ARGON_HOME + "/scala-virtualized" + scalacp)

  var argon = Seq(
    compileArgonSettings,
    compile in Compile <<= (compile in Compile).dependsOn(compileArgon),
    unmanagedClasspath in Compile <+= (baseDirectory) map { bd => Attributed.blank(argoncp) },
    unmanagedClasspath in Test <+= (baseDirectory) map { bd => Attributed.blank(argoncp) },
    unmanagedClasspath in Compile <+= (baseDirectory) map { bd => Attributed.blank(macrocp) },
    unmanagedClasspath in Test <+= (baseDirectory) map { bd => Attributed.blank(macrocp) },
  )
  */
  lazy val argon = RootProject(file(ARGON_HOME))
  lazy val spatial = Project("spatial", file("."), settings = buildSettings) dependsOn (argon)
  lazy val apps = Project("apps", file("apps"), settings = buildSettings) dependsOn (spatial)
}
