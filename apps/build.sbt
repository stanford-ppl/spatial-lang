val paradiseVersion = "2.1.0"

publishArtifact := false
trapExit := false

scalaSource in Compile := baseDirectory(_/ "src").value
resourceDirectory in Compile :=  baseDirectory(_/ "resources").value

//paradise
resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)
