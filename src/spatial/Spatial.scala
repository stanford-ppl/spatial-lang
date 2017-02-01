package spatial

import argon.codegen.scalagen._
import argon.ops._
import argon.traversal.IRPrinter
import argon.{AppCore, CompilerCore, LibCore}
import spatial.api._
import spatial.dse._
import spatial.analysis._
import spatial.transform._
import spatial.codegen.scalagen.{ScalaGenVector, _}

protected trait SpatialOps extends OverloadHack with SpatialMetadataOps with BankingMetadataOps
     with IfThenElseOps with PrintOps with ControllerOps with MathOps with TextOps with DRAMOps with StringCastOps
     with HostTransferOps with ParameterOps with RangeOps with StructOps with UnrolledOps with VectorOps
     with ArrayExtOps with AssertOps

protected trait SpatialApi extends SpatialOps with SpatialMetadataApi with BankingMetadataApi
     with IfThenElseApi with PrintApi with ControllerApi with MathApi with TextApi with DRAMApi with StringCastApi
     with HostTransferApi with ParameterApi with RangeApi with StructApi with UnrolledApi with VectorApi
     with ArrayExtApi with AssertApi

protected trait SpatialExp extends SpatialOps with SpatialMetadataExp with BankingMetadataExp
     with NodeClasses with NodeUtils with ParameterRestrictions
     with IfThenElseExp with PrintExp with ControllerExp with MathExp with TextExp with DRAMExp with StringCastExp
     with HostTransferExp with ParameterExp with RangeExp with StructExp with UnrolledExp with VectorExp
     with ArrayExtExp with AssertExp

protected trait ScalaGenSpatial extends ScalaCodegen with ScalaSingleFileGen
  with ScalaGenBool with ScalaGenVoid with ScalaGenFixPt with ScalaGenFltPt with ScalaGenMixedNumeric
  with ScalaGenCounter with ScalaGenReg with ScalaGenSRAM with ScalaGenFIFO
  with ScalaGenIfThenElse with ScalaGenPrint with ScalaGenController with ScalaGenMath with ScalaGenText
  with ScalaGenDRAM with ScalaGenStringCast with ScalaGenHostTransfer with ScalaGenRange
  with ScalaGenUnrolled with ScalaGenVector
  with ScalaGenArray with ScalaGenArrayExt with ScalaGenAssert {

  override val IR: SpatialCompiler
}

protected trait SpatialCompiler extends CompilerCore with SpatialExp { self =>
  lazy val printer = new IRPrinter {val IR: self.type = self }

  // Traversals
  lazy val scalarAnalyzer = new ScalarAnalyzer { val IR: self.type = self }
//lazy val constFolding   = new ConstantFolding { val IR: self.type = self }
  lazy val levelAnalyzer  = new PipeLevelAnalyzer { val IR: self.type = self }
  lazy val dimAnalyzer    = new DimensionAnalyzer { val IR: self.type = self }

  lazy val unitPipeInsert = new UnitPipeTransformer { val IR: self.type = self }

  lazy val affineAnalyzer = new SpatialAccessAnalyzer { val IR: self.type = self }
  lazy val ctrlAnalyzer   = new ControlSignalAnalyzer { val IR: self.type = self }

  lazy val regCleanup     = new RegisterCleanup { val IR: self.type = self }

  lazy val memAnalyzer    = new MemoryAnalyzer { val IR: self.type = self; def localMems = ctrlAnalyzer.localMems }
  lazy val paramAnalyzer  = new ParameterAnalyzer{val IR: self.type = self }

  lazy val dse = new DSE {
    val IR: self.type = self
    def restricts  = paramAnalyzer.restrict
    def tileSizes  = paramAnalyzer.tileSizes
    def parFactors = paramAnalyzer.parFactors
    def localMems  = ctrlAnalyzer.localMems
    def metapipes  = ctrlAnalyzer.metapipes
    def top = ctrlAnalyzer.top.get
  }

  lazy val reduceAnalyzer = new ReductionAnalyzer { val IR: self.type = self }

  lazy val uctrlAnalyzer  = new UnrolledControlAnalyzer { val IR: self.type = self }

  lazy val rewriter       = new RewriteTransformer { val IR: self.type = self }

  lazy val unroller       = new UnrollingTransformer { val IR: self.type = self }

  lazy val bufferAnalyzer = new BufferAnalyzer { val IR: self.type = self; def localMems = uctrlAnalyzer.localMems }
  lazy val dramAddrAlloc  = new DRAMAddrAnalyzer { val IR: self.type = self; def memStreams = uctrlAnalyzer.memStreams }

  lazy val scalagen = new ScalaGenSpatial { val IR: self.type = self }

  // Traversal schedule
  passes += printer
  passes += scalarAnalyzer    // Perform bound and global analysis
//passes += constFolding      // Constant folding (TODO: Necessary?)
  passes += levelAnalyzer     // Initial pipe style annotation fixes
  passes += dimAnalyzer       // Correctness checks for onchip and offchip dimensions

  // --- Unit Pipe Insertion
  passes += printer
  passes += unitPipeInsert    // Wrap primitives in outer controllers
  passes += printer

  // --- Pre-Reg Cleanup
  passes += ctrlAnalyzer      // Control signal analysis

  // --- Register cleanup
  passes += printer
  passes += regCleanup        // Remove unused registers and corresponding reads/writes created in unit pipe transform
  passes += printer

  // --- Pre-DSE Analysis
  passes += scalarAnalyzer    // Bounds / global analysis
  passes += affineAnalyzer    // Memory access patterns
  passes += ctrlAnalyzer      // Control signal analysis
  passes += memAnalyzer       // Memory banking/buffering

  // --- DSE
  passes += dse               // TODO: Design space exploration

  // --- Post-DSE Analysis
  passes += scalarAnalyzer    // Bound and global analysis after param. finalization
  passes += reduceAnalyzer    // Reduce/accumulator specialization
  passes += memAnalyzer       // Finalize banking/buffering
  // TODO: models go here

  // --- Design Elaboration
  passes += printer
  passes += unroller          // Unrolling
  passes += printer

  passes += uctrlAnalyzer     // Analysis for unused register reads
  passes += regCleanup        // Duplicate register reads for each use
  passes += rewriter          // Post-unrolling rewrites (e.g. enabled register writes)

  // --- Post-Unroll Analysis
  passes += uctrlAnalyzer     // Control signal analysis (post-unrolling)
  passes += printer
  passes += bufferAnalyzer    // Set top controllers for n-buffers
  passes += dramAddrAlloc     // Get address offsets for each used DRAM object
  passes += printer

  // --- Code generation
  passes += scalagen
}

protected trait SpatialIR extends SpatialCompiler with SpatialApi
protected trait SpatialLib extends LibCore // Actual library implementation goes here

trait SpatialApp extends AppCore {
  val IR: SpatialIR = new SpatialIR { }
  val Lib: SpatialLib = new SpatialLib { def args: Array[String] = stagingArgs }
}

