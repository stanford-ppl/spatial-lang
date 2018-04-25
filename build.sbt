name := "spatial-lang"

val scalatestVersion = "3.0.1"
val paradiseVersion = "2.1.0"

val assemblySettings = Seq(
  test in assembly := {}
)

isSnapshot in ThisBuild := true

val commonSettings = assemblySettings ++ Seq(
  version := "0.1-SNAPSHOT",
  organization := "edu.stanford.cs.dawn",
  scalaVersion := "2.12.1",
  test in assembly := {},

  // publishTo in ThisBuild := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository"))),

  // POM settings for Sonatype
  homepage := Some(url("https://github.com/stanford-ppl/spatial-lang")),
  scmInfo := Some(ScmInfo(url("https://github.com/stanford-ppl/spatial-lang"),
                              "git@github.com:stanford-ppl/spatial-lang.git")),
  developers := List(
                     Developer("Matthew Feldman",
                               "mattfel1",
                               "mattfel@stanford.edu",
                               url("https://spatial.stanford.edu")),
                     Developer("David Koeplinger",
                               "dkoeplin",
                               "dkoeplin@stanford.edu",
                               url("https://spatial.stanford.edu")),
                     Developer("Raghu Prabhakar",
                               "raghup17",
                               "raghup17@stanford.edu",
                               url("https://spatial.stanford.edu")),
                     Developer("Yaqi Zhang",
                               "yaqiz",
                               "yaqiz@stanford.edu",
                               url("https://spatial.stanford.edu"))
                    ),
  licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
  publishMavenStyle := true,

  // Add sonatype repository settings
  publishTo in ThisBuild := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },

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

lazy val spatial = (project in file("spatial/core"))
  .dependsOn(argon, forge, virtualized)
  .settings(commonSettings)
  // .settings(mainClass in assembly := Some("com.example.Main"))
    // credentials += Credentials("My Maven Repo", "my.maven.repository", "username", "password"),
        
    // artifact in (Compile, assembly) := {
    //   val art = (artifact in (Compile, assembly)).value
    //   art.copy(`classifier` = Some("assembly"))
    // },
    // addArtifact(artifact in (Compile, assembly), assembly)
  // )

lazy val virtualized = (project in file("scala-virtualized"))
  .settings(commonSettings)


lazy val forge = (project in file("argon/forge"))
  .dependsOn(virtualized)
  .settings(commonSettings)


lazy val argon = (project in file("argon/core"))
  .dependsOn(forge, virtualized)
  .settings(commonSettings)

lazy val apps = project
  .dependsOn(spatial, virtualized)
  .settings(commonSettings)
  .settings(assemblyJarName in assembly := "apps.jar")




val pirApps = List("DotProduct", "OuterProduct", "GEMM_Blocked", "SPMV_CRS", "PageRank_plasticine", "BlackScholes", 
                    "TPCHQ6", "Kmeans_plasticine", "GDA", "Gibbs_Ising2D", "Backprop")
addCommandAlias("pirapps", pirApps.map { app => s"; apps/run-main $app --cgra+" }.mkString("; ") )
addCommandAlias("spatial", "; apps/run-main")
addCommandAlias("make", "; apps/compile")

// GPG Keys

// useGpg := true
// pgpReadOnly := false

