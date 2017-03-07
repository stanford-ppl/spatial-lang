package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.RegExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait ChiselGenReg extends ChiselCodegen {
  val IR: RegExp with SpatialExp
  import IR._

  var argIns: List[Sym[Reg[_]]] = List()
  var argOuts: List[Sym[Reg[_]]] = List()
  private var nbufs: List[(Sym[Reg[_]], Int)]  = List()

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case ArgInNew(_)=> s"x${lhs.id}_argin"
            case ArgOutNew(_) => s"x${lhs.id}_argout"
            case RegNew(_) => s"x${lhs.id}_reg"
            case RegRead(reg:Sym[_]) => s"x${lhs.id}_readx${reg.id}"
            case RegWrite(reg:Sym[_],_,_) => s"x${lhs.id}_writex${reg.id}"
            case _ => super.quote(s)
          }
        case _ => super.quote(s)
      }
    } else {
      super.quote(s)
    }
  }

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: RegType[_] => src"Array[${tp.typeArguments.head}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArgInNew(init)  => 
      argIns = argIns :+ lhs.asInstanceOf[Sym[Reg[_]]]
      emit(src"val $lhs = Array($init)")
    case ArgOutNew(init) => 
      argOuts = argOuts :+ lhs.asInstanceOf[Sym[Reg[_]]]
      emit(src"val $lhs = Array($init)")
    case RegNew(init)    => 
      val width = bitWidth(init.tp)
      val duplicates = duplicatesOf(lhs)  
      duplicates.zipWithIndex.foreach{ case (d, i) => 
        reduceType(lhs) match {
          case Some(fps: ReduceFunction) => 
            fps match {
              case FixPtSum => 
                if (d.isAccum) {
                  if (!hasFracBits(lhs.tp.typeArguments.head)) {
                    emitGlobal(src"""val ${lhs}_${i} = Module(new SpecialAccum(1,"add","UInt", List(${width}))) // TODO: Create correct accum based on type""")  
                  } else {
                    lhs.tp.typeArguments.head match {
                      case FixPtType(s,d,f) => emitGlobal(src"""val ${lhs}_${i} = Module(new SpecialAccum(1,"add","FixedPoint", List(${if (s) 1 else 0},$d,$f))) // TODO: Create correct accum based on type""")  
                      case _ => emitGlobal(src"""val ${lhs}_${i} = Module(new SpecialAccum(1,"add","UInt", List(${width}))) // TODO: Create correct accum based on type""")  
                    }                  

                  }
                } else {
                  if (d.depth > 1) {
                    nbufs = nbufs :+ (lhs.asInstanceOf[Sym[Reg[_]]], i)
                    emitGlobal(src"val ${lhs}_${i} = Module(new NBufFF(${d.depth}, ${width})) // ${nameOf(lhs).getOrElse("")}")
                  } else {
                    emitGlobal(src"val ${lhs}_${i} = Module(new FF(${width})) // ${nameOf(lhs).getOrElse("")}")
                  }              
                }
              case _ => 
                if (d.depth > 1) {
                  nbufs = nbufs :+ (lhs.asInstanceOf[Sym[Reg[_]]], i)
                  emitGlobal(src"val ${lhs}_${i} = Module(new NBufFF(${d.depth}, ${width})) // ${nameOf(lhs).getOrElse("")}")
                } else {
                  emitGlobal(src"val ${lhs}_${i} = Module(new FF(${width})) // ${nameOf(lhs).getOrElse("")}")
                }
            }
          case _ =>
            if (d.depth > 1) {
              nbufs = nbufs :+ (lhs.asInstanceOf[Sym[Reg[_]]], i)
              emitGlobal(src"val ${lhs}_${i} = Module(new NBufFF(${d.depth}, ${width})) // ${nameOf(lhs).getOrElse("")}")
            } else {
              emitGlobal(src"val ${lhs}_${i} = Module(new FF(${width})) // ${nameOf(lhs).getOrElse("")}")
            }
        } // TODO: Figure out which reg is really the accum
      }
    case RegRead(reg)    => 
      if (isArgIn(reg)) {
        emitGlobal(src"""val $lhs = io.argIns(${argMapping(reg)})""")
      } else {
        val inst = dispatchOf(lhs, reg).head // Reads should only have one index
        val port = portsOf(lhs, reg, inst)
        val duplicates = duplicatesOf(reg)
        if (duplicates(inst).isAccum) {
          reduceType(reg) match {
            case Some(fps: ReduceFunction) => 
              fps match {
                case FixPtSum =>
                  if (hasFracBits(reg.tp.typeArguments.head)) {
                    reg.tp.typeArguments.head match {
                      case FixPtType(s,d,f) => emit(src"""val ${lhs} = Utils.FixedPoint(${if (s) 1 else 0}, $d, $f, ${reg}_initval // get reset value that was created by reduce controller""")                    
                    }
                  } else {
                    emit(src"""val ${lhs} = ${reg}_initval // get reset value that was created by reduce controller""")                    
                  }
                  
                case _ =>  
                  reg.tp.typeArguments.head match { // TODO: If this is a tuple reg, are we guaranteed a field apply later?
                    case FixPtType(s,d,f) => emit(src"""val $lhs = Utils.FixedPoint(${if (s) 1 else 0}, $d, $f, ${reg}_${inst}.read(${port.head})""")
                    case _ => emit(src"""val $lhs = ${reg}_${inst}.read(${port.head})""")
                  }
              }
            case _ =>
              throw new AccumWithoutReduceFunctionException(reg, lhs)
          }
        } else {
          emit(src"""val ${lhs} = ${reg}_${inst}.read(${port.head})""")
        }
      }


    case RegWrite(reg,v,en) => 
      val parent = writersOf(reg).find{_.node == lhs}.get.ctrlNode
      if (isArgOut(reg)) {
        emit(src"""val $reg = Reg(init = 0.U) // HW-accessible register""")
        v.tp match {
          case FixPtType(_,_,_) => if (hasFracBits(v.tp)) {
              emit(src"""$reg := Mux($en & ${parent}_en, ${v}.number, $reg)""")
            } else {
              emit(src"""$reg := Mux($en & ${parent}_en, $v, $reg)""") 
            }
          case _ => emit(src"""$reg := Mux($en & ${parent}_en, $v, $reg)""")
        }
        
        emit(src"""io.argOuts(${argOuts.indexOf(reg)}).bits := ${reg} // ${nameOf(reg).getOrElse("")}""")
        emit(src"""io.argOuts(${argOuts.indexOf(reg)}).valid := $en & ${parent}_en""")
      } else {         
        reduceType(reg) match {
          case Some(fps: ReduceFunction) => // is an accumulator
            duplicatesOf(reg).zipWithIndex.foreach { case (dup, ii) =>
              fps match {
                case FixPtSum =>
                  if (dup.isAccum) {
                    emit(src"""${reg}_${ii}.io.next := ${v}""")
                    emit(src"""${reg}_${ii}.io.enable := ${reg}_wren""")
                    emit(src"""${reg}_${ii}.io.reset := Utils.delay(${reg}_resetter, 2)""")
                    emit(src"""${reg} := ${reg}_${ii}.io.output""")
                    emitGlobal(src"""val ${reg} = Wire(UInt())""")
                  } else {
                    val ports = portsOf(lhs, reg, ii) // Port only makes sense if it is not the accumulating duplicate
                    emit(src"""${reg}_${ii}.write($reg, $en & Utils.delay(${reg}_wren,1) /* TODO: This delay actually depends on latency of reduction function */, false.B, List(${ports.mkString(",")}))""")
                  }
                case _ =>
                  val ports = portsOf(lhs, reg, ii) // Port only makes sense if it is not the accumulating duplicate
                  emit(src"""${reg}_${ii}.write($v, $en & Utils.delay(${reg}_wren,1), false.B, List(${ports.mkString(",")}))""")
              }
            }
          case _ => // Not an accum
            duplicatesOf(reg).zipWithIndex.foreach { case (dup, ii) =>
              val ports = portsOf(lhs, reg, ii) // Port only makes sense if it is not the accumulating duplicate
              emit(src"""${reg}_${ii}.write($v, $en & ${parent}_datapath_en, false.B, List(${ports.mkString(",")}))""")
            }
        }
      }

    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      nbufs.foreach{ case (mem, i) => 
        // TODO: Does david figure out which controllers' signals connect to which ports on the nbuf already? This is kind of complicated
        val readers = readersOf(mem)
        val writers = writersOf(mem)
        val readPorts = readers.filter{reader => dispatchOf(reader, mem).contains(i) }.groupBy{a => portsOf(a, mem, i) }
        val writePorts = writers.filter{writer => dispatchOf(writer, mem).contains(i) }.groupBy{a => portsOf(a, mem, i) }
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
      // emit(src"val argIns = Input(Vec(numArgIns, UInt(w.W)))")
      // emit(src"val argOuts = Vec(numArgOuts, Decoupled((UInt(w.W))))")
      argIns.zipWithIndex.map { case(p,i) => 
        emit(s"""//${quote(p)} = argIns($i) ( ${nameOf(p).getOrElse("")} )""")
      }
      argOuts.zipWithIndex.map { case(p,i) => 
        emit(s"""//${quote(p)} = argOuts($i) ( ${nameOf(p).getOrElse("")} )""")
      // argOutsByName = argOutsByName :+ s"${quote(p)}"
      }
    }

    withStream(getStream("IOModule")) {
      emit("// Scalars")
      emit(s"val io_numArgIns_reg = ${argIns.length}")
      emit(s"val io_numArgOuts_reg = ${argOuts.length}")
    }

    super.emitFileFooter()
  }
}
