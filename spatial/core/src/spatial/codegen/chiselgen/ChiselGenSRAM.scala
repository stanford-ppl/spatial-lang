package spatial.codegen.chiselgen

import scala.math._
import argon.core._
import argon.codegen.chiselgen.ChiselCodegen
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

sealed trait BooleanSignal
// "Standard" Signals
object En extends BooleanSignal
object Done extends BooleanSignal
object BaseEn extends BooleanSignal
object Mask extends BooleanSignal
object Resetter extends BooleanSignal
object DatapathEn extends BooleanSignal
object CtrTrivial extends BooleanSignal
// A few non-canonical signals
object IIDone extends BooleanSignal
object RstEn extends BooleanSignal
object CtrEn extends BooleanSignal
object Ready extends BooleanSignal
object Valid extends BooleanSignal
object NowValid extends BooleanSignal
object Inhibitor extends BooleanSignal
object Wren extends BooleanSignal
object Chain extends BooleanSignal
object Blank extends BooleanSignal
object DataOptions extends BooleanSignal
object ValidOptions extends BooleanSignal
object ReadyOptions extends BooleanSignal
object RVec extends BooleanSignal
object WVec extends BooleanSignal
object Retime extends BooleanSignal


trait ChiselGenSRAM extends ChiselCodegen {
  private var nbufs: List[(Sym[SRAM[_]], Int)] = Nil

  var itersMap = new scala.collection.mutable.HashMap[Bound[_], List[Exp[_]]]
  var cchainPassMap = new scala.collection.mutable.HashMap[Exp[_], Exp[_]] // Map from a cchain to its ctrl node, for computing suffix on a cchain before we enter the ctrler
  var validPassMap = new scala.collection.mutable.HashMap[(Exp[_], String), Seq[Exp[_]]] // Map from a valid bound sym to its ctrl node, for computing suffix on a valid before we enter the ctrler
  var accumsWithIIDlay = new scala.collection.mutable.ListBuffer[Exp[_]]

  // Helper for getting the BigDecimals inside of Const exps for things like dims, when we know that we need the numbers quoted and not chisel types
  protected def getConstValues(all: Seq[Exp[_]]): Seq[Any] = all.map{i => getConstValue(i) }
  protected def getConstValue(one: Exp[_]): Any = one match {case Const(c) => c }

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

  def swap(lhs: Exp[_], s: BooleanSignal): String = {
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
      case RVec => wireMap(src"${lhs}_rVec")
      case WVec => wireMap(src"${lhs}_wVec")
      case Retime => wireMap(src"${lhs}_retime")
    }
  }

  def swap(lhs: => String, s: BooleanSignal): String = {
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
      case RVec => wireMap(src"${lhs}_rVec")
      case WVec => wireMap(src"${lhs}_wVec")
      case Retime => wireMap(src"${lhs}_retime")
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


  protected def bufferControlInfo(mem: Exp[_], i: Int = 0): List[(Exp[_], String)] = {
    val readers = readersOf(mem)
    val writers = writersOf(mem)
    val readPorts = readers.filter{reader => dispatchOf(reader, mem).contains(i) }.groupBy{a => portsOf(a, mem, i) }.toList
    val writePorts = writers.filter{writer => dispatchOf(writer, mem).contains(i) }.groupBy{a => portsOf(a, mem, i) }.toList
    // Console.println(s"working on $mem $i $readers $readPorts $writers $writePorts")
    // Console.println(s"${readPorts.map{case (_, readers) => readers}}")
    // Console.println(s"innermost ${readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node}")
    // Console.println(s"middle ${parentOf(readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get}")
    // Console.println(s"outermost ${childrenOf(parentOf(readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get)}")
    val readCtrls = readPorts.map{case (port, readers) =>
      val readTops = readers.flatMap{a => topControllerOf(a, mem, i) }
      readTops.headOption.getOrElse{throw new Exception(u"Memory $mem, instance $i, port $port had no read top controllers") }
    }
    if (readCtrls.isEmpty) throw new Exception(u"Memory $mem, instance $i had no readers?")

    // childrenOf(parentOf(readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get)

    val allSiblings = childrenOf(parentOf(readCtrls.head.node).get)
    val readSiblings = readPorts.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem, i)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
    val writeSiblings = writePorts.map{case (_,w) => w.flatMap{ a => topControllerOf(a, mem, i)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
    val writePortsNumbers = writeSiblings.map{ sw => allSiblings.indexOf(sw) }
    val readPortsNumbers = readSiblings.map{ sr => allSiblings.indexOf(sr) }
    val firstActivePort = math.min( readPortsNumbers.min, writePortsNumbers.min )
    val lastActivePort = math.max( readPortsNumbers.max, writePortsNumbers.max )
    val numStagesInbetween = lastActivePort - firstActivePort

    val info = (0 to numStagesInbetween).map { port =>
      val ctrlId = port + firstActivePort
      val node = allSiblings(ctrlId)
      val rd = if (readPortsNumbers.toList.contains(ctrlId)) {"read"} else {
        // emit(src"""${mem}_${i}.readTieDown(${port})""")
        ""
      }
      val wr = if (writePortsNumbers.toList.contains(ctrlId)) {"write"} else {""}
      val empty = if (rd == "" & wr == "") "empty" else ""
      (node, src"/*$rd $wr $empty*/")
    }

    info.toList
  }

  // Emit an SRFF that will block a counter from incrementing after the counter reaches the max
  //  rather than spinning even when there is retiming and the surrounding loop has a delayed
  //  view of the counter done signal
  protected def emitInhibitor(lhs: Exp[_], cchain: Option[Exp[_]], fsm: Option[Exp[_]] = None, switch: Option[Exp[_]]): Unit = {
    if (spatialConfig.enableRetiming || spatialConfig.enablePIRSim) {
      emitGlobalWireMap(src"${lhs}_inhibitor", "Wire(Bool())") // Used to be global module?
      if (fsm.isDefined) {
          emitGlobalModule(src"val ${lhs}_inhibit = Module(new SRFF()) // Module for masking datapath between ctr_done and pipe done")
          emit(src"${lhs}_inhibit.io.input.set := Utils.risingEdge(~${fsm.get})")  
          emit(src"${lhs}_inhibit.io.input.reset := ${swap(lhs, Done)}.D(1 + ${swap(lhs, Retime)}, rr)")
          /* or'ed  back in because of BasicCondFSM!! */
          emit(src"${swap(lhs, Inhibitor)} := ${lhs}_inhibit.io.output.data /*| ${fsm.get}*/ // Really want inhibit to turn on at last enabled cycle")        
          emit(src"${lhs}_inhibit.io.input.asyn_reset := reset")
      } else if (switch.isDefined) {
        emit(src"${swap(lhs, Inhibitor)} := ${swap(switch.get, Inhibitor)}")
      } else {
        if (cchain.isDefined) {
          emitGlobalModule(src"val ${lhs}_inhibit = Module(new SRFF()) // Module for masking datapath between ctr_done and pipe done")
          emit(src"${lhs}_inhibit.io.input.set := ${cchain.get}.io.output.done")  
          emit(src"${swap(lhs, Inhibitor)} := ${lhs}_inhibit.io.output.data /*| ${cchain.get}.io.output.done*/ // Correction not needed because _done should mask dp anyway")
          emit(src"${lhs}_inhibit.io.input.reset := ${swap(lhs, Done)}.D(0, rr)")
          emit(src"${lhs}_inhibit.io.input.asyn_reset := reset")
        } else {
          emitGlobalModule(src"val ${lhs}_inhibit = Module(new SRFF()) // Module for masking datapath between ctr_done and pipe done")
          emit(src"${lhs}_inhibit.io.input.set := Utils.risingEdge(${swap(lhs, Done)} /*${lhs}_sm.io.output.ctr_inc*/)")
          val rster = if (levelOf(lhs) == InnerControl & listensTo(lhs).distinct.length > 0) {src"Utils.risingEdge(${swap(lhs, Done)}).D(1 + ${swap(lhs, Retime)}, rr) // Ugly hack, do not try at home"} else src"${swap(lhs, Done)}.D(1, rr)"
          emit(src"${lhs}_inhibit.io.input.reset := $rster")
          emit(src"${swap(lhs, Inhibitor)} := ${lhs}_inhibit.io.output.data /*| Utils.delay(Utils.risingEdge(${lhs}_sm.io.output.ctr_inc), 1) // Correction not needed because _done should mask dp anyway*/")
          emit(src"${lhs}_inhibit.io.input.asyn_reset := reset")
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
      emit(src"(0 until ${vecWidth}).foreach{i => ${lhs}(i).r := Utils.getRetimed(${data}(i).r, $delay)}")
    } else {
      if (isBool) {
        emitGlobalWireMap(src"""$lhs""", src"""Wire(Bool())""");emit(src"""${lhs} := ${data}.D($delay, rr)""")
      } else {
        emitGlobalWireMap(src"""$lhs""", src"""Wire(${wire})""");emit(src"""${lhs}.r := Utils.getRetimed(${data}.r, $delay)""")
      }
    }
  }

  def logRetime(lhs: => Sym[_], data: String, delay: Int, isVec: Boolean, vecWidth: Int, wire: String, isBool: Boolean): Unit = {
    if (delay > maxretime) maxretime = delay
    if (isVec) {
      emitGlobalWireMap(src"$lhs", src"Wire(${wire})")
      emit(src"(0 until ${vecWidth}).foreach{i => ${lhs}(i).r := Utils.getRetimed(${data}(i).r, $delay)}")
    } else {
      if (isBool) {
        emitGlobalWireMap(src"""$lhs""", src"""Wire(Bool())""");emit(src"""${lhs} := ${data}.D($delay, rr)""")
      } else {
        emitGlobalWireMap(src"""$lhs""", src"""Wire(${wire})""");emit(src"""${lhs}.r := Utils.getRetimed(${data}.r, $delay)""")
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

  def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Option[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s" }.mkString(" + ") + ofs.map{o => src" + $o"}.getOrElse("")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@SRAMNew(_) =>
      val dimensions = constDimsOf(lhs)
      duplicatesOf(lhs).zipWithIndex.foreach{ case (mem, i) => 
        val rParZip = readersOf(lhs)
          .filter{read => dispatchOf(read, lhs) contains i}
          .map { r => 
            val par = r.node match {
              case Def(_: SRAMLoad[_]) => 1
              case Def(a@ParSRAMLoad(_,inds,ens)) => inds.length
            }
            val port = portsOf(r, lhs, i).toList.head
            (par, port)
          }
        val rPar = if (rParZip.length == 0) "1" else rParZip.map{_._1}.mkString(",")
        val rBundling = if (rParZip.length == 0) "0" else rParZip.map{_._2}.mkString(",")
        val wParZip = writersOf(lhs)
          .filter{write => dispatchOf(write, lhs) contains i}
          .filter{w => portsOf(w, lhs, i).toList.length == 1}
          .map { w => 
            val par = w.node match {
              case Def(_: SRAMStore[_]) => 1
              case Def(a@ParSRAMStore(_,_,_,ens)) => ens match {
                case Op(ListVector(elems)) => elems.length // Was this deprecated?
                case _ => ens.length
              }
            }
            val port = portsOf(w, lhs, i).toList.head
            (par, port)
          }
        val wPar = if (wParZip.length == 0) "1" else wParZip.map{_._1}.mkString(",")
        val wBundling = if (wParZip.length == 0) "0" else wParZip.map{_._2}.mkString(",")
        val broadcasts = writersOf(lhs)
          .filter{w => portsOf(w, lhs, i).toList.length > 1}.map { w =>
          w.node match {
            case Def(_: SRAMStore[_]) => 1
            case Def(a@ParSRAMStore(_,_,_,ens)) => ens match {
              case Op(ListVector(elems)) => elems.length // Was this deprecated?
              case _ => ens.length
            }
          }
        }
        val bPar = if (broadcasts.length > 0) broadcasts.mkString(",") else "0"
        val width = bitWidth(lhs.tp.typeArguments.head)

        mem match {
          case BankedMemory(dims, depth, isAccum) =>
            val strides = src"""List(${dims.map(_.banks)})"""
            if (depth == 1) {
              emitGlobalModule(src"""val ${lhs}_$i = Module(new SRAM(List($dimensions), $width, 
    List(${dims.map(_.banks)}), $strides,
    List($wPar), List($rPar), BankedMemory, ${spatialConfig.enableSyncMem}
  ))""")
            } else {
              nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
              val memname = if (bPar == "0") "NBufSRAMnoBcast" else "NBufSRAM"
              emitGlobalModule(src"""val ${lhs}_$i = Module(new ${memname}(List($dimensions), $depth, $width,
    List(${dims.map(_.banks)}), $strides,
    List($wPar), List($rPar), 
    List($wBundling), List($rBundling), List($bPar), BankedMemory, ${spatialConfig.enableSyncMem}
  ))""")
            }
          case DiagonalMemory(strides, banks, depth, isAccum) =>
            if (depth == 1) {
              emitGlobalModule(src"""val ${lhs}_$i = Module(new SRAM(List($dimensions), $width, 
    List(${(0 until dimensions.length).map{_ => s"$banks"}}), List($strides),
    List($wPar), List($rPar), DiagonalMemory, ${spatialConfig.enableSyncMem}
  ))""")
            } else {
              nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
              val memname = if (bPar == "0") "NBufSRAMnoBcast" else "NBufSRAM"
              emitGlobalModule(src"""val ${lhs}_$i = Module(new ${memname}(List($dimensions), $depth, $width,
    List(${(0 until dimensions.length).map{_ => s"$banks"}}), List($strides),
    List($wPar), List($rPar), 
    List($wBundling), List($rBundling), List($bPar), DiagonalMemory, ${spatialConfig.enableSyncMem}
  ))""")
            }
          }
        }
    
    case SRAMLoad(sram, dims, is, ofs, en) =>
      val dispatch = dispatchOf(lhs, sram)
      val rPar = 1 // Because this is SRAMLoad node    
      val width = bitWidth(sram.tp.typeArguments.head)
      emit(s"""// Assemble multidimR vector""")
      dispatch.foreach{ i =>  // TODO: Shouldn't dispatch only have one element?
        val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
        val enable = src"""${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}"""
        emitGlobalWireMap(src"""${lhs}_rVec""", src"""Wire(Vec(${rPar}, new multidimR(${dims.length}, List(${constDimsOf(sram)}), ${width})))""")
        emit(src"""${swap(lhs, RVec)}(0).en := Utils.getRetimed($enable, ${symDelay(lhs)}) & $en""")
        is.zipWithIndex.foreach{ case(ind,j) => 
          emit(src"""${swap(lhs, RVec)}(0).addr($j) := ${ind}.raw // Assume always an int""")
        }
        val p = portsOf(lhs, sram, i).head
        val basequote = src"${lhs}_base" // get string before we create the map
        emit(src"""val ${basequote} = ${sram}_$i.connectRPort(Vec(${swap(lhs, RVec)}.toArray), $p)""")
        emitGlobalWireMap(src"""${lhs}""", src"""Wire(${newWire(lhs.tp)})""") 
        emit(src"""${lhs}.r := ${sram}_$i.io.output.data(${basequote})""")
      }

    case SRAMStore(sram, dims, is, ofs, v, en) =>
      val width = bitWidth(sram.tp.typeArguments.head)
      val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
      val enable = src"""${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}"""
      emit(s"""// Assemble multidimW vector""")
      emitGlobalWireMap(src"""${lhs}_wVec""", src"""Wire(Vec(1, new multidimW(${dims.length}, List(${constDimsOf(sram)}), $width))) """)
      emit(src"""${swap(lhs, WVec)}(0).data := $v.raw""")
      emit(src"""${swap(lhs, WVec)}(0).en := $en & (${enable} & ${swap(parent, IIDone)}).D(${symDelay(lhs)}, rr)""")
      is.zipWithIndex.foreach{ case(ind,j) => 
        emit(src"""${swap(lhs, WVec)}(0).addr($j) := ${ind}.raw // Assume always an int""")
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) =>
        emit(src"""${sram}_$i.connectWPort(${swap(lhs, WVec)}, List(${portsOf(lhs, sram, i)})) """)
      }

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
        // emit("// ##############")
        // emit("// # BOOL WIRES #")
        // emit("// ##############")
        // emit(src"""${boolMap.map{x => src"      // ${x._2} = ${x._1}"}.mkString("\n")}""")
        // emit("// ##############")
        // emit("// # UINT WIRES #")
        // emit("// ##############")
        // emit(src"""${uintMap.map{x => src"      // ${x._2} = ${x._1}"}.mkString("\n")}""")
        // emit("// ##############")
        // emit("// # SINT WIRES #")
        // emit("// ##############")
        // emit(src"""${sintMap.map{x => src"      // ${x._2} = ${x._1}"}.mkString("\n")}""")
        // emit("// ################")
        // emit("// # FIXS32_0 WIRES #")
        // emit("// ################")
        // emit(src"""${fixs32_0Map.map{x => src"      // ${x._2} = ${x._1}"}.mkString("\n")}""")
        // emit("// ################")
        // emit("// # FIXU32_0 WIRES #")
        // emit("// ################")
        // emit(src"""${fixu32_0Map.map{x => src"      // ${x._2} = ${x._1}"}.mkString("\n")}""")
        // emit("// ###################")
        // emit("// # FIXS10_22 WIRES #")
        // emit("// ###################")
        // emit(src"""${fixs10_22Map.map{x => src"      // ${x._2} = ${x._1}"}.mkString("\n")}""")

      }

    }
    withStream(getStream("IOModule")) {
      emit("""// Set Build Info""")
      val trgt = s"${spatialConfig.target.name}".replace("DE1", "de1soc")
      if (config.multifile == 5 || config.multifile == 6) {
        compressorMap.values.map(_._1).toSet.toList.foreach{wire: String => 
          if (wire != "_retime") {
            val numel = compressorMap.filter(_._2._1 == wire).size
            emit(src"val ${listHandle(wire)} = List.fill(${numel}){$wire}")            
          } else {
            // val retimes = compressorMap.filter(_._2._1 == wire).map(_._2._2).map{i => retimeMap(i)}.mkString(",")
            emit(src"val ${listHandle(wire)} = List[Int](${retimeList.mkString(",")})")            
          }
        }
      }
      // emit(src"val b = List.fill(${boolMap.size}){Wire(Bool())}")
      // emit(src"val u = List.fill(${uintMap.size}){Wire(UInt(32.W))}")
      // emit(src"val s = List.fill(${sintMap.size}){Wire(SInt(32.W))}")
      // emit(src"val fs32_0 = List.fill(${fixs32_0Map.size}){Wire(new FixedPoint(true, 32, 0))}")
      // emit(src"val fu32_0 = List.fill(${fixu32_0Map.size}){Wire(new FixedPoint(false, 32, 0))}")
      // emit(src"val fs10_22 = List.fill(${fixs10_22Map.size}){Wire(new FixedPoint(true, 10, 22))}")
      emit(s"""Utils.target = ${trgt}""")
      emit(s"""Utils.retime = ${spatialConfig.enableRetiming}""")
    }
    withStream(getStream("BufferControlCxns")) {
      nbufs.foreach{ case (mem, i) => 
        val info = bufferControlInfo(mem, i)
        info.zipWithIndex.foreach{ case (inf, port) => 
          emit(src"""${mem}_${i}.connectStageCtrl(${swap(quote(inf._1), Done)}.D(1,rr), ${swap(quote(inf._1), BaseEn)}, List(${port})) ${inf._2}""")
        }
      }
    }

    super.emitFileFooter()
  }
    
} 
