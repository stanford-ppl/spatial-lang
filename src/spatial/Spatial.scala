package spatial

import argon.codegen.scalagen._
import argon.codegen.chiselgen._
import argon.codegen.pirgen._
import argon.codegen.cppgen._
import argon.core.Staging
import argon.ops._
import argon.traversal.IRPrinter
import argon.{AppCore, CompilerCore, LibCore}
import spatial.api._
import spatial.dse._
import spatial.analysis._
import spatial.transform._
import spatial.codegen.scalagen._
import spatial.codegen.chiselgen._
import spatial.codegen.pirgen._
import spatial.codegen.cppgen._


protected trait SpatialExp extends Staging
  with ArrayExp with ArrayExtExp with AssertExp with BoolExp with CastExp with FixPtExp with FltPtExp
  with HashMapExp with IfThenElseExp with MixedNumericExp with PrintExp with StringCastExp with StructExp
  with TextExp with TupleExp with VoidExp

  with ControllerExp with CounterExp with DRAMExp with FIFOExp with HostTransferExp with MathExp
  with MemoryExp with ParameterExp with RangeExp with RegExp with SRAMExp with StagedUtilExp with UnrolledExp with VectorExp
  with StreamExp with PinExp with AlteraVideoExp

  with NodeClasses with NodeUtils with ParameterRestrictions with SpatialMetadataExp with BankingMetadataExp


protected trait SpatialApi extends SpatialExp
  with ArrayApi with ArrayExtApi with AssertApi with BoolApi with CastApi with FixPtApi with FltPtApi
  with HashMapApi with IfThenElseApi with MixedNumericApi with PrintApi with StringCastApi with StructApi
  with TextApi with TupleApi with VoidApi

  with ControllerApi with CounterApi with DRAMApi with FIFOApi with HostTransferApi with MathApi
  with MemoryApi with ParameterApi with RangeApi with RegApi with SRAMApi with StagedUtilApi with UnrolledApi with VectorApi
  with StreamApi with PinApi with AlteraVideoApi

  with SpatialMetadataApi with BankingMetadataApi


protected trait ScalaGenSpatial extends ScalaCodegen with ScalaFileGen
  with ScalaGenArray with ScalaGenArrayExt with ScalaGenAssert with ScalaGenBool with ScalaGenFixPt with ScalaGenFltPt
  with ScalaGenHashMap with ScalaGenIfThenElse with ScalaGenMixedNumeric with ScalaGenPrint with ScalaGenStringCast with ScalaGenStructs
  with ScalaGenText with ScalaGenVoid

  with ScalaGenController with ScalaGenCounter with ScalaGenDRAM with ScalaGenFIFO with ScalaGenHostTransfer with ScalaGenMath
  with ScalaGenRange with ScalaGenReg with ScalaGenSRAM with ScalaGenUnrolled with ScalaGenVector
  with ScalaGenStream {

  override val IR: SpatialCompiler
}

protected trait ChiselGenSpatial extends ChiselCodegen with ChiselFileGen
  with ChiselGenBool with ChiselGenVoid with ChiselGenFixPt with ChiselGenFltPt with ChiselGenMixedNumeric
  with ChiselGenCounter with ChiselGenReg with ChiselGenSRAM with ChiselGenFIFO 
  with ChiselGenIfThenElse with ChiselGenPrint with ChiselGenController with ChiselGenMath with ChiselGenText
  with ChiselGenDRAM with ChiselGenStringCast with ChiselGenHostTransfer with ChiselGenUnrolled with ChiselGenVector
  with ChiselGenArray with ChiselGenAlteraVideo with ChiselGenStream with ChiselGenStructs {

  override val IR: SpatialCompiler
}

protected trait PIRGenSpatial extends PIRCodegen with PIRFileGen 
  with PIRGenPrint with PIRGenController 
  //with PIRGenCounter with PIRGenReg with PIRGenSRAM with PIRGenFIFO with PIRGenMath 
  //with PIRGenDRAM with PIRGenStringCast with PIRGenHostTransfer with PIRGenUnrolled with PIRGenVector
  //with PIRGenArray 
  {

  override val IR: SpatialCompiler
}

protected trait CppGenSpatial extends CppCodegen with CppFileGen
  with CppGenBool with CppGenVoid with CppGenFixPt with CppGenFltPt with CppGenMixedNumeric
  with CppGenCounter with CppGenReg with CppGenSRAM with CppGenFIFO 
  with CppGenIfThenElse with CppGenPrint with CppGenController with CppGenMath with CppGenText
  with CppGenDRAM with CppGenStringCast with CppGenHostTransfer with CppGenUnrolled with CppGenVector
  with CppGenArray with CppGenArrayExt with CppGenAsserts with CppGenRange with CppGenAlteraVideo with CppGenStream
  with CppGenHashMap{

  override val IR: SpatialCompiler
}

protected trait TreeWriter extends TreeGenSpatial {
  override val IR: SpatialCompiler
}


protected trait SpatialCompiler extends CompilerCore with SpatialExp with SpatialApi with PIRCommonExp { self =>
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
  lazy val regReadCSE     = new RegReadCSE { val IR: self.type = self }

  lazy val memAnalyzer    = new MemoryAnalyzer { val IR: self.type = self; def localMems = ctrlAnalyzer.localMems }
  lazy val paramAnalyzer  = new ParameterAnalyzer{val IR: self.type = self }

  lazy val scopeCheck     = new ScopeCheck { val IR: self.type = self }

  lazy val dse = new DSE {
    val IR: self.type = self
    def restricts  = paramAnalyzer.restrict
    def tileSizes  = paramAnalyzer.tileSizes
    def parFactors = paramAnalyzer.parFactors
    def localMems  = ctrlAnalyzer.localMems
    def metapipes  = ctrlAnalyzer.metapipes
    def top = ctrlAnalyzer.top.get
  }

  lazy val transferExpand = new TransferSpecialization { val IR: self.type = self }

  lazy val reduceAnalyzer = new ReductionAnalyzer { val IR: self.type = self }

  lazy val uctrlAnalyzer  = new UnrolledControlAnalyzer { val IR: self.type = self }

  lazy val rewriter       = new RewriteTransformer { val IR: self.type = self }

  lazy val unroller       = new UnrollingTransformer { val IR: self.type = self }

  lazy val bufferAnalyzer = new BufferAnalyzer { val IR: self.type = self; def localMems = uctrlAnalyzer.localMems }
  lazy val streamAnalyzer = new StreamAnalyzer { val IR: self.type = self ; def streamPipes = uctrlAnalyzer.streampipes; def streamEnablers = uctrlAnalyzer.streamEnablers }
  lazy val dramAddrAlloc  = new DRAMAddrAnalyzer { val IR: self.type = self; def memStreams = uctrlAnalyzer.memStreams }

  lazy val scalagen = new ScalaGenSpatial { val IR: self.type = self; override def shouldRun = SpatialConfig.enableScala }
  lazy val chiselgen = new ChiselGenSpatial { val IR: self.type = self; override def shouldRun = SpatialConfig.enableChisel }
  lazy val pirgen = new PIRGenSpatial { val IR: self.type = self; override def shouldRun = SpatialConfig.enablePIR }
  lazy val cppgen = new CppGenSpatial { val IR: self.type = self; override def shouldRun = SpatialConfig.enableCpp }
  lazy val treegen = new TreeGenSpatial { val IR: self.type = self; override def shouldRun = SpatialConfig.enableTree }

  // Traversal schedule
  passes += printer
  passes += scalarAnalyzer    // Perform bound and global analysis
  passes += scopeCheck        // Check that illegal host values are not used in the accel block
//passes += constFolding      // Constant folding (TODO: Necessary?)
  passes += levelAnalyzer     // Initial pipe style annotation fixes
  passes += dimAnalyzer       // Correctness checks for onchip and offchip dimensions

  // --- Unit Pipe Insertion
  passes += printer
  passes += unitPipeInsert    // Wrap primitives in outer controllers
  passes += regReadCSE        // CSE register reads in inner pipelines
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

  // --- Post-DSE Expansion
  // NOTE: Small compiler pass ordering issue here:
  // We may need bound information during node expansion,
  // but we also need to reanalyze bounds to account for expanded nodes
  // For now just doing it twice
  passes += scalarAnalyzer    // Bounds / global analysis
  passes += printer
  passes += transferExpand    // Expand burst loads/stores from single abstract nodes
  passes += levelAnalyzer     // Pipe style annotation fixes after expansion

  // --- Post-Expansion Cleanup
  passes += printer
  passes += regReadCSE        // CSE register reads in inner pipelines
  passes += scalarAnalyzer    // Bounds / global analysis
  passes += ctrlAnalyzer      // Control signal analysis
  passes += regCleanup        // Remove unused registers and corresponding reads/writes created in unit pipe transform

  // --- Pre-Unrolling Analysis
  passes += ctrlAnalyzer      // Control signal analysis
  passes += affineAnalyzer    // Memory access patterns
  passes += reduceAnalyzer    // Reduce/accumulator specialization
  passes += memAnalyzer       // Finalize banking/buffering
  // TODO: models go here

  // --- Design Elaboration
  passes += printer
  passes += unroller          // Unrolling
  passes += regReadCSE        // CSE register reads in inner pipelines
  passes += printer

  passes += uctrlAnalyzer     // Analysis for unused register reads
  passes += printer
  passes += regCleanup        // Duplicate register reads for each use
  passes += rewriter          // Post-unrolling rewrites (e.g. enabled register writes)
  passes += printer

  // --- Post-Unroll Analysis
  passes += scopeCheck        // Check that illegal host values are not used in the accel block
  passes += uctrlAnalyzer     // Control signal analysis (post-unrolling)
  passes += printer
  passes += bufferAnalyzer    // Set top controllers for n-buffers
  passes += streamAnalyzer    // Set stream pipe children fifo dependencies
  passes += dramAddrAlloc     // Get address offsets for each used DRAM object
  passes += printer

  // --- Code generation
  passes += scalagen
  passes += chiselgen
  passes += pirgen 
  passes += cppgen
  passes += treegen
}

protected trait SpatialIR extends SpatialCompiler
protected trait SpatialLib extends LibCore // Actual library implementation goes here

trait SpatialApp extends AppCore {
  import spatial.targets._

  private def __target: FPGATarget = Targets.targets.find(_.name == SpatialConfig.targetName).getOrElse{ DefaultTarget }
  val target = __target

  val IR: SpatialIR = new SpatialIR { def target = SpatialApp.this.target }
  val Lib: SpatialLib = new SpatialLib { def args: Array[String] = stagingArgs }
}

