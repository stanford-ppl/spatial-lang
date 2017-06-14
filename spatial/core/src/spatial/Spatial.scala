package spatial

import argon.AppCore
import argon.core.{ArgonCore, State}
import argon.traversal.IRPrinter
import argon.util.Report
import spatial.dse._
import spatial.analysis._
import spatial.transform._
import spatial.codegen.pirgen._
import spatial.codegen.chiselgen.ChiselGenSpatial
import spatial.codegen.cppgen.CppGenSpatial
import spatial.codegen.dotgen.DotGenSpatial
import spatial.codegen.scalagen.ScalaGenSpatial
import spatial.targets.{DefaultTarget, FPGATarget, Targets}

trait SpatialDSL extends ArgonCore with SpatialLangExternal
object dsl extends SpatialDSL {
  type SpatialApp = spatial.SpatialApp
}

trait SpatialApp extends AppCore {

  // Traversal schedule
  override def createTraversalSchedule(state: State) = {
    lazy val printer = IRPrinter(state)

    // Traversals
    lazy val scalarAnalyzer = new ScalarAnalyzer { val IR = state }
    lazy val levelAnalyzer  = new PipeLevelAnalyzer { val IR = state }
    lazy val dimAnalyzer    = new DimensionAnalyzer { val IR = state }

    lazy val switchInsert   = new SwitchTransformer { val IR = state }
    lazy val unitPipeInsert = new UnitPipeTransformer { val IR = state }

    lazy val affineAnalyzer = new SpatialAccessAnalyzer { val IR = state }
    lazy val ctrlAnalyzer   = new ControlSignalAnalyzer { val IR = state }

    lazy val regCleanup     = new RegisterCleanup { val IR = state }
    lazy val regReadCSE     = new RegReadCSE { val IR = state }

    lazy val memAnalyzer    = new MemoryAnalyzer { val IR = state; def localMems = ctrlAnalyzer.localMems }
    lazy val paramAnalyzer  = new ParameterAnalyzer{val IR = state }

    lazy val scopeCheck     = new ScopeCheck { val IR = state }

    lazy val latencyAnalyzer = new LatencyAnalyzer { val IR = state }

    lazy val controlSanityCheck = new ControllerSanityCheck { val IR = state }

    lazy val retiming = new PipeRetimer { val IR = state }

    lazy val dse = new DSE {
      val IR = state
      def restricts  = paramAnalyzer.restrict
      def tileSizes  = paramAnalyzer.tileSizes
      def parFactors = paramAnalyzer.parFactors
      def localMems  = ctrlAnalyzer.localMems
      def metapipes  = ctrlAnalyzer.metapipes
      def top = ctrlAnalyzer.top.get
    }

    lazy val transferExpand = new TransferSpecialization { val IR = state }
    lazy val reduceAnalyzer = new ReductionAnalyzer { val IR = state }
    lazy val uctrlAnalyzer  = new UnrolledControlAnalyzer { val IR = state }
    lazy val rewriter       = new RewriteTransformer { val IR = state }
    lazy val unroller       = new UnrollingTransformer { val IR = state }
    lazy val bufferAnalyzer = new BufferAnalyzer { val IR = state; def localMems = uctrlAnalyzer.localMems }
    lazy val streamAnalyzer = new StreamAnalyzer {
      val IR = state ;
      def streamPipes = uctrlAnalyzer.streampipes
      def streamEnablers = uctrlAnalyzer.streamEnablers
      def streamHolders = uctrlAnalyzer.streamHolders
      def streamLoadCtrls = uctrlAnalyzer.streamLoadCtrls
      def streamParEnqs = uctrlAnalyzer.streamParEnqs
    }

    lazy val pirRetimer = new PIRHackyRetimer { val IR = state }
    lazy val pirTiming  = new PIRHackyLatencyAnalyzer { val IR = state }

    lazy val argMapper  = new ArgMappingAnalyzer {
      val IR = state
      def memStreams = uctrlAnalyzer.memStreams
      def argPorts = uctrlAnalyzer.argPorts
      def genericStreams = uctrlAnalyzer.genericStreams
    }

    lazy val scalagen = new ScalaGenSpatial { val IR = state; def localMems = uctrlAnalyzer.localMems }
    lazy val chiselgen = new ChiselGenSpatial { val IR = state }
    lazy val pirgen = new PIRGenSpatial { val IR = state }
    lazy val cppgen = new CppGenSpatial { val IR = state }
    lazy val treegen = new TreeGenSpatial { val IR = state }
    lazy val dotgen = new DotGenSpatial { val IR = state }

    passes += printer
    passes += scalarAnalyzer    // Perform bound and global analysis
    passes += scopeCheck        // Check that illegal host values are not used in the accel block
    passes += levelAnalyzer     // Initial pipe style annotation fixes
    passes += dimAnalyzer       // Correctness checks for onchip and offchip dimensions

    // --- Unit Pipe Insertion
    passes += printer
    passes += switchInsert      // Change nested if-then-else statements to Switch controllers
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

    passes += printer
    passes += regCleanup        // Remove unused registers and corresponding reads/writes created in unit pipe transform
    passes += printer

    //passes += switchFlatten     // Switch inlining for simplification / optimization
    //passes += printer

    // --- Pre-Unrolling Analysis
    passes += ctrlAnalyzer      // Control signal analysis
    passes += affineAnalyzer    // Memory access patterns
    passes += reduceAnalyzer    // Reduce/accumulator specialization
    passes += memAnalyzer       // Finalize banking/buffering

    // TODO: models go here
    passes += latencyAnalyzer

    // --- Design Elaboration

    if (SpatialConfig.enablePIRSim) passes += pirRetimer

    passes += printer
    passes += unroller          // Unrolling
    passes += printer
    passes += uctrlAnalyzer     // Readers/writers for CSE
    passes += printer
    passes += regReadCSE        // CSE register reads in inner pipelines
    passes += printer

    passes += uctrlAnalyzer     // Analysis for unused register reads
    passes += printer
    passes += regCleanup        // Duplicate register reads for each use
    passes += rewriter          // Post-unrolling rewrites (e.g. enabled register writes)
    passes += printer

    // --- Retiming
    if (SpatialConfig.enableRetiming)   passes += retiming // Add delay shift registers where necessary
    passes += printer

    // --- Post-Unroll Analysis
    passes += uctrlAnalyzer     // Control signal analysis (post-unrolling)
    passes += printer
    passes += bufferAnalyzer    // Set top controllers for n-buffers
    passes += streamAnalyzer    // Set stream pipe children fifo dependencies
    passes += argMapper         // Get address offsets for each used DRAM object
    passes += latencyAnalyzer   // Get delay lengths of inner pipes (used for retiming control signals)
    if (SpatialConfig.enablePIRSim) passes += pirTiming // PIR delays (retiming control signals)
    passes += printer

    // --- Sanity Checks
    passes += scopeCheck        // Check that illegal host values are not used in the accel block
    passes += controlSanityCheck

    // --- Code generation
    if (SpatialConfig.enableTree)  passes += treegen
    if (SpatialConfig.enableSim)   passes += scalagen
    if (SpatialConfig.enableSynth) passes += cppgen
    if (SpatialConfig.enableSynth) passes += chiselgen
    if (SpatialConfig.enableDot)   passes += dotgen
    if (SpatialConfig.enablePIR)   passes += pirgen
  }

  def target = SpatialConfig.target
  def target_=(t: FPGATarget): Unit = { SpatialConfig.target = t }

  override protected def onException(t: Throwable): Unit = {
    super.onException(t)
    Report.error("If you'd like, you can submit this log and your code in a bug report at: ")
    Report.error("  https://github.com/stanford-ppl/spatial-lang/issues")
    Report.error("and we'll try to fix it as soon as we can.")
  }

  // Make the "true" entry point final
  final override def main(sargs: Array[String]): Unit = super.main(sargs)

  override protected def parseArguments(args: Seq[String]): Unit = {
    SpatialConfig.init()
    val parser = new SpatialArgParser
    parser.parse(args)
    if (SpatialConfig.targetName != "Default") {
      SpatialConfig.target = Targets.targets.find(_.name == SpatialConfig.targetName).getOrElse{
        Report.warn(s"Could not find target with name ${SpatialConfig.targetName}.")
        DefaultTarget
      }
    }
  }
}

