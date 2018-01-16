package spatial.codegen.chiselgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.banking._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._


trait ChiselGenReg extends ChiselGenSRAM {
  var argIns: List[Sym[Reg[_]]] = List()
  var argOuts: List[Sym[Reg[_]]] = List()
  var argIOs: List[Sym[Reg[_]]] = List()
  // var outMuxMap: Map[Sym[Reg[_]], Int] = Map()
  private var nbufs: List[Sym[Reg[_]]]  = List()

  override protected def spatialNeedsFPType(tp: Type[_]): Boolean = tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
    case FixPtType(s,d,f) => if (s) true else if (f == 0) false else true
    case IntType()  => false
    case LongType() => false
    case FloatType() => true
    case DoubleType() => true
    case _ => super.needsFPType(tp)
  }

  override protected def name(s: Dyn[_]): String = s match {
    case Def(ArgInNew(_))  => s"${s}_argin"
    case Def(ArgOutNew(_)) => s"${s}_argout"
    case Def(HostIONew(_)) => s"${s}_hostio"
    case Def(RegNew(_))    => s"""${s}_${s.name.getOrElse("reg").replace("$","")}"""

    case Def(RegRead(reg:Sym[_]))      => s"${s}_readx${reg.id}"
    case Def(RegWrite(reg:Sym[_],_,_)) => s"${s}_writex${reg.id}"
    case _ => super.name(s)
  } 

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: RegType[_] => src"Array[${tp.typeArguments.head}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArgInNew(init)  => 
      argIns = argIns :+ lhs.asInstanceOf[Sym[Reg[_]]]
    case ArgOutNew(init) => 
      emitGlobalWireMap(src"${lhs}_data_options", src"Wire(Vec(${scala.math.max(1,writersOf(lhs).length)}, UInt(64.W)))", forceful=true)
      emitGlobalWireMap(src"${lhs}_en_options", src"Wire(Vec(${scala.math.max(1,writersOf(lhs).length)}, Bool()))", forceful=true)
      emit(src"""io.argOuts(${argMapping(lhs).argOutId}).bits := chisel3.util.Mux1H(${swap(lhs, EnOptions)}, ${swap(lhs, DataOptions)}) // ${lhs.name.getOrElse("")}""", forceful=true)
      emit(src"""io.argOuts(${argMapping(lhs).argOutId}).valid := ${swap(lhs, EnOptions)}.reduce{_|_}""", forceful=true)
      argOuts = argOuts :+ lhs.asInstanceOf[Sym[Reg[_]]]

    case HostIONew(init) =>
      emitGlobalWireMap(src"${lhs}_data_options", src"Wire(Vec(${scala.math.max(1,writersOf(lhs).length)}, UInt(64.W)))", forceful = true)
      emitGlobalWireMap(src"${lhs}_en_options", src"Wire(Vec(${scala.math.max(1,writersOf(lhs).length)}, Bool()))", forceful = true)
      emit(src"""io.argOuts(${argMapping(lhs).argOutId}).bits := chisel3.util.Mux1H(${swap(lhs, EnOptions)}, ${swap(lhs, DataOptions)}) // ${lhs.name.getOrElse("")}""", forceful = true)
      emit(src"""io.argOuts(${argMapping(lhs).argOutId}).valid := ${swap(lhs, EnOptions)}.reduce{_|_}""", forceful = true)
      argIOs = argIOs :+ lhs.asInstanceOf[Sym[Reg[_]]]

    case op@RegNew(init)    =>
      // Console.println(src" working on reg $lhs")
      val width = bitWidth(init.tp)
      emitGlobalWire(src"val ${lhs}_initval = ${init}")
      resettersOf(lhs).indices.foreach{ i => emitGlobalWire(src"""val ${lhs}_manual_reset_$i = Wire(Bool())""")}
      if (resettersOf(lhs).nonEmpty) emitGlobalWire(src"""val ${lhs}_manual_reset = ${resettersOf(lhs).indices.map{i => src"${lhs}_manual_reset_$i"}.mkString(" | ")}""")
      val d = instanceOf(lhs)
      val numBroadcasters = writersOf(lhs).count{write => portsOf(write, lhs, 0).size > 1 }
      val numWriters = writersOf(lhs).count{w => portsOf(w, lhs, 0).size == 1}.max(1)
      reduceType(lhs) match {
        case Some(FixPtSum) =>
          if (d.isAccum) {
            if (!spatialNeedsFPType(op.mT)) {
              emitGlobalModule(src"""val $lhs = Module(new SpecialAccum(1,"add","UInt", List(${width}))) """)
            } else {
              op.mT match {
                case FixPtType(s,d,f) => emitGlobalModule(src"""val $lhs = Module(new SpecialAccum(1,"add","FixedPoint", List(${if (s) 1 else 0},$d,$f)))""")
                case _ => emitGlobalModule(src"""val $lhs = Module(new SpecialAccum(1,"add","UInt", List(${width}))) // TODO: No match""")
              }
            }
            // Figure out if we need to tie down direct ports
            val direct_tiedown = writersOf(lhs).map{w => reduceType(lhs).isDefined}.reduce{_&_}
            if (direct_tiedown) {
              emitGlobalModule(src"""$lhs.io.input.direct_enable := false.B""")
            }
          } else {
            if (d.depth > 1) {
              nbufs = nbufs :+ lhs.asInstanceOf[Sym[Reg[_]]]
              if (numWriters > 1) warn(s"You have multiple writers to an NBufFF ( ${lhs.name.getOrElse("")} = ${numWriters} writes ).  Have you considered the loop-carry dependency issues?")
              emitGlobalModuleMap(src"$lhs", src"Module(new NBufFF(${d.depth}, ${width}, numWriters = ${numWriters}))")
              if (numBroadcasters == 0){
                emit(src"${swap(src"$lhs", Blank)}.io.broadcast.enable := false.B")
              }
            } else {
              emitGlobalModuleMap(src"$lhs",src"Module(new templates.FF(${width}, ${numWriters}))")
            }
          }

        case Some(_: ReduceFunction) =>
          if (d.depth > 1) {
            nbufs = nbufs :+ lhs.asInstanceOf[Sym[Reg[_]]]
            if (numWriters > 1) warn(s"You have multiple writers to an NBufFF ( ${lhs.name.getOrElse("")} = ${numWriters} writes ).  Have you considered the loop-carry dependency issues?")
            emitGlobalModule(src"val $lhs = Module(new NBufFF(${d.depth}, ${width}, numWriters = ${numWriters}))")
            if (numBroadcasters == 0){
              emit(src"${swap(src"$lhs", Blank)}.io.broadcast.enable := false.B")
            }
          } else {
            emitGlobalModule(src"val $lhs = Module(new templates.FF(${width}, ${numWriters}))")
          }

        case None =>
          if (d.depth > 1) {
            nbufs = nbufs :+ lhs.asInstanceOf[Sym[Reg[_]]]
            if (numWriters > 1) warn(s"You have multiple writers to an NBufFF ( ${lhs.name.getOrElse("")} = ${numWriters} writes ).  Have you considered the loop-carry dependency issues?")
            emitGlobalModuleMap(src"$lhs", src"Module(new NBufFF(${d.depth}, ${width}, numWriters = ${numWriters}))")
            if (numBroadcasters == 0){
              emit(src"${swap(src"$lhs", Blank)}.io.broadcast.enable := false.B")
            }
          } else {
            emitGlobalModuleMap(src"$lhs",src"Module(new templates.FF(${width}, ${numWriters}))")
          }
      } // TODO: Figure out which reg is really the accum

    case op@RegRead(reg) =>
      if (isArgIn(reg) | isHostIO(reg)) {
        emitGlobalWireMap(src"""$lhs""",src"Wire(${newWire(op.mT)})")
        emitGlobalWire(src"""$lhs.r := io.argIns(${argMapping(reg).argInId})""")
      }
      else {
        emitGlobalWireMap(src"""$lhs""", src"""Wire(${newWire(lhs.tp)})""")

        val inst = instanceOf(reg) // Reads should only have one index
        val port = portsOf(lhs, reg).values.toList
        // Console.println(s"working on $lhs $reg $inst $duplicates")
        reduceType(lhs) match {
          case Some(FixPtSum) if inst.isAccum =>
            if (spatialNeedsFPType(op.mT)) {
              op.mT match {
                case FixPtType(s,d,f) => emit(src"""$lhs.r := Utils.FixedPoint(${if (s) 1 else 0}, $d, $f, ${reg}_initval).r // get reset value that was created by reduce controller""")
              }
            } else {
              emit(src"""$lhs.r := ${reg}_initval // get reset value that was created by reduce controller""")
            }
          case _ => lhs.tp match {
            case FixPtType(s,d,f) =>
              emit(src"""$lhs.r := ${swap(src"${reg}", Blank)}.read(${port.head})""")
            case BooleanType() => emit(src"""$lhs.r := ${swap(src"${reg}", Blank)}.read(${port.head}) === 1.U(1.W)""")
            case _ => emit(src"""$lhs.r := ${swap(src"${reg}", Blank)}.read(${port.head})""")
          }
        }
      }


    case RegReset(reg,en) => 
      val parent = parentOf(lhs).get
      val id = resettersOf(reg).map{_._1}.indexOf(lhs)
      emit(src"${reg}_manual_reset_$id := $en & ${DL(swap(parent, DatapathEn), src"${enableRetimeMatch(en, lhs)}.toInt")} ")

    case op@RegWrite(reg,v,en) =>
      val fully_unrolled_accum = !writersOf(reg).exists{w => readersOf(reg).exists{ r => w.node.dependsOn(r.node) }}
      val manualReset = if (resettersOf(reg).nonEmpty) {s"| ${quote(reg)}_manual_reset"} else ""
      val parent = writersOf(reg).find{_.node == lhs}.get.ctrlNode
      if (isArgOut(reg) | isHostIO(reg)) {
        val id = argMapping(reg).argOutId
        emit(src"val ${lhs}_wId = getArgOutLane($id)")
        v.tp match {
          case FixPtType(s,d,f) =>
            if (s) {
              val pad = 64 - d - f
              if (pad > 0) {
                emit(src"""${swap(reg, DataOptions)}(${lhs}_wId) := util.Cat(util.Fill($pad, ${v}.msb), ${v}.r)""")
              } else {
                emit(src"""${swap(reg, DataOptions)}(${lhs}_wId) := ${v}.r""")
              }
            } else {
              emit(src"""${swap(reg, DataOptions)}(${lhs}_wId) := ${v}.r""")
            }
          }
          emit(src"""${swap(reg, EnOptions)}(${lhs}_wId) := $en & ${DL(swap(parent, DatapathEn), src"${enableRetimeMatch(en, lhs)}.toInt")}""")
      } else {
        reduceType(lhs) match {
          case Some(fps: ReduceFunction) => // is an accumulator
            // Make sure this was not stripped of its accumulation from full unroll
            if (fully_unrolled_accum) {
              emitGlobalWireMap(src"""${reg}_wren""", "Wire(Bool())");emit(src"${swap(reg, Wren)} := ${swap(parentOf(lhs).get, DatapathEn)}")
              emitGlobalWireMap(src"""${reg}_resetter""", "Wire(Bool())");emit(src"""${swap(reg, Resetter)} := ${DL(swap(parentOf(lhs).get, RstEn), src"${enableRetimeMatch(en, lhs)}.toInt", true)} // Delay was added on 12/5/2017, not sure why it wasn't there before""")
            }
            emitGlobalWireMap(src"""${lhs}""", src"""Wire(${newWire(reg.tp.typeArguments.head)})""")
            val dup = instanceOf(reg)
            fps match {
              case FixPtSum =>
                if (dup.isAccum) {
                  emit(src"""${swap(src"${reg}", Blank)}.io.input.next := ${v}.number""")
                  emit(src"""${swap(src"${reg}", Blank)}.io.input.enable := ${DL(swap(reg, Wren), src"${enableRetimeMatch(en, lhs)}.toInt", true)}""")
                  emit(src"""${swap(src"${reg}", Blank)}.io.input.init := ${reg}_initval.number""")
                  emit(src"""${swap(src"${reg}", Blank)}.io.input.reset := reset.toBool | ${DL(src"${swap(reg, Resetter)} ${manualReset}", src"${enableRetimeMatch(en, lhs)}.toInt", true)}""")
                  emit(src"""${lhs} := ${swap(src"${reg}", Blank)}.io.output""")
                } else {
                  val ports = portsOf(lhs, reg).values.toList // Port only makes sense if it is not the accumulating duplicate
                  val data_string = if (fully_unrolled_accum) src"$v" else src"$lhs"
                  emit(src"""${swap(src"${reg}", Blank)}.write(${data_string}, $en & ${DL(src"${swap(reg, Wren)} & ${swap(parent, IIDone)}", src"${enableRetimeMatch(en, lhs)}.toInt+1", true)}, reset.toBool ${manualReset}, List($ports), ${reg}_initval.number, accumulating = ${isAccum(lhs)}) //path4""")
                }
              case _ =>
                val ports = portsOf(lhs, reg).values.toList // Port only makes sense if it is not the accumulating duplicate
                val dlay = if (accumsWithIIDlay.contains(reg)) {src"${reg}_II_dlay"} else "0" // Ultra hacky
                if (dup.isAccum) {
                  emit(src"""${swap(src"${reg}", Blank)}.write($v, $en & ${DL(src"${swap(reg, Wren)} & ${DL(swap(parent, IIDone), dlay, true)}", src"${enableRetimeMatch(en, lhs)}.toInt", true)}, reset.toBool | ${DL(swap(reg, Resetter), src"${enableRetimeMatch(en, lhs)}.toInt", true)} ${manualReset}, List($ports), ${reg}_initval.number, accumulating = ${isAccum(lhs)}) //path1""")
                } else {
                  emit(src"""${swap(src"${reg}", Blank)}.write($v, $en & ${DL(src"${swap(reg, Wren)} & ${DL(swap(parent, IIDone), dlay, true)}", src"${enableRetimeMatch(en, lhs)}.toInt", true)}, reset.toBool ${manualReset}, List($ports), ${reg}_initval.number, accumulating = ${isAccum(lhs)}) //path2""")
                }
                
            }
          case _ => // Not an accum
            val dup = instanceOf(reg)
            val ports = portsOf(lhs, reg).values.toList // Port only makes sense if it is not the accumulating duplicate
            emit(src"""${reg}.write($v, $en & ${DL(src"${swap(parent, DatapathEn)} & ${swap(parent, IIDone)}", src"${enableRetimeMatch(en, lhs)}.toInt", true)}, reset.toBool ${manualReset}, List($ports), ${name(reg.asInstanceOf[Dyn[_]])}_initval.number, accumulating = ${isAccum(lhs)}) //path3""")
        }
      }


    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      nbufs.foreach{mem =>
        val info = bufferControlInfo(mem)
        info.zipWithIndex.foreach{ case (inf, port) => 
          emit(src"""${swap(src"${mem}", Blank)}.connectStageCtrl(${DL(swap(quote(inf._1), Done), 1, true)}, ${swap(quote(inf._1), BaseEn)}, List(${port})) ${inf._2}""")
        }
      }
    }

    withStream(getStream("Instantiator")) {
      emit("")
      emit("// Scalars")
      emit(s"val numArgIns_reg = ${argIns.length}")
      emit(s"val numArgOuts_reg = ${argOuts.length}")
      emit(s"val numArgIOs_reg = ${argIOs.length}")
      // emit(src"val argIns = Input(Vec(numArgIns, UInt(w.W)))")
      // emit(src"val argOuts = Vec(numArgOuts, Decoupled((UInt(w.W))))")
      argIns.zipWithIndex.foreach { case(p,i) =>
        emit(s"""//${quote(p)} = argIns($i) ( ${p.name.getOrElse("")} )""")
      }
      argOuts.zipWithIndex.foreach { case(p,i) =>
        emit(s"""//${quote(p)} = argOuts($i) ( ${p.name.getOrElse("")} )""")
      // argOutsByName = argOutsByName :+ s"${quote(p)}"
      }
      argIOs.zipWithIndex.foreach { case(p,i) =>
        emit(s"""//${quote(p)} = argIOs($i) ( ${p.name.getOrElse("")} )""")
      // argOutsByName = argOutsByName :+ s"${quote(p)}"
      }
    }

    withStream(getStream("IOModule")) {
      emit("// Scalars")
      emit(s"val io_numArgIns_reg = ${argIns.length}")
      emit(s"val io_numArgOuts_reg = ${argOuts.length}")
      emit(s"val io_numArgIOs_reg = ${argIOs.length}")

      // emit("// ArgOut muxes")
      // argOuts.foreach{ a => 
      //   if (writersOf(a).length == 1) {
      //     emit(src"val ${a}_data_options = Wire(UInt(64.W))")
      //     emit(src"val ${a}_en_options = Wire(Bool())")
      //   } else {
      //     emit(src"val ${a}_data_options = Wire(Vec(${writersOf(a).length}, UInt(64.W)))")
      //     emit(src"val ${a}_en_options = Wire(Vec(${writersOf(a).length}, Bool()))")
      //   }
      // }
      // argIOs.foreach{ a => 
      //   if (writersOf(a).length == 1) {
      //     emit(src"val ${a}_data_options = Wire(UInt(64.W))")
      //     emit(src"val ${a}_en_options = Wire(Bool())")
      //   } else {
      //     emit(src"val ${a}_data_options = Wire(Vec(${writersOf(a).length}, UInt(64.W)))")
      //     emit(src"val ${a}_en_options = Wire(Vec(${writersOf(a).length}, Bool()))")
      //   }
      // }
    }

    super.emitFileFooter()
  }
}
