package spatial

import argon.codegen.scalagen._
import argon.ops._
import argon.traversal.IRPrinter
import argon.{AppCore, CompilerCore}

import spatial.api._
import spatial.analysis._
import spatial.transform._

import spatial.codegen.scalagen._

trait SpatialOps extends OverloadHack with SpatialMetadataOps with BankingMetadataOps
     with IfThenElseOps with PrintOps with ControllerOps with MathOps with TextOps with DRAMOps with StringCastOps
     with HostTransferOps with ParameterOps with RangeOps with StructOps

trait SpatialApi extends SpatialOps with SpatialMetadataApi with BankingMetadataApi
     with IfThenElseApi with PrintApi with ControllerApi with MathApi with TextApi with DRAMApi with StringCastApi
     with HostTransferApi with ParameterApi with RangeApi with StructApi

trait SpatialExp extends SpatialOps with SpatialMetadataExp with BankingMetadataExp with NodeClasses with NodeUtils
     with IfThenElseExp with PrintExp with ControllerExp with MathExp with TextExp with DRAMExp with StringCastExp
     with HostTransferExp with ParameterExp with RangeExp with StructExp

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

  // Traversals
  lazy val boundAnalyzer  = new BoundAnalyzer { val IR: self.type = self }
  lazy val globalAnalyzer = new GlobalAnalyzer { val IR: self.type = self }
  lazy val constFolding   = new ConstantFolding { val IR: self.type = self }
  lazy val levelAnalyzer  = new PipeLevelAnalyzer { val IR: self.type = self }
  lazy val dimAnalyzer    = new DimensionAnalyzer { val IR: self.type = self }

  lazy val unitPipeInsert = new UnitPipeTransformer { val IR: self.type = self }

  lazy val affineAnalyzer = new SpatialAccessAnalyzer { val IR: self.type = self }
  lazy val ctrlAnalyzer   = new ControlSignalAnalyzer { val IR: self.type = self }
  lazy val memAnalyzer    = new MemoryAnalyzer { val IR: self.type = self; val localMems = ctrlAnalyzer.localMems }


  lazy val scalagen = new ScalaGenSpatial { val IR: self.type = self }

  // Traversal schedule
  passes += printer
  passes += boundAnalyzer     // Perform bound analysis for parameters
  passes += globalAnalyzer    // Check for values which can be computed outside controller
//passes += constFolding      // Constant folding (TODO: Necessary?)
  passes += levelAnalyzer     // Initial pipe style annotation fixes
  passes += dimAnalyzer       // Correctness checks for onchip and offchip dimensions

  // --- Unit Pipe Insertion
  passes += unitPipeInsert    // Wrap primitives in outer controllers

  // --- Pre-DSE analysis
  passes += affineAnalyzer    // Memory access patterns
  passes += ctrlAnalyzer      // Control signal analysis
  passes += memAnalyzer       // Memory banking

  passes += scalagen
}


