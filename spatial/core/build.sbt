name := "spatial"

libraryDependencies ++= Seq(
  "org.encog" % "encog-core" % "3.3.0",
  "com.github.darrenjw" %% "scala-glm" % "0.3",
  "org.scalanlp" %% "breeze" % "0.13",
  "org.scalanlp" %% "breeze-viz" % "0.13",
  "org.scalanlp" %% "breeze-natives" % "0.13"
)

scalaSource in Compile := baseDirectory(_/ "src").value
scalaSource in Test := baseDirectory(_/"test").value
resourceDirectory in Compile :=  baseDirectory(_/ "resources").value

