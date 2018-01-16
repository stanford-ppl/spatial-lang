package spatial.codegen.chiselgen

import scala.math._
import argon.core._
import argon.codegen.chiselgen.ChiselCodegen
import argon.nodes._
import spatial.targets.DE1._
import spatial.aliases._
import spatial.banking._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

sealed trait RemapSignal
// "Standard" Signals
object En extends RemapSignal
object Done extends RemapSignal
object BaseEn extends RemapSignal
object Mask extends RemapSignal
object Resetter extends RemapSignal
object DatapathEn extends RemapSignal
object CtrTrivial extends RemapSignal
// A few non-canonical signals
object IIDone extends RemapSignal
object RstEn extends RemapSignal
object CtrEn extends RemapSignal
object Ready extends RemapSignal
object Valid extends RemapSignal
object NowValid extends RemapSignal
object Inhibitor extends RemapSignal
object Wren extends RemapSignal
object Chain extends RemapSignal
object Blank extends RemapSignal
object DataOptions extends RemapSignal
object ValidOptions extends RemapSignal
object ReadyOptions extends RemapSignal
object EnOptions extends RemapSignal
object RVec extends RemapSignal
object WVec extends RemapSignal
object Retime extends RemapSignal
object SM extends RemapSignal
object Inhibit extends RemapSignal

sealed trait AppProperties
object HasLineBuffer extends AppProperties
object HasNBufSRAM extends AppProperties
object HasNBufRegFile extends AppProperties
object HasGeneralFifo extends AppProperties
object HasTileStore extends AppProperties
object HasTileLoad extends AppProperties
object HasGather extends AppProperties
object HasScatter extends AppProperties
object HasLUT extends AppProperties
object HasBreakpoint extends AppProperties
object HasAlignedLoad extends AppProperties
object HasAlignedStore extends AppProperties
object HasUnalignedLoad extends AppProperties
object HasUnalignedStore extends AppProperties
object HasStaticCtr extends AppProperties
object HasVariableCtrBounds extends AppProperties
object HasVariableCtrStride extends AppProperties
object HasFloats extends AppProperties


trait ChiselGenSRAM extends ChiselCodegen {
  private var nbufs: List[Sym[SRAM[_]]] = Nil

  var itersMap = new scala.collection.mutable.HashMap[Bound[_], List[Exp[_]]]
  var cchainPassMap = new scala.collection.mutable.HashMap[Exp[_], Exp[_]] // Map from a cchain to its ctrl node, for computing suffix on a cchain before we enter the ctrler
  var validPassMap = new scala.collection.mutable.HashMap[(Exp[_], String), Seq[Exp[_]]] // Map from a valid bound sym to its ctrl node, for computing suffix on a valid before we enter the ctrler
  var accumsWithIIDlay = new scala.collection.mutable.ListBuffer[Exp[_]]
  var widthStats = new scala.collection.mutable.ListBuffer[Int]
  var depthStats = new scala.collection.mutable.ListBuffer[Int]
  var appPropertyStats = Set[AppProperties]()

  // Helper for getting the BigDecimals inside of Const exps for things like dims, when we know that we need the numbers quoted and not chisel types
  protected def getConstValues(all: Seq[Exp[_]]): Seq[Any] = all.map{i => getConstValue(i) }
  protected def getConstValue(one: Exp[_]): Any = one match {case Const(c) => c }

  // TODO: Should this be deprecated?
  protected def enableRetimeMatch(en: Exp[_], lhs: Exp[_]): Double = { // With partial retiming, the delay on standard signals needs to match the delay of the enabling input, not necessarily the symDelay(lhs) if en is delayed partially
    val last_def_delay = en match {
      case Def(And(_,_)) => latencyOption("And", None)
      case Def(Or(_,_)) => latencyOption("Or", None)
      case Def(Not(_)) => latencyOption("Not", None)
      case Const(_) => 0.0
      case Def(DelayLine(size,_)) => size.toDouble // Undo subtraction
      case Def(RegRead(_)) => latencyOption("RegRead", None)
      case Def(FixEql(a,_)) => latencyOption("FixEql", Some(bitWidth(a.tp)))
      case b: Bound[_] => 0.0
      case _ => throw new Exception(s"Node enable $en not yet handled in partial retiming")
    }
    // if (spatialConfig.enableRetiming) symDelay(en) + last_def_delay else 0.0
    if (spatialConfig.enableRetiming) symDelay(lhs) else 0.0
  }

  protected def computeSuffix(s: Bound[_]): String = {
    var result = if (config.enableNaming) super.quote(s) else wireMap(super.quote(s)) // TODO: Playing with fire here.  Probably just make the quote and name of bound in Codegen.scala do the wireMap themselves instead of doing it here!
    if (itersMap.contains(s)) {
      val siblings = itersMap(s)
      var nextLevel: Option[Exp[_]] = Some(controllerStack.head)
      while (nextLevel.isDefined) {
        if (siblings.contains(nextLevel.get)) {
          if (siblings.indexOf(nextLevel.get) > 0) {result = result + s"_chain_read_${siblings.indexOf(nextLevel.get)}"}
          nextLevel = None
        } else {
          nextLevel = parentOf(nextLevel.get)
        }
      }
    } 
    result
  }

  def latencyOption(op: String, b: Option[Int]): Double = {
    if (spatialConfig.enableRetiming) {
      if (b.isDefined) {spatialConfig.target.latencyModel.model(op)("b" -> b.get)("LatencyOf")}
      else spatialConfig.target.latencyModel.model(op)()("LatencyOf") 
    } else {
      0.0
    }
  }
  def latencyOptionString(op: String, b: Option[Int]): String = {
    if (spatialConfig.enableRetiming) {
      val latency = latencyOption(op, b)
      if (b.isDefined) {
        s"""Some(${latency})"""
      } else {
        s"""Some(${latency})"""
      }
    } else {
      "None"      
    }
  }

  protected def isStreamChild(lhs: Exp[_]): Boolean = {
    var nextLevel: Option[Exp[_]] = Some(lhs)
    var result = false
    while (nextLevel.isDefined) {
      if (styleOf(nextLevel.get) == StreamPipe) {
        result = true
        nextLevel = None
      } else {
        nextLevel = parentOf(nextLevel.get)
      }
    }
    result
  }

  // Method for deciding if we should use the always-enabled delay line or the stream delay line (DS)
  def DL[T](name: String, latency: T, isBit: Boolean = false): String = {
    val streamOuts = if (!controllerStack.isEmpty) {
      pushesTo(controllerStack.head).distinct.map{ pt => pt.memory match {
        case fifo @ Def(StreamOutNew(bus)) => src"${swap(fifo, Ready)}"
        case _ => ""
      }}.filter(_ != "").mkString(" & ")
    } else { "" }

    latency match {
      case lat: Int => 
        if (!controllerStack.isEmpty) {
          if (isStreamChild(controllerStack.head) & streamOuts != "") {
            if (isBit) src"(${name}).DS(${latency}.toInt, rr, ${streamOuts})"
            else src"Utils.getRetimedStream($name, $latency, ${streamOuts})"
          } else {
            if (isBit) src"(${name}).D(${latency}.toInt, rr)"
            else src"Utils.getRetimed($name, $latency)"          
          }
        } else {
          if (isBit) src"(${name}).D(${latency}.toInt, rr)"
          else src"Utils.getRetimed($name, $latency)"                    
        }
      case lat: Double => 
        if (!controllerStack.isEmpty) {
          if (isStreamChild(controllerStack.head) & streamOuts != "") {
            if (isBit) src"(${name}).DS(${latency}.toInt, rr, ${streamOuts})"
            else src"Utils.getRetimedStream($name, $latency, ${streamOuts})"
          } else {
            if (isBit) src"(${name}).D(${latency}.toInt, rr)"
            else src"Utils.getRetimed($name, $latency)"
          }
        } else {
          if (isBit) src"(${name}).D(${latency}.toInt, rr)"
          else src"Utils.getRetimed($name, $latency)"
        }
      case lat: String => 
        if (!controllerStack.isEmpty) {
          if (isStreamChild(controllerStack.head) & streamOuts != "") {
            if (isBit) src"(${name}).DS(${latency}.toInt, rr, ${streamOuts})"
            else src"Utils.getRetimedStream($name, $latency, ${streamOuts})"
          } else {
            if (isBit) src"(${name}).D(${latency}.toInt, rr)"
            else src"Utils.getRetimed($name, $latency)"
          }
        } else {
          if (isBit) src"(${name}).D(${latency}.toInt, rr)"
          else src"Utils.getRetimed($name, $latency)"
        }
    }
  }

  // Method for deciding if we should use the always-enabled delay line or the stream delay line (DS), specifically for signals like inhibitor resets that must acknowledeg a done signal that can strobe while stalled
  def DLI[T](name: String, latency: T, isBit: Boolean = false): String = {
    val streamOuts = if (!controllerStack.isEmpty) {
      pushesTo(controllerStack.head).distinct.map{ pt => pt.memory match {
        case fifo @ Def(StreamOutNew(bus)) => src"${swap(fifo, Ready)}.D(${latency}, rr)"
        case _ => ""
      }}.filter(_ != "").mkString(" & ")
    } else { "" }

    latency match {
      case lat: Int => 
        if (!controllerStack.isEmpty) {
          if (isStreamChild(controllerStack.head) & streamOuts != "") {
            if (isBit) src"(${name}).DS(${latency}.toInt, rr, ${streamOuts})"
            else src"Utils.getRetimedStream($name, $latency, ${streamOuts})"
          } else {
            if (isBit) src"(${name}).D(${latency}.toInt, rr)"
            else src"Utils.getRetimed($name, $latency)"          
          }
        } else {
          if (isBit) src"(${name}).D(${latency}.toInt, rr)"
          else src"Utils.getRetimed($name, $latency)"                    
        }
      case lat: Double => 
        if (!controllerStack.isEmpty) {
          if (isStreamChild(controllerStack.head) & streamOuts != "") {
            if (isBit) src"(${name}).DS(${latency}.toInt, rr, ${streamOuts})"
            else src"Utils.getRetimedStream($name, $latency, ${streamOuts})"
          } else {
            if (isBit) src"(${name}).D(${latency}.toInt, rr)"
            else src"Utils.getRetimed($name, $latency)"
          }
        } else {
          if (isBit) src"(${name}).D(${latency}.toInt, rr)"
          else src"Utils.getRetimed($name, $latency)"
        }
      case lat: String => 
        if (!controllerStack.isEmpty) {
          if (isStreamChild(controllerStack.head) & streamOuts != "") {
            if (isBit) src"(${name}).DS(${latency}.toInt, rr, ${streamOuts})"
            else src"Utils.getRetimedStream($name, $latency, ${streamOuts})"
          } else {
            if (isBit) src"(${name}).D(${latency}.toInt, rr)"
            else src"Utils.getRetimed($name, $latency)"
          }
        } else {
          if (isBit) src"(${name}).D(${latency}.toInt, rr)"
          else src"Utils.getRetimed($name, $latency)"
        }
    }
  }

  def swap(lhs: Exp[_], s: RemapSignal): String = {
    s match {
      case En => wireMap(src"${lhs}_en")
      case Done => wireMap(src"${lhs}_done")
      case BaseEn => wireMap(src"${lhs}_base_en")
      case Mask => wireMap(src"${lhs}_mask")
      case Resetter => wireMap(src"${lhs}_resetter")
      case DatapathEn => wireMap(src"${lhs}_datapath_en")
      case CtrTrivial => wireMap(src"${lhs}_ctr_trivial")
      case IIDone => wireMap(src"${lhs}_II_done")
      case RstEn => wireMap(src"${lhs}_rst_en")
      case CtrEn => wireMap(src"${lhs}_ctr_en")
      case Ready => wireMap(src"${lhs}_ready")
      case Valid => wireMap(src"${lhs}_valid")
      case NowValid => wireMap(src"${lhs}_now_valid")
      case Inhibitor => wireMap(src"${lhs}_inhibitor")
      case Wren => wireMap(src"${lhs}_wren")
      case Chain => wireMap(src"${lhs}_chain")
      case Blank => wireMap(src"${lhs}")
      case DataOptions => wireMap(src"${lhs}_data_options")
      case ValidOptions => wireMap(src"${lhs}_valid_options")
      case ReadyOptions => wireMap(src"${lhs}_ready_options")
      case EnOptions => wireMap(src"${lhs}_en_options")
      case RVec => wireMap(src"${lhs}_rVec")
      case WVec => wireMap(src"${lhs}_wVec")
      case Retime => wireMap(src"${lhs}_retime")
      case SM => wireMap(src"${lhs}_sm")
      case Inhibit => wireMap(src"${lhs}_inhibit")
    }
  }

  def swap(lhs: => String, s: RemapSignal): String = {
    s match {
      case En => wireMap(src"${lhs}_en")
      case Done => wireMap(src"${lhs}_done")
      case BaseEn => wireMap(src"${lhs}_base_en")
      case Mask => wireMap(src"${lhs}_mask")
      case Resetter => wireMap(src"${lhs}_resetter")
      case DatapathEn => wireMap(src"${lhs}_datapath_en")
      case CtrTrivial => wireMap(src"${lhs}_ctr_trivial")
      case IIDone => wireMap(src"${lhs}_II_done")
      case RstEn => wireMap(src"${lhs}_rst_en")
      case CtrEn => wireMap(src"${lhs}_ctr_en")
      case Ready => wireMap(src"${lhs}_ready")
      case Valid => wireMap(src"${lhs}_valid")
      case NowValid => wireMap(src"${lhs}_now_valid")
      case Inhibitor => wireMap(src"${lhs}_inhibitor")
      case Wren => wireMap(src"${lhs}_wren")
      case Chain => wireMap(src"${lhs}_chain")
      case Blank => wireMap(src"${lhs}")
      case DataOptions => wireMap(src"${lhs}_data_options")
      case ValidOptions => wireMap(src"${lhs}_valid_options")
      case ReadyOptions => wireMap(src"${lhs}_ready_options")
      case EnOptions => wireMap(src"${lhs}_en_options")
      case RVec => wireMap(src"${lhs}_rVec")
      case WVec => wireMap(src"${lhs}_wVec")
      case Retime => wireMap(src"${lhs}_retime")
      case SM => wireMap(src"${lhs}_sm")
      case Inhibit => wireMap(src"${lhs}_inhibit")
    }
  }

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  //def cchainWidth(ctr: Exp[Counter]): Int = {
    //ctr match {
      //case Def(CounterNew(Exact(s), Exact(e), _, _)) => 
        //val sbits = if (s > 0) {BigInt(1) max ceil(scala.math.log((BigInt(1) max s).toDouble)/scala.math.log(2)).toInt} 
                    //else {BigInt(1) max ceil(scala.math.log((BigInt(1) max (s.abs+BigInt(1))).toDouble)/scala.math.log(2)).toInt}
        //val ebits = if (e > 0) {BigInt(1) max ceil(scala.math.log((BigInt(1) max e).toDouble)/scala.math.log(2)).toInt} 
                    //else {BigInt(1) max ceil(scala.math.log((BigInt(1) max (e.abs+BigInt(1))).toDouble)/scala.math.log(2)).toInt}
        //({ebits max sbits} + 2).toInt
      //case Def(CounterNew(start, stop, _, _)) => 
        //val sbits = bitWidth(start.tp)
        //val ebits = bitWidth(stop.tp)
        //({ebits max sbits} + 0).toInt
      //case _ => 32
    //}
  //}

  def isSpecializedReduce(accum: Exp[_]): Boolean = {
    reduceType(accum) match {
      case Some(fps: ReduceFunction) => // is an accumulator
        fps match {
          case FixPtSum => true
          case _ => false
        }
      case _ => false
    }
  }
  def cchainWidth(ctr: Exp[Counter]): Int = ctr match {
    case Def(CounterNew(Exact(s), Exact(e), _, _)) =>
      val sbits = if (s > 0) {BigInt(2) + ceil(scala.math.log((BigInt(1) max s).toDouble)/scala.math.log(2)).toInt}
                  else {BigInt(2) + ceil(scala.math.log((BigInt(1) max (s.abs+BigInt(1))).toDouble)/scala.math.log(2)).toInt}
      val ebits = if (e > 0) {BigInt(2) + ceil(scala.math.log((BigInt(1) max e).toDouble)/scala.math.log(2)).toInt}
                  else {BigInt(2) + ceil(scala.math.log((BigInt(1) max (e.abs+BigInt(1))).toDouble)/scala.math.log(2)).toInt}
      ({ebits max sbits} + 2).toInt
    case Def(CounterNew(start, stop, _, _)) =>
      val sbits = bitWidth(start.tp)
      val ebits = bitWidth(stop.tp)
      ({ebits max sbits} + 0).toInt
    case _ => 32
  }

  def getWriteAddition(c: Exp[Any]): String = {
    // If we are inside a stream pipe, the following may be set
    // Add 1 to latency of fifo checks because SM takes one cycle to get into the done state
    val lat = bodyLatency.sum(c)
    val readiers = listensTo(c).distinct.map{_.memory}.map {
      case fifo @ Def(StreamInNew(bus)) => src"${swap(fifo, Valid)}"
      case _ => ""
    }.filter(_ != "").mkString(" & ")

    val hasReadiers = if (readiers != "") "&" else ""

    src" ${hasReadiers} ${readiers}"
  }

  def getNowValidLogic(c: Exp[Any]): String = { // Because of retiming, the _ready for streamins and _valid for streamins needs to get factored into datapath_en
      // If we are inside a stream pipe, the following may be set
      val readiers = listensTo(c).distinct.map{_.memory}.map {
        case fifo @ Def(StreamInNew(bus)) => src"${swap(fifo, NowValid)}" //& ${fifo}_ready"
        case _ => ""
      }.mkString(" & ")
      val hasReadiers = if (readiers != "") "&" else ""
      if (spatialConfig.enableRetiming) src"${hasReadiers} ${readiers}" else " "
  }
  def getReadyLogic(c: Exp[Any]): String = { // Because of retiming, the _ready for streamins and _valid for streamins needs to get factored into datapath_en
      // If we are inside a stream pipe, the following may be set
      val readiers = listensTo(c).distinct.map{_.memory}.map {
        case fifo @ Def(StreamInNew(bus)) => src"${swap(fifo, Ready)}"
        case _ => ""
      }.mkString(" & ")
      val hasReadiers = if (readiers != "") "&" else ""
      if (spatialConfig.enableRetiming) src"${hasReadiers} ${readiers}" else " "
  }


  protected def bufferControlInfo(mem: Exp[_]): List[(Exp[_], String)] = {
    val readers = readersOf(mem)
    val writers = writersOf(mem)
    val readPorts = readers.groupBy{a => portsOf(a, mem, 0) }.toList
    val writePorts = writers.groupBy{a => portsOf(a, mem, 0) }.toList
    // Console.println(s"working on $mem $i $readers $readPorts $writers $writePorts")
    // Console.println(s"${readPorts.map{case (_, readers) => readers}}")
    // Console.println(s"innermost ${readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node}")
    // Console.println(s"middle ${parentOf(readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get}")
    // Console.println(s"outermost ${childrenOf(parentOf(readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get)}")
    var specialLB = false
    val readCtrls = readPorts.map{case (port, reads) =>
      val readTops = reads.flatMap{a => topControllerOf(a, mem) }
      mem match {
        case Def(_:LineBufferNew[_]) => // Allow empty lca, meaning we use a sequential pipe for rotations
          if (readTops.nonEmpty) {
            readTops.head.node
          } else {
            warn(mem.ctx, u"Memory $mem, port $port had no read top controllers.  Consider wrapping this linebuffer in a metapipe to get better speedup")
            warn(mem.ctx)

            specialLB = true
            // readTops.headOption.getOrElse{throw new Exception(u"Memory $mem, instance $i, port $port had no read top controllers") }    
            reads.head.node
          }
        case _ =>
          readTops.headOption.getOrElse{throw new Exception(u"Memory $mem, port $port had no read top controllers") }.node
      }
      
    }
    if (readCtrls.isEmpty) throw new Exception(u"Memory $mem had no readers?")

    // childrenOf(parentOf(readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get)

    if (!specialLB) {
      val allSiblings = childrenOf(parentOf(readCtrls.head).get)
      val readSiblings = readPorts.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem) }}.filter(_.nonEmpty).map{_.head.node}
      val writeSiblings = writePorts.map{case (_,w) => w.flatMap{ a => topControllerOf(a, mem) }}.filter(_.nonEmpty).map{_.head.node}
      val writePortsNumbers = writeSiblings.map{ sw => allSiblings.indexOf(sw) }
      val readPortsNumbers = readSiblings.map{ sr => allSiblings.indexOf(sr) }
      val firstActivePort = math.min( readPortsNumbers.min, writePortsNumbers.min )
      val lastActivePort = math.max( readPortsNumbers.max, writePortsNumbers.max )
      val numStagesInbetween = lastActivePort - firstActivePort

      val info = (0 to numStagesInbetween).map { port =>
        val ctrlId = port + firstActivePort
        val node = allSiblings(ctrlId)
        val rd = if (readPortsNumbers.contains(ctrlId)) {"read"} else {
          // emit(src"""${mem}_${i}.readTieDown(${port})""")
          ""
        }
        val wr = if (writePortsNumbers.contains(ctrlId)) {"write"} else {""}
        val empty = if (rd == "" & wr == "") "empty" else ""
        (node, src"/*$rd $wr $empty*/")
      }
      info.toList
    } else {
      // Assume write comes before read and there is only one write
      val writer = writers.head.ctrl._1
      val reader = readers.head.ctrl._1
      val lca = leastCommonAncestorWithPaths[Exp[_]](reader, writer, {node => parentOf(node)})._1.get
      val allSiblings = childrenOf(lca)
      var writeSibling: Option[Exp[Any]] = None
      var candidate = writer
      while (writeSibling.isEmpty) {
        if (allSiblings.contains(candidate)) {
          writeSibling = Some(candidate)
        } else {
          candidate = parentOf(candidate).get
        }
      }
      // Get LCA of read and write
      List((writeSibling.get, src"/*seq write*/"))
    }

  }

  // Emit an SRFF that will block a counter from incrementing after the counter reaches the max
  //  rather than spinning even when there is retiming and the surrounding loop has a delayed
  //  view of the counter done signal
  protected def emitInhibitor(lhs: Exp[_], cchain: Option[Exp[_]], fsm: Option[Exp[_]] = None, switch: Option[Exp[_]]): Unit = {
    if (spatialConfig.enableRetiming || spatialConfig.enablePIRSim) {
      emitGlobalWireMap(src"${lhs}_inhibitor", "Wire(Bool())") // Used to be global module?
      if (fsm.isDefined) {
          emitGlobalModuleMap(src"${lhs}_inhibit", "Module(new SRFF())")
          emit(src"${swap(lhs, Inhibit)}.io.input.set := Utils.risingEdge(~${fsm.get})")  
          emit(src"${swap(lhs, Inhibit)}.io.input.reset := ${DLI(swap(lhs, Done), src"1 + ${swap(lhs, Retime)}", true)}")
          /* or'ed  back in because of BasicCondFSM!! */
          emit(src"${swap(lhs, Inhibitor)} := ${swap(lhs, Inhibit)}.io.output.data /*| ${fsm.get}*/ // Really want inhibit to turn on at last enabled cycle")        
          emit(src"${swap(lhs, Inhibit)}.io.input.asyn_reset := reset")
      } else if (switch.isDefined) {
        emit(src"${swap(lhs, Inhibitor)} := ${swap(switch.get, Inhibitor)}")
      } else {
        if (cchain.isDefined) {
          emitGlobalModuleMap(src"${lhs}_inhibit", "Module(new SRFF())")
          emit(src"${swap(lhs, Inhibit)}.io.input.set := ${cchain.get}.io.output.done")  
          emit(src"${swap(lhs, Inhibitor)} := ${swap(lhs, Inhibit)}.io.output.data /*| ${cchain.get}.io.output.done*/ // Correction not needed because _done should mask dp anyway")
          emit(src"${swap(lhs, Inhibit)}.io.input.reset := ${swap(lhs, Done)}")
          emit(src"${swap(lhs, Inhibit)}.io.input.asyn_reset := reset")
        } else {
          emitGlobalModuleMap(src"${lhs}_inhibit", "Module(new SRFF())")
          emit(src"${swap(lhs, Inhibit)}.io.input.set := Utils.risingEdge(${swap(lhs, Done)} /*${lhs}_sm.io.output.ctr_inc*/)")
          val rster = if (levelOf(lhs) == InnerControl & listensTo(lhs).distinct.length > 0) {src"${DLI(src"Utils.risingEdge(${swap(lhs, Done)})", src"1 + ${swap(lhs, Retime)}", true)} // Ugly hack, do not try at home"} else src"${DLI(swap(lhs, Done), 1, true)}"
          emit(src"${swap(lhs, Inhibit)}.io.input.reset := $rster")
          emit(src"${swap(lhs, Inhibitor)} := ${swap(lhs, Inhibit)}.io.output.data")
          emit(src"${swap(lhs, Inhibit)}.io.input.asyn_reset := reset")
        }        
      }
    } else {
      emitGlobalWireMap(src"${lhs}_inhibitor", "Wire(Bool())");emit(src"${swap(lhs, Inhibitor)} := false.B // Maybe connect to ${swap(lhs, Done)}?  ")
    }
  }

  def logRetime(lhs: String, data: String, delay: Int, isVec: Boolean = false, vecWidth: Int = 1, wire: String = "", isBool: Boolean = false): Unit = {
    if (delay > maxretime) maxretime = delay
    if (isVec) {
      emitGlobalWireMap(src"$lhs", src"Wire(${wire})")
      emit(src"(0 until ${vecWidth}).foreach{i => ${lhs}(i).r := ${DL(src"${data}(i).r", delay)}}")
    } else {
      if (isBool) {
        emitGlobalWireMap(src"""$lhs""", src"""Wire(Bool())""");emit(src"""${lhs} := ${DL(data, delay, true)}""")
      } else {
        emitGlobalWireMap(src"""$lhs""", src"""Wire(${wire})""");emit(src"""${lhs}.r := ${DL(src"${data}.r", delay)}""")
      }
    }
  }

  def logRetime(lhs: => Sym[_], data: String, delay: Int, isVec: Boolean, vecWidth: Int, wire: String, isBool: Boolean): Unit = {
    if (delay > maxretime) maxretime = delay
    if (isVec) {
      emitGlobalWireMap(src"$lhs", src"Wire(${wire})")
      emit(src"(0 until ${vecWidth}).foreach{i => ${lhs}(i).r := ${DL(src"${data}(i).r", delay)}}")
    } else {
      if (isBool) {
        emitGlobalWireMap(src"""$lhs""", src"""Wire(Bool())""");emit(src"""${lhs} := ${DL(data, delay, true)}""")
      } else {
        emitGlobalWireMap(src"""$lhs""", src"""Wire(${wire})""");emit(src"""${lhs}.r := ${DL(src"${data}.r", delay)}""")
      }
    }
  }

  protected def newWire(tp: Type[_]): String = tp match {
    case FixPtType(s,d,f) => src"new FixedPoint($s, $d, $f)"
    case IntType() => "UInt(32.W)"
    case LongType() => "UInt(32.W)"
    case FltPtType(g,e) => src"new FloatingPoint($e, $g)"
    case BooleanType => "Bool()"
    case tp: VectorType[_] => src"Vec(${tp.width}, ${newWire(tp.typeArguments.head)})"
    case tp: StructType[_] => src"UInt(${bitWidth(tp)}.W)"
    // case tp: IssuedCmd => src"UInt(${bitWidth(tp)}.W)"
    case tp: ArrayType[_] => src"Wire(Vec(999, ${newWire(tp.typeArguments.head)}"
    case _ => throw new argon.NoWireConstructorException(s"$tp")
  }
  override protected def spatialNeedsFPType(tp: Type[_]): Boolean = tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
    case FixPtType(s,d,f) => if (s) true else if (f == 0) false else true
    case IntType()  => false
    case LongType() => false
    case FloatType() => true
    case DoubleType() => true
    case _ => super.needsFPType(tp)
  }

  override protected def name(s: Dyn[_]): String = s match {
    case Def(SRAMNew(_)) => s"""${s}_${s.name.getOrElse("sram").replace("$","")}"""
    case _ => super.name(s)
  }

  override protected def quote(e: Exp[_]): String = e match {
    // FIXME: Unclear precedence with the quote rule for Bound in ChiselGenCounter
    case b: Bound[_] => 
      swap(computeSuffix(b), Blank)
    case _ => super.quote(e)
  } 

  def emitBankedLoad(lhs: Exp[_], mem: Exp[_], bank: Seq[Seq[Exp[Index]]], ofs: Seq[Exp[Index]], ens: Seq[Exp[Bit]])(tp: Type[_]): Unit = {
    val rPar = ens.length
    val width = bitWidth(tp)
    val parent = parentOf(lhs).get //readersOf(mem).find{_.node == lhs}.get.ctrlNode
    val invisibleEnable = src"""${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}"""
    emit(s"""// Assemble R_Info vector""")
    emitGlobalWireMap(src"""${lhs}_rVec""", s"Wire(Vec(${rPar}, new R_Info(32, ${List.fill(bank.length)(32)})))")
    ofs.zipWithIndex.foreach{case (o,i) => 
      emit(src"""${swap(lhs, RVec)}($i).en := ${DL(invisibleEnable, enableRetimeMatch(ens(i), lhs), true)} & ${ens(i)}""")
      emit(src"""${swap(lhs, RVec)}($i).ofs := ${o}.r""")
      bank(i).zipWithIndex.foreach{case (b,j) => 
        emit(src"""${swap(lhs, RVec)}($i).banks($j) := ${b}.r""")
      }
    }
    val p = portsOf(lhs, mem).head
    val lhs_name = src"${lhs}_idx"
    emit(src"""val ${lhs_name} = ${mem}.connectRPort(Vec(${swap(lhs, RVec)}.toArray), ${p._1})""") // TODO: ._1 the correct field?
    emitGlobalWireMap(src"""${lhs}""", src"""Wire(${newWire(lhs.tp)})""") 
    emit(src"""(0 until ${ens.length}).foreach{case i => ${lhs}(i).r := ${mem}.io.output.data(${lhs_name} + i)}""")
  }

  def emitBankedStore[T:Type](lhs: Exp[_], mem: Exp[_], data: Seq[Exp[T]], bank: Seq[Seq[Exp[Index]]], ofs: Seq[Exp[Index]], ens: Seq[Exp[Bit]]): Unit = {
    val wPar = ens.length
    val width = bitWidth(mem.tp.typeArguments.head)
    val parent = parentOf(lhs).get
    val invisibleEnable = src"""${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}"""
    emit(s"""// Assemble W_Info vector""")
    emitGlobalWireMap(src"""${lhs}_wVec""", s"Wire(Vec(${wPar}, new W_Info(32, ${List.fill(bank.length)(32)}, $width)))")
    ofs.zipWithIndex.foreach{case (o,i) => 
      emit(src"""${swap(lhs, WVec)}($i).en := ${DL(invisibleEnable, enableRetimeMatch(ens(i), lhs), true)} & ${ens(i)}""")
      emit(src"""${swap(lhs, WVec)}($i).ofs := ${o}.r""")
      emit(src"""${swap(lhs, WVec)}($i).data := ${data(i)}.r""")
      bank(i).zipWithIndex.foreach{case (b,j) => 
        emit(src"""${swap(lhs, WVec)}($i).banks($j) := ${b}.r""")
      }
    }
    emit(src"""${mem}.connectWPort(${swap(lhs, WVec)}, ${portsOf(lhs, mem).head._2.mkString("List(", ",", ")")})""")
  }

  def emitBankedInitMem(mem: Exp[_], init: Option[Seq[Exp[_]]])(tp: Type[_]): Unit = {
    val inst = instanceOf(mem)
    val dims = constDimsOf(mem)
    implicit val ctx: SrcCtx = mem.ctx

    val templateName = mem match {
      case Def(op: SRAMNew[_,_]) => "SRAM"
    }
    // val data = init match {
    //   case Some(elems) =>
    //     // val nBanks = inst.nBanks.product
    //     // val bankDepth = Math.ceil(dims.product.toDouble / nBanks).toInt
    //     // src"""Array[Array[$tp]](${banks.mkString("\n")})"""
    //   case None =>
    //     // val banks = inst.totalBanks
    //     // val bankDepth = Math.ceil(dims.product.toDouble / banks).toInt
    //     // src"""Array.fill($banks){ Array.fill($bankDepth)(${invalid(tp)}) }"""
    // }

    val dimensions = dims.map(_.toString).mkString("List(", ",", ")")
    val numBanks = inst.nBanks.map(_.toString).mkString("List(", ",", ")")
    val strides = numBanks // TODO: What to do with strides
    val wPar = writersOf(mem).map {w => w.node match { case Def(BankedSRAMStore(_,_,_,_,ens)) => ens.length; case _ => -1 }}.mkString("List(", ",", ")")
    val rPar = readersOf(mem).map {r => r.node match { case Def(BankedSRAMLoad(_,_,_,ens)) => ens.length; case _ => -1 }}.mkString("List(", ",", ")")
    val bankingMode = "BankedMemory" // TODO: Find correct one
    val wPort = writersOf(mem).map {w => portsOf(w,mem).toList.head}

    emitGlobalModule(src"""val $mem = Module(new $templateName($dimensions, ${bitWidth(tp)}, $numBanks, $strides, $wPar, $rPar, $bankingMode, ${spatialConfig.enableSyncMem}))""")
  }


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op: SRAMNew[_,_] => emitBankedInitMem(lhs, initialDataOf.get(lhs))(mtyp(op.mT))

//     case op@SRAMNew(_) =>
//       val dimensions = constDimsOf(lhs)
//       val mem = instanceOf(lhs)
//       mem match {
//         case ModBanking
//       }
//       val rParZip = readersOf(lhs)
//         .filter{read => dispatchOf(read, lhs) contains i}
//         .map { r => 
//           val par = r.node match {
//             case Def(_: SRAMLoad[_]) => 1
//             case Def(a@ParSRAMLoad(_,inds,ens)) => inds.length
//           }
//           val port = portsOf(r, lhs, i).toList.head
//           (par, port)
//         }
//       val rPar = if (rParZip.length == 0) "1" else rParZip.map{_._1}.mkString(",")
//       val rBundling = if (rParZip.length == 0) "0" else rParZip.map{_._2}.mkString(",")
//       val wParZip = writersOf(lhs)
//         .filter{write => dispatchOf(write, lhs) contains i}
//         .filter{w => portsOf(w, lhs, i).toList.length == 1}
//         .map { w => 
//           val par = w.node match {
//             case Def(_: SRAMStore[_]) => 1
//             case Def(a@ParSRAMStore(_,_,_,ens)) => ens match {
//               case Op(ListVector(elems)) => elems.length // Was this deprecated?
//               case _ => ens.length
//             }
//           }
//           val port = portsOf(w, lhs, i).toList.head
//           (par, port)
//         }
//       val wPar = if (wParZip.length == 0) "1" else wParZip.map{_._1}.mkString(",")
//       val wBundling = if (wParZip.length == 0) "0" else wParZip.map{_._2}.mkString(",")
//       val broadcasts = writersOf(lhs)
//         .filter{w => portsOf(w, lhs, i).toList.length > 1}.map { w =>
//         w.node match {
//           case Def(_: SRAMStore[_]) => 1
//           case Def(a@ParSRAMStore(_,_,_,ens)) => ens match {
//             case Op(ListVector(elems)) => elems.length // Was this deprecated?
//             case _ => ens.length
//           }
//         }
//       }
//       val bPar = if (broadcasts.length > 0) broadcasts.mkString(",") else "0"
//       val width = bitWidth(lhs.tp.typeArguments.head)

//       mem match {
//         case BankedMemory(dims, depth, isAccum) =>
//           val strides = src"""List(${dims.map(_.banks)})"""
//           if (depth == 1) {
//             emitGlobalModule(src"""val ${lhs}_$i = Module(new SRAM(List($dimensions), $width, 
//   List(${dims.map(_.banks)}), $strides,
//   List($wPar), List($rPar), BankedMemory, ${spatialConfig.enableSyncMem}
// ))""")
//           } else {
//             appPropertyStats += HasNBufSRAM
//             nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
//             val memname = if (bPar == "0") "NBufSRAMnoBcast" else "NBufSRAM"
//             emitGlobalModule(src"""val ${lhs}_$i = Module(new ${memname}(List($dimensions), $depth, $width,
//   List(${dims.map(_.banks)}), $strides,
//   List($wPar), List($rPar), 
//   List($wBundling), List($rBundling), List($bPar), BankedMemory, ${spatialConfig.enableSyncMem}
// ))""")
//           }
//         case DiagonalMemory(strides, banks, depth, isAccum) =>
//           if (depth == 1) {
//             emitGlobalModule(src"""val ${lhs}_$i = Module(new SRAM(List($dimensions), $width, 
//   List(${(0 until dimensions.length).map{_ => s"$banks"}}), List($strides),
//   List($wPar), List($rPar), DiagonalMemory, ${spatialConfig.enableSyncMem}
// ))""")
//           } else {
//             appPropertyStats += HasNBufSRAM
//             nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
//             val memname = if (bPar == "0") "NBufSRAMnoBcast" else "NBufSRAM"
//             emitGlobalModule(src"""val ${lhs}_$i = Module(new ${memname}(List($dimensions), $depth, $width,
//   List(${(0 until dimensions.length).map{_ => s"$banks"}}), List($strides),
//   List($wPar), List($rPar), 
//   List($wBundling), List($rBundling), List($bPar), DiagonalMemory, ${spatialConfig.enableSyncMem}
// ))""")
//           }
//         }
    
//     case SRAMLoad(sram, dims, is, ofs, en) =>
//       val dispatch = dispatchOf(lhs, sram)
//       val rPar = 1 // Because this is SRAMLoad node    
//       val width = bitWidth(sram.tp.typeArguments.head)
//       emit(s"""// Assemble multidimR vector""")
//       dispatch.foreach{ i =>  // TODO: Shouldn't dispatch only have one element?
//         val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
//         val enable = src"""${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}"""
//         emitGlobalWireMap(src"""${lhs}_rVec""", src"""Wire(Vec(${rPar}, new multidimR(${dims.length}, List(${constDimsOf(sram)}), ${width})))""")
//         emit(src"""${swap(lhs, RVec)}(0).en := Utils.getRetimed($enable, ${enableRetimeMatch(en, lhs)}.toInt) & $en""")
//         is.zipWithIndex.foreach{ case(ind,j) => 
//           emit(src"""${swap(lhs, RVec)}(0).addr($j) := ${ind}.raw // Assume always an int""")
//         }
//         val p = portsOf(lhs, sram, i).head
//         val basequote = src"${lhs}_base" // get string before we create the map
//         emit(src"""val ${basequote} = ${sram}_$i.connectRPort(Vec(${swap(lhs, RVec)}.toArray), $p)""")
//         emitGlobalWireMap(src"""${lhs}""", src"""Wire(${newWire(lhs.tp)})""") 
//         emit(src"""${lhs}.r := ${sram}_$i.io.output.data(${basequote})""")
//       }

//     case SRAMStore(sram, dims, is, ofs, v, en) =>
//       val width = bitWidth(sram.tp.typeArguments.head)
//       val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
//       val enable = src"""${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}"""
//       emit(s"""// Assemble multidimW vector""")
//       emitGlobalWireMap(src"""${lhs}_wVec""", src"""Wire(Vec(1, new multidimW(${dims.length}, List(${constDimsOf(sram)}), $width))) """)
//       emit(src"""${swap(lhs, WVec)}(0).data := $v.raw""")
//       emit(src"""${swap(lhs, WVec)}(0).en := $en & (${enable} & ${swap(parent, IIDone)}).D(${enableRetimeMatch(en, lhs)}.toInt, rr)""")
//       is.zipWithIndex.foreach{ case(ind,j) => 
//         emit(src"""${swap(lhs, WVec)}(0).addr($j) := ${ind}.raw // Assume always an int""")
//       }
//       else {
//         nbufs = nbufs :+ lhs.asInstanceOf[Sym[SRAM[_]]]
//         val memname = if (bPar == "0") "NBufSRAMnoBcast" else "NBufSRAM"
//         emitGlobalModule(src"""val $lhs = Module(new ${memname}(List($dimensions), $depth, $width,
//   List($nBanks), $strides,
//   List($wPar), List($rPar),
//   List($wBundling), List($rBundling), List($bPar), BankedMemory, ${spatialConfig.enableSyncMem}
// ))""")
//       }

    case _:SRAMLoad[_]  => throw new Exception(s"Cannot generate unbanked SRAM load.\n${str(lhs)}")
    case _:SRAMStore[_] => throw new Exception(s"Cannot generate unbanked SRAM store.\n${str(lhs)}")

    case op@BankedSRAMLoad(sram,bank,ofs,ens) =>
      emitBankedLoad(lhs, sram, bank, ofs, ens)(mtyp(op.mT))

    case op@BankedSRAMStore(sram,data,bank,ofs,ens) =>
      emitBankedStore(lhs, sram, data, bank, ofs, ens)(mtyp(op.mT))

    
    /*case ParSRAMLoad(sram,inds,ens) =>
      val dispatch = dispatchOf(lhs, sram)
      val width = bitWidth(sram.tp.typeArguments.head)
      val rPar = inds.length
      val dims = stagedDimsOf(sram)

      // Check if we need to expose a _ready signal to the read port
      val streamOuts = pushesTo(controllerStack.head).distinct.map{ pt => pt.memory match {
          case fifo @ Def(StreamOutNew(bus)) => src"${swap(fifo, Ready)}"
          case _ => ""
        }}.filter(_ != "").mkString(" & ")

      disableSplit = true
      emit(s"""// Assemble multidimR vector""")
      emitGlobalWireMap(src"""${lhs}_rVec""", src"""Wire(Vec(${rPar}, new multidimR(${dims.length}, List(${constDimsOf(sram)}), ${width})))""")
      if (dispatch.toList.length == 1) {
        val k = dispatch.toList.head 
        val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
        inds.zipWithIndex.foreach{ case (ind, i) =>
          emit(src"${swap(lhs, RVec)}($i).en := ${DL(swap(parent, En), src"${enableRetimeMatch(ens(i), lhs)}.toInt", true)} & ${ens(i)}")
          ind.zipWithIndex.foreach{ case (a, j) =>
            emit(src"""${swap(lhs, RVec)}($i).addr($j) := ${a}.raw """)
          }
        }
        val p = portsOf(lhs, sram, k).head
        if (isStreamChild(controllerStack.head) & streamOuts != "") {
          emit(src"""val ${lhs}_base = ${sram}_$k.connectRPort(Vec(${swap(lhs, RVec)}.toArray), $p, $streamOuts)""")
        } else {
          emit(src"""val ${lhs}_base = ${sram}_$k.connectRPort(Vec(${swap(lhs, RVec)}.toArray), $p)""")
        }
        // sram.tp.typeArguments.head match { 
        //   case FixPtType(s,d,f) => if (spatialNeedsFPType(sram.tp.typeArguments.head)) {
              emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""") 
              emit(s"""(0 until ${rPar}).foreach{i => ${quote(lhs)}(i).r := ${quote(sram)}_$k.io.output.data(${quote(lhs)}_base+i) }""")
          //   } else {
          //     emit(src"""val $lhs = (0 until ${rPar}).map{i => ${sram}_$k.io.output.data(${lhs}_base+i) }""")
          //   }
          // case _ => emit(src"""val $lhs = (0 until ${rPar}).map{i => ${sram}_$k.io.output.data(${lhs}_base+i) }""")
        // }
      } else {
        emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""")
        dispatch.zipWithIndex.foreach{ case (k,id) => 
          val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
          emit(src"${swap(lhs, RVec)}($id).en := ${DL(swap(parent, En), swap(parent, Retime), true)} & ${ens(id)}")
          inds(id).zipWithIndex.foreach{ case (a, j) =>
            emit(src"""${swap(lhs, RVec)}($id).addr($j) := ${a}.raw """)
          }
          val p = portsOf(lhs, sram, k).head
          if (isStreamChild(controllerStack.head) & streamOuts != "") {
            emit(src"""val ${lhs}_base_$k = ${sram}_$k.connectRPort(Vec(${swap(lhs, RVec)}($id)), $p, $streamOuts) // TODO: No need to connect all rVec lanes to SRAM even though only one is needed""")
          } else {
            emit(src"""val ${lhs}_base_$k = ${sram}_$k.connectRPort(Vec(${swap(lhs, RVec)}($id)), $p) // TODO: No need to connect all rVec lanes to SRAM even though only one is needed""")
          }
          // sram.tp.typeArguments.head match { 
          //   case FixPtType(s,d,f) => if (spatialNeedsFPType(sram.tp.typeArguments.head)) {
                emit(s"""${quote(lhs)}($id).r := ${quote(sram)}_$k.io.output.data(${quote(lhs)}_base_$k)""")
            //   } else {
            //     emit(src"""${lhs}($id) := ${sram}_$k.io.output.data(${lhs}_base_$k)""")
            //   }
            // case _ => emit(src"""${lhs}($id) := ${sram}_$k.io.output.data(${lhs}_base_$k)""")
          // }
        }
      }
      disableSplit = false

    case ParSRAMStore(sram,inds,data,ens) =>
      val dims = stagedDimsOf(sram)
      val width = bitWidth(sram.tp.typeArguments.head)
      val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
      val enable = src"${swap(parent, DatapathEn)}"
      emit(s"""// Assemble multidimW vector""")
      emitGlobalWireMap(src"""${lhs}_wVec""", src"""Wire(Vec(${inds.indices.length}, new multidimW(${dims.length}, List(${constDimsOf(sram)}), ${width})))""")
      val datacsv = data.map{d => src"${d}.r"}.mkString(",")
      data.zipWithIndex.foreach { case (d, i) =>
        emit(src"""${swap(lhs, WVec)}($i).data := ${d}.r""")
      }
      inds.zipWithIndex.foreach{ case (ind, i) =>
        emit(src"${swap(lhs, WVec)}($i).en := ${ens(i)} & ${DL(src"$enable & ~${swap(parent, Inhibitor)} & ${swap(parent, IIDone)}", src"${enableRetimeMatch(ens(i), lhs)}.toInt")}")
        ind.zipWithIndex.foreach{ case (a, j) =>
          emit(src"""${swap(lhs, WVec)}($i).addr($j) := ${a}.r """)
        }
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) =>
        emit(src"""${sram}_$i.connectWPort(${swap(lhs, WVec)}, List(${portsOf(lhs, sram, i)}))""")
      }*/

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {
    if (config.multifile == 5 || config.multifile == 6) {
      withStream(getStream("Mapping")) {
        emit("// Found the following wires:")
        compressorMap.values.map(_._1).toSet.toList.foreach{wire: String => 
          emit(s"    // $wire (${listHandle(wire)})")
        }
        compressorMap.values.map(_._1).toSet.toList.foreach{wire: String => 
          val handle = listHandle(wire)
          emit("")
          emit(s"// ${wire}")
          emit("// ##################")
          compressorMap.filter(_._2._1 == wire).foreach{entry => 
            emit(s"      // ${handle}(${entry._2._2}) = ${entry._1}")
          }
        }
      }

    }
    withStream(getStream("IOModule")) {
      emit("""// Set Build Info""")
      val trgt = s"${spatialConfig.target.name}".replace("DE1", "de1soc")
      if (config.multifile == 5 || config.multifile == 6) {
        pipeRtMap.groupBy(_._1._1).foreach{x =>
          val listBuilder = x._2.toList.sortBy(_._1._2).map(_._2)
          emit(src"val ${listHandle(x._1)}_rtmap = List(${listBuilder.mkString(",")})")
        }
        // TODO: Make the things below more efficient
        compressorMap.values.map(_._1).toSet.toList.foreach{wire: String => 
          if (wire == "_retime") {
            emit(src"val ${listHandle(wire)} = List[Int](${retimeList.mkString(",")})")  
          }
        }
        compressorMap.values.map(_._1).toSet.toList.foreach{wire: String => 
          if (wire == "_retime") {
          } else if (wire.contains("pipe(") || wire.contains("inner(")) {
            val numel = compressorMap.filter(_._2._1 == wire).size
            emit(src"val ${listHandle(wire)} = List.tabulate(${numel}){i => ${wire.replace("))", src",retime=${listHandle("_retime")}(${listHandle(wire)}_rtmap(i))))")}}")
          } else {
            val numel = compressorMap.filter(_._2._1 == wire).size
            emit(src"val ${listHandle(wire)} = List.fill(${numel}){${wire}}")            
          }
        }
      }

      emit(s"Utils.fixmul_latency = ${latencyOption("FixMul", Some(32))}.toInt")
      emit(s"Utils.fixdiv_latency = ${latencyOption("FixDiv", Some(32))}.toInt")
      emit(s"Utils.fixadd_latency = ${latencyOption("FixAdd", Some(32))}.toInt")
      emit(s"Utils.fixsub_latency = ${latencyOption("FixSub", Some(32))}.toInt")
      emit(s"Utils.fixmod_latency = ${latencyOption("FixMod", Some(32))}.toInt")
      emit(s"Utils.fixeql_latency = ${latencyOption("FixEql", None)}.toInt")
      emit(s"Utils.mux_latency    = ${latencyOption("Mux", None)}.toInt")
      emit(s"Utils.sramload_latency    = ${latencyOption("SRAMLoad", None)}.toInt")
      emit(s"Utils.sramstore_latency    = ${latencyOption("SRAMStore", None)}.toInt")
      emit(s"Utils.SramThreshold = 4")
      emit(s"""Utils.target = ${trgt}""")
      emit(s"""Utils.retime = ${spatialConfig.enableRetiming}""")
    }
    withStream(getStream("BufferControlCxns")) {
      nbufs.foreach{mem =>
        val info = bufferControlInfo(mem)
        info.zipWithIndex.foreach{ case (inf, port) => 
          emit(src"""${mem}.connectStageCtrl(${DL(swap(quote(inf._1), Done), 1, true)}, ${swap(quote(inf._1), BaseEn)}, List(${port})) ${inf._2}""")
        }
      }
    }

    super.emitFileFooter()
  }
    
} 
