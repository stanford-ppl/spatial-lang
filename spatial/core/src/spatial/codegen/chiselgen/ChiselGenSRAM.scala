package spatial.codegen.chiselgen

import scala.math._
import argon.core._
import argon.codegen.chiselgen.ChiselCodegen
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig

sealed trait StandardSignal
object En extends StandardSignal
object Done extends StandardSignal
object BaseEn extends StandardSignal
object Mask extends StandardSignal
object Resetter extends StandardSignal
object DatapathEn extends StandardSignal
object CtrTrivial extends StandardSignal
// A few non-canonical signals
object IIDone extends StandardSignal
object RstEn extends StandardSignal
object CtrEn extends StandardSignal

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
    var result = super.quote(s)
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

  def swap(lhs: Exp[_], s: StandardSignal): String = {
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
    }
  }

  def swap(lhs: => String, s: StandardSignal): String = {
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

  def cchainWidth(ctr: Exp[Counter]): Int = {
    ctr match {
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
  }

  def getWriteAddition(c: Exp[Any]): String = {
      // If we are inside a stream pipe, the following may be set
      // Add 1 to latency of fifo checks because SM takes one cycle to get into the done state
      val lat = bodyLatency.sum(c)
      val readiers = listensTo(c).distinct.map{_.memory}.map {
        case fifo @ Def(StreamInNew(bus)) => src"${fifo}_valid"
        case _ => ""
      }.filter(_ != "").mkString(" & ")

      val hasReadiers = if (readiers != "") "&" else ""

      src" ${hasReadiers} ${readiers}"

  }

  def getNowValidLogic(c: Exp[Any]): String = { // Because of retiming, the _ready for streamins and _valid for streamins needs to get factored into datapath_en
      // If we are inside a stream pipe, the following may be set
      val readiers = listensTo(c).distinct.map{_.memory}.map {
        case fifo @ Def(StreamInNew(bus)) => src"${fifo}_now_valid" //& ${fifo}_ready"
        case _ => ""
      }.mkString(" & ")
      val hasReadiers = if (readiers != "") "&" else ""
      if (SpatialConfig.enableRetiming) src"${hasReadiers} ${readiers}" else " "
  }
  def getReadyLogic(c: Exp[Any]): String = { // Because of retiming, the _ready for streamins and _valid for streamins needs to get factored into datapath_en
      // If we are inside a stream pipe, the following may be set
      val readiers = listensTo(c).distinct.map{_.memory}.map {
        case fifo @ Def(StreamInNew(bus)) => src"${fifo}_ready"
        case _ => ""
      }.mkString(" & ")
      val hasReadiers = if (readiers != "") "&" else ""
      if (SpatialConfig.enableRetiming) src"${hasReadiers} ${readiers}" else " "
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
    if (SpatialConfig.enableRetiming || SpatialConfig.enablePIRSim) {
      emitGlobalModule(src"val ${lhs}_inhibitor = Wire(Bool())")
      if (fsm.isDefined) {
          emitGlobalModule(src"val ${lhs}_inhibit = Module(new SRFF()) // Module for masking datapath between ctr_done and pipe done")
          emit(src"${lhs}_inhibit.io.input.set := Utils.risingEdge(${fsm.get}_doneCondition)")  
          emit(src"${lhs}_inhibit.io.input.reset := ${lhs}_done.D(1, rr)")
          /* or'ed _doneCondition back in because of BasicCondFSM!! */
          emit(src"${lhs}_inhibitor := ${lhs}_inhibit.io.output.data | ${fsm.get}_doneCondition // Really want inhibit to turn on at last enabled cycle")        
          emit(src"${lhs}_inhibit.io.input.asyn_reset := reset")
      } else if (switch.isDefined) {
        emit(src"${lhs}_inhibitor := ${switch.get}_inhibitor")
      } else {
        if (cchain.isDefined) {
          emitGlobalModule(src"val ${lhs}_inhibit = Module(new SRFF()) // Module for masking datapath between ctr_done and pipe done")
          emit(src"${lhs}_inhibit.io.input.set := ${cchain.get}.io.output.done")  
          emit(src"${lhs}_inhibitor := ${lhs}_inhibit.io.output.data /*| ${cchain.get}.io.output.done*/ // Correction not needed because _done should mask dp anyway")
          emit(src"${lhs}_inhibit.io.input.reset := ${lhs}_done.D(0, rr)")
          emit(src"${lhs}_inhibit.io.input.asyn_reset := reset")
        } else {
          emitGlobalModule(src"val ${lhs}_inhibit = Module(new SRFF()) // Module for masking datapath between ctr_done and pipe done")
          emit(src"${lhs}_inhibit.io.input.set := Utils.risingEdge(${lhs}_done /*${lhs}_sm.io.output.ctr_inc*/)")
          val rster = if (levelOf(lhs) == InnerControl & listensTo(lhs).distinct.length > 0) {src"Utils.risingEdge(${lhs}_done).D(1 + ${lhs}_retime, rr) // Ugly hack, do not try at home"} else src"${lhs}_done.D(1, rr)"
          emit(src"${lhs}_inhibit.io.input.reset := $rster")
          emit(src"${lhs}_inhibitor := ${lhs}_inhibit.io.output.data /*| Utils.delay(Utils.risingEdge(${lhs}_sm.io.output.ctr_inc), 1) // Correction not needed because _done should mask dp anyway*/")
          emit(src"${lhs}_inhibit.io.input.asyn_reset := reset")
        }        
      }
    } else {
      emitGlobalModule(src"val ${lhs}_inhibitor = false.B // Maybe connect to ${lhs}_done?  ")      
    }
  }

  def logRetime(lhs: String, data: String, delay: Int, isVec: Boolean = false, vecWidth: Int = 1, wire: String = "", isBool: Boolean = false): Unit = {
    if (delay > maxretime) maxretime = delay
    if (isVec) {
      emitGlobalWire(src"val $lhs = Wire(${wire})")
      emit(src"(0 until ${vecWidth}).foreach{i => ${lhs}(i).r := ShiftRegister(${data}(i).r, $delay)}")        
    } else {
      if (isBool) {
        emitGlobalWire(src"""val $lhs = Wire(Bool())""");emit(src"""$lhs := ${data}.D($delay, rr)""")
      } else {
        emitGlobalWire(src"""val $lhs = Wire(${wire})""");emit(src"""$lhs := ShiftRegister($data, $delay)""")
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

  override def quote(s: Exp[_]): String = {
    s match {
      case b: Bound[_] => computeSuffix(b)
      case _ =>
        if (SpatialConfig.enableNaming) {
          s match {
            case lhs: Sym[_] =>
              val Op(rhs) = lhs
              rhs match {
                case SRAMNew(dims)=> 
                  s"""x${lhs.id}_${lhs.name.getOrElse("sram").replace("$","")}"""
                case _ =>
                  super.quote(s)
              }
            case _ =>
              super.quote(s)
          }
        } else {
          super.quote(s)
        }
    }
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
    List($wPar), List($rPar), BankedMemory, ${SpatialConfig.enableSyncMem}
  ))""")
            } else {
              nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
              val memname = if (bPar == "0") "NBufSRAMnoBcast" else "NBufSRAM"
              emitGlobalModule(src"""val ${lhs}_$i = Module(new ${memname}(List($dimensions), $depth, $width,
    List(${dims.map(_.banks)}), $strides,
    List($wPar), List($rPar), 
    List($wBundling), List($rBundling), List($bPar), BankedMemory, ${SpatialConfig.enableSyncMem}
  ))""")
            }
          case DiagonalMemory(strides, banks, depth, isAccum) =>
            if (depth == 1) {
              emitGlobalModule(src"""val ${lhs}_$i = Module(new SRAM(List($dimensions), $width, 
    List(${(0 until dimensions.length).map{_ => s"$banks"}}), List($strides),
    List($wPar), List($rPar), DiagonalMemory, ${SpatialConfig.enableSyncMem}
  ))""")
            } else {
              nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
              val memname = if (bPar == "0") "NBufSRAMnoBcast" else "NBufSRAM"
              emitGlobalModule(src"""val ${lhs}_$i = Module(new ${memname}(List($dimensions), $depth, $width,
    List(${(0 until dimensions.length).map{_ => s"$banks"}}), List($strides),
    List($wPar), List($rPar), 
    List($wBundling), List($rBundling), List($bPar), DiagonalMemory, ${SpatialConfig.enableSyncMem}
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
        val enable = src"""${swap(parent, DatapathEn)} & ~${parent}_inhibitor"""
        emit(src"""val ${lhs}_rVec = Wire(Vec(${rPar}, new multidimR(${dims.length}, List(${constDimsOf(sram)}), ${width})))""")
        emit(src"""${lhs}_rVec(0).en := ShiftRegister($enable, ${symDelay(lhs)}) & $en""")
        is.zipWithIndex.foreach{ case(ind,j) => 
          emit(src"""${lhs}_rVec(0).addr($j) := ${ind}.raw // Assume always an int""")
        }
        val p = portsOf(lhs, sram, i).head
        emit(src"""val ${lhs}_base = ${sram}_$i.connectRPort(Vec(${lhs}_rVec.toArray), $p)""")
        emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""") 
        emit(src"""${lhs}.raw := ${sram}_$i.io.output.data(${lhs}_base)""")
      }

    case SRAMStore(sram, dims, is, ofs, v, en) =>
      val width = bitWidth(sram.tp.typeArguments.head)
      val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
      val enable = src"""${swap(parent, DatapathEn)} & ~${parent}_inhibitor"""
      emit(s"""// Assemble multidimW vector""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(1, new multidimW(${dims.length}, List(${constDimsOf(sram)}), $width))) """)
      emit(src"""${lhs}_wVec(0).data := $v.raw""")
      emit(src"""${lhs}_wVec(0).en := $en & (${enable} & ${swap(parent, IIDone)}).D(${symDelay(lhs)}, rr)""")
      is.zipWithIndex.foreach{ case(ind,j) => 
        emit(src"""${lhs}_wVec(0).addr($j) := ${ind}.raw // Assume always an int""")
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) =>
        emit(src"""${sram}_$i.connectWPort(${lhs}_wVec, List(${portsOf(lhs, sram, i)})) """)
      }

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {
    withStream(getStream("IOModule")) {
      emit("""// Set Build Info""")
      val trgt = s"${SpatialConfig.target.name}".replace("DE1", "de1soc")
      emit(src"""${boolMap.map{x => src"// ${x._1} = ${x._2}"}.mkString("\n")}""")
      emit(src"val b = List.fill(${boolMap.size}){Wire(Bool())}")
      emit(s"""Utils.target = ${trgt}""")
      emit(s"""Utils.retime = ${SpatialConfig.enableRetiming}""")
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
