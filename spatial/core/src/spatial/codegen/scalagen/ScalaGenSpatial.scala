package spatial.codegen.scalagen

import argon.core._
import argon.codegen.scalagen._

trait ScalaGenSpatial extends ScalaCodegen with ScalaFileGen
  with ScalaGenArray with ScalaGenSpatialArrayExt with ScalaGenSpatialBool with ScalaGenSpatialFixPt with ScalaGenSpatialFltPt
  with ScalaGenHashMap with ScalaGenIfThenElse with ScalaGenStructs with ScalaGenSpatialStruct
  with ScalaGenString with ScalaGenUnit with ScalaGenFunction with ScalaGenVariables
  with ScalaGenDebugging with ScalaGenFILO
  with ScalaGenController with ScalaGenCounter with ScalaGenDRAM with ScalaGenFIFO with ScalaGenHostTransfer with ScalaGenMath
  with ScalaGenRange with ScalaGenReg with ScalaGenSRAM with ScalaGenUnrolled with ScalaGenVector
  with ScalaGenStream with ScalaGenAssert
  with ScalaGenLineBuffer with ScalaGenRegFile with ScalaGenStateMachine with ScalaGenFileIO with ScalaGenBinaryFileIO
  with ScalaGenDelays with ScalaGenLUTs
  with ScalaGenVarReg with ScalaGenSwitch {

  override def copyDependencies(out: String): Unit = {
    dependencies ::= FileDep("scalagen", "Makefile", "../")
    dependencies ::= FileDep("scalagen", "run.sh", "../")
    dependencies ::= FileDep("scalagen", "build.sbt", "../")
    dependencies ::= FileDep("scalagen/project", "build.properties", "../project/")
    super.copyDependencies(out)
  }
}