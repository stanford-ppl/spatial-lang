val paradiseVersion = "2.1.0"

publishArtifact := false

scalaSource in Compile := baseDirectory(_/ "src").value

//paradise
resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)
