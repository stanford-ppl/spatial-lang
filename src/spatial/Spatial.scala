package spatial

import argon.codegen.scalagen._
import argon.ops._
import argon.traversal.IRPrinter
import argon.{AppCore, CompilerCore}
import spatial.analysis._
import spatial.api._
import spatial.codegen.scalagen._

trait SpatialOps extends OverloadHack with SpatialMetadataOps
     with IfThenElseOps with PrintOps with ControllerOps with MathOps with TextOps with DRAMOps with StringCastOps
     with HostTransferOps with ParameterOps with RangeOps

trait SpatialApi extends SpatialOps with SpatialMetadataApi
     with IfThenElseApi with PrintApi with ControllerApi with MathApi with TextApi with DRAMApi with StringCastApi
     with HostTransferApi with ParameterApi with RangeApi

trait SpatialExp extends SpatialOps with SpatialMetadataExp with NodeClasses
     with IfThenElseExp with PrintExp with ControllerExp with MathExp with TextExp with DRAMExp with StringCastExp
     with HostTransferExp with ParameterExp with RangeExp

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


