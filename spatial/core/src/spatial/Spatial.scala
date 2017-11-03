package spatial

import argon.ArgonApp
import argon.ArgonCompiler
import argon.analysis.ParamFinalizer
import argon.core.{Config, State}
import argon.traversal.IRPrinter
import argon.util.Report
import spatial.aliases._
import spatial.dse._
import spatial.analysis._
import spatial.transform._
import spatial.codegen.pirgen._
import spatial.codegen.chiselgen.ChiselGenSpatial
import spatial.codegen.cppgen.CppGenSpatial
import spatial.codegen.dotgen.DotGenSpatial
import spatial.codegen.scalagen.ScalaGenSpatial
import spatial.interpreter.Interpreter
import spatial.lang.cake.SpatialExternal
import spatial.targets.{DefaultTarget, FPGATarget, Targets}

import scala.language.existentials

object dsl extends SpatialExternal {
  type SpatialApp = spatial.SpatialApp
}

trait SpatialCompiler extends ArgonCompiler {
  // Traversal schedule
  override def createTraversalSchedule(state: State) = {
    if (spatialConfig.enableRetiming) Report.warn("Spatial: retiming enabled")

    lazy val printer = IRPrinter(state)

    // Traversals
    lazy val scalarAnalyzer = new ScalarAnalyzer { var IR = state }
    lazy val levelAnalyzer  = new ControlLevelAnalyzer { var IR = state }
    lazy val dimAnalyzer    = new DimensionAnalyzer { var IR = state }

    lazy val affineAnalyzer = new SpatialAccessAnalyzer { var IR = state }
    lazy val ctrlAnalyzer   = new ControlSignalAnalyzer { var IR = state }

    lazy val switchInsert   = SwitchTransformer(state)
    lazy val switchOptimize = SwitchOptimization(state)
    lazy val unitPipeInsert = UnitPipeTransformer(state)
    lazy val pipeMerger     = PipeMergerTransformer(state)
    lazy val regCleanup     = RegisterCleanup(state)
    lazy val regReadCSE     = RegReadCSE(state)
    lazy val rewriter       = RewriteTransformer(state)
    lazy val unroller       = UnrollingTransformer(state)

    lazy val memAnalyzer    = new MemoryAnalyzer { var IR = state; def localMems = ctrlAnalyzer.localMems }
    lazy val paramAnalyzer  = new ParameterAnalyzer{var IR = state }
    lazy val heuristicAnalyzer = new HeuristicAnalyzer { var IR = state }

    lazy val sanityCheck     = new SanityCheck { var IR = state }

    lazy val contentionAnalyzer = new ContentionAnalyzer{ var IR = state; def top = ctrlAnalyzer.top.get }
    lazy val latencyAnalyzer = LatencyAnalyzer(IR = state, latencyModel = target.latencyModel)
    lazy val areaAnalyzer = spatialConfig.target.areaAnalyzer(state)

    lazy val controlSanityCheck = new ControllerSanityCheck { var IR = state }

    lazy val retiming     = PipeRetimer(IR = state, latencyModel = target.latencyModel)
    lazy val initAnalyzer = InitiationAnalyzer(IR = state, latencyModel = target.latencyModel)

    lazy val dse = new DSE {
      var IR = state
      def restricts  = heuristicAnalyzer.restrict
      def tileSizes  = paramAnalyzer.tileSizes
      def parFactors = paramAnalyzer.parFactors
      def localMems  = ctrlAnalyzer.localMems
      def metapipes  = ctrlAnalyzer.metapipes
      def top = ctrlAnalyzer.top.get
    }
    lazy val finalizer = ParamFinalizer(IR = state)

    lazy val transferExpand = new TransferSpecialization { var IR = state }
    lazy val reduceAnalyzer = new ReductionAnalyzer { var IR = state }
    lazy val uctrlAnalyzer  = new UnrolledControlAnalyzer { var IR = state }

    lazy val bufferAnalyzer = new BufferAnalyzer { var IR = state; def localMems = uctrlAnalyzer.localMems }
    lazy val streamAnalyzer = new StreamAnalyzer {
      var IR = state ;
      def streamPipes = uctrlAnalyzer.streampipes
      def streamEnablers = uctrlAnalyzer.streamEnablers
      def streamHolders = uctrlAnalyzer.streamHolders
      def streamLoadCtrls = uctrlAnalyzer.streamLoadCtrls
      def tileTransferCtrls = uctrlAnalyzer.tileTransferCtrls
      def streamParEnqs = uctrlAnalyzer.streamParEnqs
    }

    lazy val friendlyTransformer = FriendlyTransformer(IR = state)
    lazy val rotateFixer = RotateTransformer(IR = state)

    lazy val lutTransform  = MemoryTransformer(IR = state)
    lazy val sramTransform = new AffineAccessTransformer { var IR = state }

    lazy val pirRetimer = new PIRHackyRetimer { var IR = state }
    lazy val pirTiming  = new PIRHackyLatencyAnalyzer { var IR = state }

    lazy val argMapper  = new ArgMappingAnalyzer {
      var IR = state
      def memStreams = uctrlAnalyzer.memStreams
      def argPorts = uctrlAnalyzer.argPorts
      def genericStreams = uctrlAnalyzer.genericStreams
    }

    lazy val scalagen = new ScalaGenSpatial { var IR = state; def localMems = uctrlAnalyzer.localMems }
    lazy val chiselgen = new ChiselGenSpatial { var IR = state }
    lazy val pirgen = new PIRGenSpatial { var IR = state }
    lazy val cppgen = new CppGenSpatial { var IR = state }
    lazy val treegen = new TreeGenSpatial { var IR = state }
    lazy val dotgen = new DotGenSpatial { var IR = state }
    lazy val interpreter = new Interpreter { var IR = state }

    passes += printer
    passes += friendlyTransformer
    passes += printer
    if (spatialConfig.rewriteLUTs) {
      passes += lutTransform    // Change LUTs to SRAM with initial value metadata
      passes += printer
    }
    passes += scalarAnalyzer    // Perform bound and global analysis
    passes += levelAnalyzer     // Initial pipe style annotation fixes
    passes += dimAnalyzer       // Correctness checks for onchip and offchip dimensions

    // --- Unit Pipe Insertion
    passes += switchInsert      // Change nested if-then-else statements to Switch controllers
    passes += switchOptimize    // Remove unneeded switches
    passes += printer
    passes += unitPipeInsert    // Wrap primitives in outer controllers
    passes += printer
    passes += regReadCSE        // CSE register reads in inner pipelines
    passes += printer

    // --- Pre-Reg Cleanup
    passes += ctrlAnalyzer      // Control signal analysis

    // --- Register cleanup
    passes += printer
    passes += regCleanup        // Remove unused registers and corresponding reads/writes created in unit pipe transform
    passes += printer
    passes += sanityCheck        // Check that illegal host values are not used in the accel block

    // --- Pre-DSE Analysis
    passes += scalarAnalyzer    // Bounds / global analysis
    passes += affineAnalyzer    // Memory access patterns
    passes += ctrlAnalyzer      // Control signal analysis
    passes += printer
    passes += memAnalyzer       // Memory banking/buffering

    passes += printer
    // passes += areaAnalyzer
    // passes += contentionAnalyzer
    // passes += latencyAnalyzer

    // --- DSE
    if (spatialConfig.enableDSE) {
      passes += paramAnalyzer
      passes += heuristicAnalyzer
    }
    passes += dse               // Design space exploration
    passes += finalizer

    // --- Post-DSE Expansion
    // NOTE: Small compiler pass ordering issue here:
    // We may need bound information during node expansion,
    // but we also need to reanalyze bounds to account for expanded nodes
    // For now just doing it twice
    passes += scalarAnalyzer    // Bounds / global analysis
    passes += transferExpand    // Expand burst loads/stores from single abstract nodes
    passes += switchInsert      // Change if-then-else statements from transfers to switches
    passes += levelAnalyzer     // Pipe style annotation fixes after expansion
    passes += printer

    // --- Post-Expansion Cleanup
    passes += regReadCSE        // CSE register reads in inner pipelines
    passes += printer
    passes += scalarAnalyzer    // Bounds / global analysis
    passes += ctrlAnalyzer      // Control signal analysis

    passes += regCleanup        // Remove unused registers and corresponding reads/writes created in unit pipe transform
    passes += printer

    //passes += switchFlatten     // Switch inlining for simplification / optimization
    //passes += printer

    // --- Pre-Unrolling Analysis
    passes += ctrlAnalyzer      // Control signal analysis
    passes += affineAnalyzer    // Memory access patterns
    passes += reduceAnalyzer    // Reduce/accumulator specialization
    passes += memAnalyzer       // Finalize banking/buffering

    // TODO: Resurrect this for SRAM views
    /*if (spatialConfig.useAffine) {
      passes += sramTransform
      passes += printer
      passes += ctrlAnalyzer      // Control signal analysis
    }*/

    // --- Design Elaboration

    if (spatialConfig.enablePIRSim) passes += pirRetimer

    passes += printer
    passes += unroller          // Unrolling
    passes += printer
    passes += uctrlAnalyzer     // Readers/writers for CSE
    passes += regReadCSE        // CSE register reads in inner pipelines
    passes += printer
    passes += uctrlAnalyzer
    passes += rotateFixer       //

    passes += uctrlAnalyzer     // Analysis for unused register reads
    passes += regCleanup        // Duplicate register reads for each use
    passes += printer
    passes += rewriter          // Post-unrolling rewrites (e.g. enabled register writes)
    passes += switchOptimize
    passes += printer

    // --- Retiming
    if (spatialConfig.enableRetiming) {
      passes += retiming        // Add delay shift registers where necessary
      passes += printer
    }
    passes += initAnalyzer

    // --- Post-Unroll Analysis
    // passes += pipeMerger
    passes += uctrlAnalyzer     // Control signal analysis (post-unrolling)
    passes += printer
    passes += bufferAnalyzer    // Set top controllers for n-buffers
    passes += streamAnalyzer    // Set stream pipe children fifo dependencies
    passes += argMapper         // Get address offsets for each used DRAM object
    if (spatialConfig.enablePIRSim) passes += pirTiming // PIR delays (retiming control signals)
    passes += printer

    // --- Sanity Checks
    passes += sanityCheck        // Check that illegal host values are not used in the accel block
    passes += controlSanityCheck
    passes += finalizer         // Finalize any remaining parameters

    // --- Code generation
    if (spatialConfig.enableTree)  passes += treegen
    if (spatialConfig.enableSim)   passes += scalagen
    if (spatialConfig.enableSynth) passes += cppgen
    if (spatialConfig.enableSynth) passes += chiselgen
    if (spatialConfig.enableDot)   passes += dotgen
    if (spatialConfig.enablePIR)   passes += pirgen
    if (spatialConfig.enableInterpret)   passes += interpreter

  }

  val target: FPGATarget = targets.DefaultTarget

  override protected def onException(t: Throwable): Unit = {
    super.onException(t)
    Report.bug("If you'd like, you can submit this log and your code in a bug report at: ")
    Report.bug("  https://github.com/stanford-ppl/spatial-lang/issues")
    Report.bug("and we'll try to fix it as soon as we can.")
  }

  override def settings(): Unit = {
    if (spatialConfig.useBasicBlocks) {
      Report.warn("Setting compiler to use basic blocks. Code motion will be disabled.")
      IR.useBasicBlocks = true
    }
    // Get the target's area and latency models ready for use by any compiler pass
    target.areaModel.init()
    target.latencyModel.init()
  }

  override protected def createConfig(): Config = new SpatialConfig()
  override protected def parseArguments(config: Config, sargs: Array[String]): Unit  = {
    val spatialConfig = config.asInstanceOf[SpatialConfig]
    spatialConfig.target = this.target // Default target is the one specified by the App

    val parser = new SpatialArgParser(spatialConfig)
    parser.parse(sargs.toSeq)
  }
  
}
trait SpatialApp extends ArgonApp with SpatialCompiler {

  // Make the "true" entry point final
  final override def main(sargs: Array[String]): Unit = super.main(sargs)

}

