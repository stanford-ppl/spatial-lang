package spatial.codegen.chiselgen

import argon.core._
import argon.codegen.chiselgen.ChiselCodegen
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig


trait ChiselGenSRAM extends ChiselCodegen {
  private var nbufs: List[(Sym[SRAM[_]], Int)] = Nil

  var itersMap = new scala.collection.mutable.HashMap[Bound[_], List[Exp[_]]]
  var cchainPassMap = new scala.collection.mutable.HashMap[Exp[_], Exp[_]] // Map from a cchain to its ctrl node, for computing suffix on a cchain before we enter the ctrler

  // Helper for getting the BigDecimals inside of Const exps for things like dims, when we know that we need the numbers quoted and not chisel types
  protected def getConstValues(all: Seq[Exp[_]]): Seq[BigDecimal] = {
    all.map{i => getConstValue(i)} 
  }
  protected def getConstValue(one: Exp[_]): BigDecimal = {
    one match {case Const(c: BigDecimal) => c }
  }

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

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
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
    val writeSiblings = writePorts.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem, i)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
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
          emit(src"${lhs}_inhibit.io.input.reset := ${lhs}_done.D(1, rr)")
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
      emit(src"val $lhs = Wire(${wire})")
      emit(src"(0 until ${vecWidth}).foreach{i => ${lhs}(i).r := ShiftRegister(${data}(i).r, $delay)}")        
    } else {
      if (isBool) {
        emit(src"""val $lhs = Mux(retime_released, ShiftRegister($data, $delay), false.B)""")
      } else {
        emit(src"""val $lhs = ShiftRegister($data, $delay)""")
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
      val dimensions = dimsOf(lhs)
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
              openGlobalModule(src"""val ${lhs}_$i = Module(new SRAM(List($dimensions), $width, """)
              emitGlobalModule(src"""List(${dims.map(_.banks)}), $strides,""")
              emitGlobalModule(src"""List($wPar), List($rPar), BankedMemory""")
              closeGlobalModule("))")
            } else {
              nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
              openGlobalModule(src"""val ${lhs}_$i = Module(new NBufSRAM(List($dimensions), $depth, $width,""")
              emitGlobalModule(src"""List(${dims.map(_.banks)}), $strides,""")
              emitGlobalModule(src"""List($wPar), List($rPar), """)
              emitGlobalModule(src"""List($wBundling), List($rBundling), List($bPar), BankedMemory""")
              closeGlobalModule("))")
            }
          case DiagonalMemory(strides, banks, depth, isAccum) =>
            if (depth == 1) {
              openGlobalModule(src"""val ${lhs}_$i = Module(new SRAM(List($dimensions), $width, """)
              emitGlobalModule(src"""List(${(0 until dimensions.length).map{_ => s"$banks"}}), List($strides),""")
              emitGlobalModule(src"""List($wPar), List($rPar), DiagonalMemory""")
              closeGlobalModule("))")
            } else {
              nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
              openGlobalModule(src"""val ${lhs}_$i = Module(new NBufSRAM(List($dimensions), $depth, $width,""")
              emitGlobalModule(src"""List(${(0 until dimensions.length).map{_ => s"$banks"}}), List($strides),""")
              emitGlobalModule(src"""List($wPar), List($rPar), """)
              emitGlobalModule(src"""List($wBundling), List($rBundling), List($bPar), DiagonalMemory""")
              closeGlobalModule("))")
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
        val enable = src"""${parent}_datapath_en & ~${parent}_inhibitor"""
        emit(src"""val ${lhs}_rVec = Wire(Vec(${rPar}, new multidimR(${dims.length}, ${width})))""")
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
      val enable = src"""${parent}_datapath_en & ~${parent}_inhibitor"""
      emit(s"""// Assemble multidimW vector""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(1, new multidimW(${dims.length}, $width))) """)
      emit(src"""${lhs}_wVec(0).data := $v.raw""")
      emit(src"""${lhs}_wVec(0).en := $en & ShiftRegister($enable, ${symDelay(lhs)})""")
      is.zipWithIndex.foreach{ case(ind,j) => 
        emit(src"""${lhs}_wVec(0).addr($j) := ${ind}.raw // Assume always an int""")
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) =>
        emit(src"""${sram}_$i.connectWPort(${lhs}_wVec, List(${portsOf(lhs, sram, i)})) """)
      }

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      nbufs.foreach{ case (mem, i) => 
        val info = bufferControlInfo(mem, i)
        info.zipWithIndex.foreach{ case (inf, port) => 
          emit(src"""${mem}_${i}.connectStageCtrl(${quote(inf._1)}_done, ${quote(inf._1)}_base_en, List(${port})) ${inf._2}""")
        }
      }
    }

    super.emitFileFooter()
  }
    
} 
