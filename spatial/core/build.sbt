name := "spatial"

scalaSource in Compile := baseDirectory(_/ "src").value
scalaSource in Test := baseDirectory(_/"test").value
resourceDirectory in Compile :=  baseDirectory(_/ "resources").value
