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

  scalacOptions ++= Seq("-language:implicitConversions", "-language:higherKinds"),
  scalacOptions ++= Seq("-explaintypes", "-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),

  // Build documentation
  scalacOptions in (Compile,doc) ++= Seq("-groups", "-implicits"),

  // For when something goes super wrong with scalac
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
  .settings(commonSettings)
  .settings(assemblyJarName in assembly := "apps.jar")

val pirApps = List("DotProduct", "OuterProduct", "GEMM_Blocked", "SPMV_CRS", "PageRank_plasticine", "BlackScholes", 
                    "TPCHQ6", "Kmeans_plasticine", "GDA", "Gibbs_Ising2D", "Backprop")
addCommandAlias("pirapps", pirApps.map { app => s"; apps/run-main $app --cgra+" }.mkString("; ") )
addCommandAlias("spatial", "; apps/run-main")
addCommandAlias("make", "; apps/compile")
