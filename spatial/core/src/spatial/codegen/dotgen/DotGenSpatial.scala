package spatial.codegen.dotgen

import argon.codegen.dotgen._
import argon.core._

trait DotGenSpatial extends DotCodegen with DotFileGen
  with DotGenEmpty
  with DotGenBoolean with DotGenUnit with DotGenFixPt with DotGenFltPt
  with DotGenCounter with DotGenReg with DotGenSRAM with DotGenFIFO
  with DotGenIfThenElse with DotGenController with DotGenMath with DotGenString
  with DotGenDRAM with DotGenHostTransfer with DotGenUnrolled with DotGenVector
  with DotGenArray with DotGenAlteraVideo with DotGenStream with DotGenRetiming with DotGenStruct{

  override def copyDependencies(out: String): Unit = {
    dependencies ::= FileDep("dotgen", "run.sh", "../")
    super.copyDependencies(out)
  }
}
