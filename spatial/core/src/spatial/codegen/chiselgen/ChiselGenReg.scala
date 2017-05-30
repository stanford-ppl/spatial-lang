package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.SpatialConfig
import spatial.SpatialExp
import scala.collection.mutable.Map

trait ChiselGenReg extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._

  var argIns: List[Sym[Reg[_]]] = List()
  var argOuts: List[Sym[Reg[_]]] = List()
  var argIOs: List[Sym[Reg[_]]] = List()
  // var outMuxMap: Map[Sym[Reg[_]], Int] = Map()
  private var nbufs: List[(Sym[Reg[_]], Int)]  = List()

  override protected def spatialNeedsFPType(tp: Type[_]): Boolean = tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
      case FixPtType(s,d,f) => if (s) true else if (f == 0) false else true
      case IntType()  => false
      case LongType() => false
      case FloatType() => true
      case DoubleType() => true
      case _ => super.needsFPType(tp)
  }

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(ArgInNew(_))=> s"x${lhs.id}_argin"
            case Def(ArgOutNew(_)) => s"x${lhs.id}_argout"
            case Def(HostIONew(_)) => s"x${lhs.id}_hostio"
            case Def(RegNew(_)) => s"""x${lhs.id}_${nameOf(lhs).getOrElse("reg").replace("$","")}"""
            case Def(RegRead(reg:Sym[_])) => s"x${lhs.id}_readx${reg.id}"
            case Def(RegWrite(reg:Sym[_],_,_)) => s"x${lhs.id}_writex${reg.id}"
            case _ => super.quote(s)
          }
        case _ => super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: RegType[_] => src"Array[${tp.typeArguments.head}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArgInNew(init)  => 
      argIns = argIns :+ lhs.asInstanceOf[Sym[Reg[_]]]
    case ArgOutNew(init) => 
      emitGlobalWire(src"val ${lhs}_data_options = Wire(Vec(${scala.math.max(1,writersOf(lhs).length)}, UInt(64.W)))", forceful=true)
      emitGlobalWire(src"val ${lhs}_en_options = Wire(Vec(${scala.math.max(1,writersOf(lhs).length)}, Bool()))", forceful=true)
      emit(src"""io.argOuts(${argMapping(lhs)._3}).bits := chisel3.util.Mux1H(${lhs}_en_options, ${lhs}_data_options) // ${nameOf(lhs).getOrElse("")}""", forceful=true)
      emit(src"""io.argOuts(${argMapping(lhs)._3}).valid := ${lhs}_en_options.reduce{_|_}""", forceful=true)
      argOuts = argOuts :+ lhs.asInstanceOf[Sym[Reg[_]]]

    case HostIONew(init) =>
      emitGlobalWire(src"val ${lhs}_data_options = Wire(Vec(${scala.math.max(1,writersOf(lhs).length)}, UInt(64.W)))", forceful = true)
      emitGlobalWire(src"val ${lhs}_en_options = Wire(Vec(${scala.math.max(1,writersOf(lhs).length)}, Bool()))", forceful = true)
      emit(src"""io.argOuts(${argMapping(lhs)._3}).bits := chisel3.util.Mux1H(${lhs}_en_options, ${lhs}_data_options) // ${nameOf(lhs).getOrElse("")}""", forceful = true)
      emit(src"""io.argOuts(${argMapping(lhs)._3}).valid := ${lhs}_en_options.reduce{_|_}""", forceful = true)
      argIOs = argIOs :+ lhs.asInstanceOf[Sym[Reg[_]]]

    case RegNew(init)    => 
      val width = bitWidth(init.tp)
      emitGlobalWire(src"val ${lhs}_initval = ${init}")
      val duplicates = duplicatesOf(lhs)
      duplicates.zipWithIndex.foreach{ case (d, i) => 
        val numBroadcasters = writersOf(lhs).map { write => if (portsOf(write, lhs, i).toList.length > 1) 1 else 0 }.reduce{_+_}
        val numWriters = writersOf(lhs)
          .filter{write => dispatchOf(write, lhs) contains i}
          .filter{w => portsOf(w, lhs, i).toList.length == 1}.length
        reduceType(lhs) match {
          case Some(fps: ReduceFunction) => 
            fps match {
              case FixPtSum => 
                if (d.isAccum) {
                  if (!spatialNeedsFPType(lhs.tp.typeArguments.head)) {
                    emitGlobalModule(src"""val ${lhs}_${i} = Module(new SpecialAccum(1,"add","UInt", List(${width}))) """)  
                  } else {
                    lhs.tp.typeArguments.head match {
                      case FixPtType(s,d,f) => emitGlobalModule(src"""val ${lhs}_${i} = Module(new SpecialAccum(1,"add","FixedPoint", List(${if (s) 1 else 0},$d,$f)))""")  
                      case _ => emitGlobalModule(src"""val ${lhs}_${i} = Module(new SpecialAccum(1,"add","UInt", List(${width}))) // TODO: No match""")  
                    }                  
                  }
                  // Figure out if we need to tie down direct ports
                  val direct_tiedown = writersOf(lhs).map{w => reduceType(w.node).isDefined}.reduce{_&_}
                  if (direct_tiedown) {
                    emitGlobalModule(src"""${lhs}_${i}.io.input.direct_enable := false.B""")
                  }
                } else {
                  if (d.depth > 1) {
                    nbufs = nbufs :+ (lhs.asInstanceOf[Sym[Reg[_]]], i)
                    if (numWriters > 1) throw new Exception(s"Cannot yet generate NBufFF ( ${nameOf(lhs).getOrElse("")} = ${numWriters} writes ) with more than one non-broadcasting writer.  Do you really need to express the app this way?")
                    emitGlobalModule(src"val ${lhs}_${i} = Module(new NBufFF(${d.depth}, ${width})) // ${nameOf(lhs).getOrElse("")}")
                    if (numBroadcasters == 0){
                      emit(src"${lhs}_${i}.io.broadcast.enable := false.B")
                    }
                  } else {
                    emitGlobalModule(src"val ${lhs}_${i} = Module(new FF(${width}, ${numWriters})) // ${nameOf(lhs).getOrElse("")}")
                  }              
                }
              case _ => 
                if (d.depth > 1) {
                  nbufs = nbufs :+ (lhs.asInstanceOf[Sym[Reg[_]]], i)
                  if (numWriters > 1) throw new Exception(s"Cannot yet generate NBufFF ( ${nameOf(lhs).getOrElse("")} = ${numWriters} writes ) with more than one non-broadcasting writer.  Do you really need to express the app this way?")
                  emitGlobalModule(src"val ${lhs}_${i} = Module(new NBufFF(${d.depth}, ${width})) // ${nameOf(lhs).getOrElse("")}")
                  if (numBroadcasters == 0){
                    emit(src"${lhs}_${i}.io.broadcast.enable := false.B")
                  }
                } else {
                  emitGlobalModule(src"val ${lhs}_${i} = Module(new FF(${width}, ${numWriters})) // ${nameOf(lhs).getOrElse("")}")
                }
            }
          case _ =>
            if (d.depth > 1) {
              nbufs = nbufs :+ (lhs.asInstanceOf[Sym[Reg[_]]], i)
              if (numWriters > 1) throw new Exception(s"Cannot yet generate NBufFF ( ${nameOf(lhs).getOrElse("")} = ${numWriters} writes ) with more than one non-broadcasting writer.  Do you really need to express the app this way?")
              emitGlobalModule(src"val ${lhs}_${i} = Module(new NBufFF(${d.depth}, ${width})) // ${nameOf(lhs).getOrElse("")}")
              if (numBroadcasters == 0){
                emit(src"${lhs}_${i}.io.broadcast.enable := false.B")
              }
            } else {
              emitGlobalModule(src"val ${lhs}_${i} = Module(new FF(${width}, ${numWriters})) // ${nameOf(lhs).getOrElse("")}")
            }
        } // TODO: Figure out which reg is really the accum
      }
    case RegRead(reg)    => 
      if (isArgIn(reg) | isHostIO(reg)) {
        emitGlobalWire(src"""val ${lhs} = Wire(${newWire(reg.tp.typeArguments.head)}) // ${nameOf(reg).getOrElse("")}""")
        emitGlobalWire(src"""${lhs}.number := io.argIns(${argMapping(reg)._2})""")
      } else {
        val inst = dispatchOf(lhs, reg).head // Reads should only have one index
        val port = portsOf(lhs, reg, inst)
        val duplicates = duplicatesOf(reg)
        if (duplicates(inst).isAccum) {
          reduceType(lhs) match {
            case Some(fps: ReduceFunction) => 
              fps match {
                case FixPtSum =>
                  if (spatialNeedsFPType(reg.tp.typeArguments.head)) {
                    reg.tp.typeArguments.head match {
                      case FixPtType(s,d,f) => emit(src"""val ${lhs} = Utils.FixedPoint(${if (s) 1 else 0}, $d, $f, ${reg}_initval) // get reset value that was created by reduce controller""")                    
                    }
                  } else {
                    emit(src"""val ${lhs} = ${reg}_initval // get reset value that was created by reduce controller""")                    
                  }
                case _ =>  
                  lhs.tp match { 
                    case FixPtType(s,d,f) => 
                      emit(src"""val $lhs = Wire(${newWire(lhs.tp)})""") 
                      emit(src"""${lhs}.r := ${reg}_${inst}.read(${port.head})""")
                    case BoolType() => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head}) === 1.U(1.W)""") 
                    case _ => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head})""")
                  }
              }
            case _ =>
              lhs.tp match { 
                case FixPtType(s,d,f) => 
                  emit(src"""val $lhs = Wire(${newWire(lhs.tp)})""") 
                  emit(src"""${lhs}.r := ${reg}_${inst}.read(${port.head})""")
                case BoolType() => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head}) === 1.U(1.W)""") 
                case _ => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head})""")
              }
          }
        } else {
          lhs.tp match { 
            case FixPtType(s,d,f) => 
              emit(src"""val $lhs = Wire(${newWire(lhs.tp)})""") 
              emit(src"""${lhs}.r := ${reg}_${inst}.read(${port.head})""")
            case BoolType() => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head}) === 1.U(1.W)""") 
            case _ => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head})""")
          }
        }
      }


    case RegReset(reg,en) => 
      val parent = parentOf(lhs).get
      emitGlobalWire(src"val ${reg}_manual_reset = Wire(Bool())")
      emit(src"${reg}_manual_reset := $en & ${parent}_datapath_en.D(${symDelay(lhs)}) ")


    case RegWrite(reg,v,en) => 
      val manualReset = if (resettersOf(reg).length > 0) {s"| ${quote(reg)}_manual_reset"} else ""
      val parent = writersOf(reg).find{_.node == lhs}.get.ctrlNode
      if (isArgOut(reg) | isHostIO(reg)) {
        val id = argMapping(reg)._3
          emit(src"val ${lhs}_wId = getArgOutLane($id)")
          v.tp match {
            case FixPtType(s,d,f) => 
              if (s) {
                val pad = 64 - d - f
                if (pad > 0) {
                  emit(src"""${reg}_data_options(${lhs}_wId) := util.Cat(util.Fill($pad, ${v}.msb), ${v}.r)""")  
                } else {
                  emit(src"""${reg}_data_options(${lhs}_wId) := ${v}.r""")                  
                }
              } else {
                emit(src"""${reg}_data_options(${lhs}_wId) := ${v}.r""")                  
              }
            case _ => 
              emit(src"""${reg}_data_options(${lhs}_wId) := ${v}.r""")                  
            }
          emit(src"""${reg}_en_options(${lhs}_wId) := $en & ShiftRegister(${parent}_datapath_en, ${symDelay(lhs)})""")
      } else {
        reduceType(lhs) match {
          case Some(fps: ReduceFunction) => // is an accumulator
            duplicatesOf(reg).zipWithIndex.foreach { case (dup, ii) =>
              fps match {
                case FixPtSum =>
                  if (dup.isAccum) {
                    emit(src"""${reg}_${ii}.io.input.next := ${v}.number""")
                    emit(src"""${reg}_${ii}.io.input.enable := ${reg}_wren""")
                    emit(src"""${reg}_${ii}.io.input.init := ${reg}_initval.number""")
                    emit(src"""${reg}_${ii}.io.input.reset := reset | ${reg}_resetter ${manualReset}""")
                    emit(src"""${reg} := ${reg}_${ii}.io.output""")
                    emitGlobalWire(src"""val ${reg} = Wire(${newWire(reg.tp.typeArguments.head)})""")
                  } else {
                    val ports = portsOf(lhs, reg, ii) // Port only makes sense if it is not the accumulating duplicate
                    emit(src"""${reg}_${ii}.write($reg, $en & (${reg}_wren.D(1) /* TODO: This delay actually depends on latency of reduction function */, false.B, List(${ports.mkString(",")}))""")
                    emit(src"""${reg}_${ii}.io.input(0).init := ${reg}_initval.number""")
                    emit(src"""${reg}_$ii.io.input(0).reset := reset ${manualReset}""")
                  }
                case _ =>
                  val ports = portsOf(lhs, reg, ii) // Port only makes sense if it is not the accumulating duplicate
                  emit(src"""${reg}_${ii}.write($v, $en & (${reg}_wren).D(0), false.B, List(${ports.mkString(",")}))""")
                  emit(src"""${reg}_${ii}.io.input(0).init := ${reg}_initval.number""")
                  if (dup.isAccum) {
                    emit(src"""${reg}_$ii.io.input(0).reset := reset | ${reg}_resetter ${manualReset}""")  
                  } else {
                    emit(src"""${reg}_$ii.io.input(0).reset := reset ${manualReset}""")
                  }
                  
              }
            }
          case _ => // Not an accum
            duplicatesOf(reg).zipWithIndex.foreach { case (dup, ii) =>
              val ports = portsOf(lhs, reg, ii) // Port only makes sense if it is not the accumulating duplicate
              emit(src"""${reg}_${ii}.write($v, $en & ShiftRegister(${parent}_datapath_en, ${symDelay(lhs)}), false.B, List(${ports.mkString(",")}))""")
              emit(src"""${reg}_${ii}.io.input(0).init := ${reg}_initval.number""")
              emit(src"""${reg}_$ii.io.input(0).reset := reset ${manualReset}""")
            }
        }
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

    withStream(getStream("Instantiator")) {
      emit("")
      emit("// Scalars")
      emit(s"val numArgIns_reg = ${argIns.length}")
      emit(s"val numArgOuts_reg = ${argOuts.length}")
      emit(s"val numArgIOs_reg = ${argIOs.length}")
      // emit(src"val argIns = Input(Vec(numArgIns, UInt(w.W)))")
      // emit(src"val argOuts = Vec(numArgOuts, Decoupled((UInt(w.W))))")
      argIns.zipWithIndex.map { case(p,i) => 
        emit(s"""//${quote(p)} = argIns($i) ( ${nameOf(p).getOrElse("")} )""")
      }
      argOuts.zipWithIndex.map { case(p,i) => 
        emit(s"""//${quote(p)} = argOuts($i) ( ${nameOf(p).getOrElse("")} )""")
      // argOutsByName = argOutsByName :+ s"${quote(p)}"
      }
      argIOs.zipWithIndex.map { case(p,i) => 
        emit(s"""//${quote(p)} = argIOs($i) ( ${nameOf(p).getOrElse("")} )""")
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
