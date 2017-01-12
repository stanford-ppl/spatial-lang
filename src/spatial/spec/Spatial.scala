package spatial.spec

import argon.codegen.scalagen._
import spatial.codegen.scalagen._
import argon.{AppCore, CompilerCore}
import argon.ops._
import argon.traversal.IRPrinter

trait SpatialOps
  extends IfThenElseOps with PrintOps with ControllerOps with MathOps with TextOps with DRAMOps with StringCastOps
     with HostTransferOps

trait SpatialApi extends SpatialOps
     with IfThenElseApi with PrintApi with ControllerApi with MathApi with TextApi with DRAMApi with StringCastApi
     with HostTransferApi

trait SpatialExp extends SpatialOps
     with IfThenElseExp with PrintExp with ControllerExp with MathExp with TextExp with DRAMExp with StringCastExp
     with HostTransferExp

trait ScalaGenSpatial extends ScalaCodegen with ScalaSingleFileGen
  with ScalaGenBool with ScalaGenFixPt with ScalaGenFltPt with ScalaGenMixedNumeric
  with ScalaGenIfThenElse with ScalaGenPrint with ScalaGenText with ScalaGenVoid
  with ScalaGenController with ScalaGenMath with ScalaGenCounter with ScalaGenDRAM with ScalaGenFIFO with ScalaGenHostTransfer
  with ScalaGenReg with ScalaGenSRAM {

  override val IR: SpatialCompiler
}

trait SpatialApp extends AppCore with SpatialApi
trait SpatialCompiler extends CompilerCore with SpatialExp { self =>
  lazy val printer = new IRPrinter {val IR: self.type = self }

  lazy val scalagen = new ScalaGenSpatial{val IR: self.type = self }
  passes += printer
  passes += scalagen
}


