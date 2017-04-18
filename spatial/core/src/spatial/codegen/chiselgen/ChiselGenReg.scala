package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.SpatialConfig
import spatial.SpatialExp
import scala.collection.mutable.Map

trait ChiselGenReg extends ChiselCodegen {
  val IR: SpatialExp
  import IR._

  var argIns: List[Sym[Reg[_]]] = List()
  var argOuts: List[Sym[Reg[_]]] = List()
  var argIOs: List[Sym[Reg[_]]] = List()
  var outMuxMap: Map[Sym[Reg[_]], Int] = Map()
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
      if (writersOf(lhs).length > 1) {
        emitGlobal(src"val ${lhs}_data_options = Wire(Vec(${writersOf(lhs).length}, UInt(64.W)))", forceful=true)
        emitGlobal(src"val ${lhs}_en_options = Wire(Vec(${writersOf(lhs).length}, Bool()))", forceful=true)
        emit(src"""io.argOuts(${argMapping(lhs)._3}).bits := chisel3.util.Mux1H(${lhs}_en_options, ${lhs}_data_options) // ${nameOf(lhs).getOrElse("")}""", forceful=true)
        emit(src"""io.argOuts(${argMapping(lhs)._3}).valid := ${lhs}_en_options.reduce{_|_}""", forceful=true)
        outMuxMap += (lhs.asInstanceOf[Sym[Reg[_]]] -> 0)
      } else {
        emitGlobal(src"val ${lhs}_data_options = Wire(UInt(64.W))", forceful=true)
        emitGlobal(src"val ${lhs}_en_options = Wire(Bool())", forceful=true)
        emit(src"""io.argOuts(${argMapping(lhs)._3}).bits := ${lhs}_data_options // ${nameOf(lhs).getOrElse("")}""", forceful=true)
        emit(src"""io.argOuts(${argMapping(lhs)._3}).valid := ${lhs}_en_options""", forceful=true)
      }
      argOuts = argOuts :+ lhs.asInstanceOf[Sym[Reg[_]]]

    case HostIONew(init) =>
      if (writersOf(lhs).length > 1) {
        emitGlobal(src"val ${lhs}_data_options = Wire(Vec(${writersOf(lhs).length}, UInt(64.W)))", forceful = true)
        emitGlobal(src"val ${lhs}_en_options = Wire(Vec(${writersOf(lhs).length}, Bool()))", forceful = true)
        emit(src"""io.argOuts(${argMapping(lhs)._3}).bits := chisel3.util.Mux1H(${lhs}_en_options, ${lhs}_data_options) // ${nameOf(lhs).getOrElse("")}""", forceful = true)
        emit(src"""io.argOuts(${argMapping(lhs)._3}).valid := ${lhs}_en_options.reduce{_|_}""", forceful = true)
        outMuxMap += (lhs.asInstanceOf[Sym[Reg[_]]] -> 0)
      } else {
        emitGlobal(src"val ${lhs}_data_options = Wire(UInt(64.W))", forceful = true)
        emitGlobal(src"val ${lhs}_en_options = Wire(Bool())", forceful = true)
        emit(src"""io.argOuts(${argMapping(lhs)._3}).bits := ${lhs}_data_options // ${nameOf(lhs).getOrElse("")}""", forceful = true)
        emit(src"""io.argOuts(${argMapping(lhs)._3}).valid := ${lhs}_en_options""", forceful = true)
      }
      argIOs = argIOs :+ lhs.asInstanceOf[Sym[Reg[_]]]

    case RegNew(init)    => 
      val width = bitWidth(init.tp)
      emitGlobal(src"val ${lhs}_initval = ${init}")
      val duplicates = duplicatesOf(lhs)  
      duplicates.zipWithIndex.foreach{ case (d, i) => 
        val numBroadcasters = writersOf(lhs).map { write => if (portsOf(write, lhs, i).toList.length > 1) 1 else 0 }.reduce{_+_}
        reduceType(lhs) match {
          case Some(fps: ReduceFunction) => 
            fps match {
              case FixPtSum => 
                if (d.isAccum) {
                  if (!spatialNeedsFPType(lhs.tp.typeArguments.head)) {
                    Console.println(s"tp ${lhs.tp.typeArguments.head} has fp ${spatialNeedsFPType(lhs.tp.typeArguments.head)}")
                    emitGlobal(src"""val ${lhs}_${i} = Module(new SpecialAccum(1,"add","UInt", List(${width}))) """)  
                  } else {
                    lhs.tp.typeArguments.head match {
                      case FixPtType(s,d,f) => emitGlobal(src"""val ${lhs}_${i} = Module(new SpecialAccum(1,"add","FixedPoint", List(${if (s) 1 else 0},$d,$f)))""")  
                      case _ => emitGlobal(src"""val ${lhs}_${i} = Module(new SpecialAccum(1,"add","UInt", List(${width}))) // TODO: No match""")  
                    }                  

                  }
                } else {
                  if (d.depth > 1) {
                    nbufs = nbufs :+ (lhs.asInstanceOf[Sym[Reg[_]]], i)
                    emitGlobal(src"val ${lhs}_${i} = Module(new NBufFF(${d.depth}, ${width})) // ${nameOf(lhs).getOrElse("")}")
                    if (numBroadcasters == 0){
                      emit(src"${lhs}_${i}.io.broadcast.enable := false.B")
                    }
                  } else {
                    emitGlobal(src"val ${lhs}_${i} = Module(new FF(${width})) // ${nameOf(lhs).getOrElse("")}")
                  }              
                }
              case _ => 
                if (d.depth > 1) {
                  nbufs = nbufs :+ (lhs.asInstanceOf[Sym[Reg[_]]], i)
                  emitGlobal(src"val ${lhs}_${i} = Module(new NBufFF(${d.depth}, ${width})) // ${nameOf(lhs).getOrElse("")}")
                  if (numBroadcasters == 0){
                    emit(src"${lhs}_${i}.io.broadcast.enable := false.B")
                  }
                } else {
                  emitGlobal(src"val ${lhs}_${i} = Module(new FF(${width})) // ${nameOf(lhs).getOrElse("")}")
                }
            }
          case _ =>
            if (d.depth > 1) {
              nbufs = nbufs :+ (lhs.asInstanceOf[Sym[Reg[_]]], i)
              emitGlobal(src"val ${lhs}_${i} = Module(new NBufFF(${d.depth}, ${width})) // ${nameOf(lhs).getOrElse("")}")
              if (numBroadcasters == 0){
                emit(src"${lhs}_${i}.io.broadcast.enable := false.B")
              }
            } else {
              emitGlobal(src"val ${lhs}_${i} = Module(new FF(${width})) // ${nameOf(lhs).getOrElse("")}")
            }
        } // TODO: Figure out which reg is really the accum
      }
    case RegRead(reg)    => 
      if (isArgIn(reg) | isHostIO(reg)) {
        if (spatialNeedsFPType(reg.tp.typeArguments.head)) {
          reg.tp.typeArguments.head match {
            case FixPtType(s,d,f) => 
              emitGlobal(src"""val ${lhs} = Wire(new FixedPoint($s, $d, $f))""")
              emitGlobal(src"""${lhs}.number := io.argIns(${argMapping(reg)._2})""")
          }
        }
      } else {
        val inst = dispatchOf(lhs, reg).head // Reads should only have one index
        val port = portsOf(lhs, reg, inst)
        val duplicates = duplicatesOf(reg)
        if (duplicates(inst).isAccum) {
          reduceType(reg) match {
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
                  lhs.tp match { // TODO: If this is a tuple reg, are we guaranteed a field apply later?
                    case FixPtType(s,d,f) => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head}).FP($s, $d, $f)""")
                    case _ => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head})""")
                  }
              }
            case _ =>
              lhs.tp match { // TODO: If this is a tuple reg, are we guaranteed a field apply later?
                case FixPtType(s,d,f) => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head}).FP($s, $d, $f)""")
                case _ => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head})""")
              }
          }
        } else {
          lhs.tp match { // TODO: If this is a tuple reg, are we guaranteed a field apply later?
            case FixPtType(s,d,f) => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head}).FP($s, $d, $f)""")
            case _ => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head})""")
          }
        }
      }


    case RegWrite(reg,v,en) => 
      val parent = writersOf(reg).find{_.node == lhs}.get.ctrlNode
      if (isArgOut(reg) | isHostIO(reg)) {
        if (writersOf(reg).length > 1) {
          val id = outMuxMap(reg.asInstanceOf[Sym[Reg[_]]])
          emit(src"""${reg}_data_options($id) := ${v}.number""")
          emit(src"""${reg}_en_options($id) := $en & ${parent}_datapath_en""")
          outMuxMap += (reg.asInstanceOf[Sym[Reg[_]]] -> {id + 1})
        } else {
          emit(src"""${reg}_data_options := ${v}.number""")
          emit(src"""${reg}_en_options := $en & ${parent}_datapath_en""")
        }
      } else {
        reduceType(reg) match {
          case Some(fps: ReduceFunction) => // is an accumulator
            duplicatesOf(reg).zipWithIndex.foreach { case (dup, ii) =>
              fps match {
                case FixPtSum =>
                  if (dup.isAccum) {
                    emit(src"""${reg}_${ii}.io.next := ${v}.number""")
                    emit(src"""${reg}_${ii}.io.enable := ${reg}_wren""")
                    emit(src"""${reg}_${ii}.io.init := ${reg}_initval.number""")
                    emit(src"""${reg}_${ii}.io.reset := reset | ${reg}_resetter""")
                    emit(src"""${reg} := ${reg}_${ii}.io.output""")
                    emitGlobal(src"""val ${reg} = Wire(UInt(32.W))""")
                  } else {
                    val ports = portsOf(lhs, reg, ii) // Port only makes sense if it is not the accumulating duplicate
                    emit(src"""${reg}_${ii}.write($reg, $en & Utils.delay(${reg}_wren,1) /* TODO: This delay actually depends on latency of reduction function */, false.B, List(${ports.mkString(",")}))""")
                    emit(src"""${reg}_${ii}.io.input.init := ${reg}_initval.number""")
                    emit(src"""${reg}_$ii.io.input.reset := reset""")
                  }
                case _ =>
                  val ports = portsOf(lhs, reg, ii) // Port only makes sense if it is not the accumulating duplicate
                  emit(src"""${reg}_${ii}.write($v, $en & Utils.delay(${reg}_wren,1), false.B, List(${ports.mkString(",")}))""")
                  emit(src"""${reg}_${ii}.io.input.init := ${reg}_initval.number""")
                  if (dup.isAccum) {
                    emit(src"""${reg}_$ii.io.input.reset := reset | ${reg}_resetter""")  
                  } else {
                    emit(src"""${reg}_$ii.io.input.reset := reset""")
                  }
                  
              }
            }
          case _ => // Not an accum
            duplicatesOf(reg).zipWithIndex.foreach { case (dup, ii) =>
              val ports = portsOf(lhs, reg, ii) // Port only makes sense if it is not the accumulating duplicate
              emit(src"""${reg}_${ii}.write($v, $en & ${parent}_datapath_en, false.B, List(${ports.mkString(",")}))""")
              emit(src"""${reg}_${ii}.io.input.init := ${reg}_initval.number""")
              emit(src"""${reg}_$ii.io.input.reset := reset""")
            }
        }
      }

    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      nbufs.foreach{ case (mem, i) => 
        // Console.println(src"working on $mem $i")
        // TODO: Does david figure out which controllers' signals connect to which ports on the nbuf already? This is kind of complicated
        val readers = readersOf(mem)
        val writers = writersOf(mem)
        val readPorts = readers.filter{reader => dispatchOf(reader, mem).contains(i) }.groupBy{a => portsOf(a, mem, i) }
        val writePorts = writers.filter{writer => dispatchOf(writer, mem).contains(i) }.groupBy{a => portsOf(a, mem, i) }
        // Console.println(s"read ports $readPorts")
        // Console.println(s"""topctrl ${readPorts.map{case (_, readers) => s"want ${readers.head}, $mem, $i"}} """)
        val allSiblings = childrenOf(parentOf(readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get)
        val readSiblings = readPorts.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem, i)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
        val writeSiblings = writePorts.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem, i)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
        val writePortsNumbers = writeSiblings.map{ sw => allSiblings.indexOf(sw) }
        val readPortsNumbers = readSiblings.map{ sr => allSiblings.indexOf(sr) }
        val firstActivePort = math.min( readPortsNumbers.min, writePortsNumbers.min )
        val lastActivePort = math.max( readPortsNumbers.max, writePortsNumbers.max )
        val numStagesInbetween = lastActivePort - firstActivePort

        (0 to numStagesInbetween).foreach { port =>
          val ctrlId = port + firstActivePort
          val node = allSiblings(ctrlId)
          val rd = if (readPortsNumbers.toList.contains(ctrlId)) {"read"} else ""
          val wr = if (writePortsNumbers.toList.contains(ctrlId)) {"write"} else ""
          val empty = if (rd == "" & wr == "") "empty" else ""
          emit(src"""${mem}_${i}.connectStageCtrl(${quote(node)}_done, ${quote(node)}_en, List(${port})) /*$rd $wr $empty*/""")
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
    }

    super.emitFileFooter()
  }
}
