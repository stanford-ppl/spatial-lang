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
      val duplicates = duplicatesOf(lhs)  
      withStream(getStream("GlobalWires")) {
        duplicates.zipWithIndex.foreach{ case (d, i) => 
          if (d.depth > 1) {
            emit(src"val ${lhs}_${i}_lib = Module(new NBufFF(${d.depth}, 32)) // ${nameOf(lhs).getOrElse("")}")
          } else {
            emit(src"val ${lhs}_${i}_lib = Module(new FF(32)) // ${nameOf(lhs).getOrElse("")}")
            emit(src"val ${lhs}_$i = ${lhs}_${i}_lib.io.output.data // ${nameOf(lhs).getOrElse("")}")
            emit(src"val ${lhs}_${i}_delayed = Wire(UInt(32)) // ${nameOf(lhs).getOrElse("")}")
          }
        }
      }
    case RegRead(reg)    => 
      // val inst = instanceIndicesOf(reader, reg).head // Reads should only have one index
      // val port = portsOf(reader, reg, inst).head
      // val nbuf = if (duplicatesOf(reg)(inst).depth > 1) {s"_lib.read($port)"} else ""

      if (isArgIn(reg)) {
        withStream(getStream("GlobalWires")) { emit(src"""val $lhs = io.ArgIn.ports(${argIns.indexOf(reg)})""") }
        } else {
          emit(src"""val ${lhs} = ${reg}.read()""")
        }
    case RegWrite(reg,v,en) => 
      if (isArgOut(reg)) {
        emit(src"""val $reg = Reg(init = 0.U) // HW-accessible register""")
        emit(src"""$reg := Mux($en, $v, $reg)""")
        emit(src"""io.ArgOut.ports(${argOuts.indexOf(reg)}) := $reg // ${nameOf(reg).getOrElse("")}""")
      } else {
        emit(s"""write reg""")
      }
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    withStream(getStream("IOModule")) {
      emit(s"""  class ArgInBundle() extends Bundle{
    val ports = Vec(${argIns.length}, Input(UInt(32.W)))""")
      argIns.zipWithIndex.map { case(p,i) => 
        emit(s"""    //  ${quote(p)} = argIns($i) ( ${nameOf(p).getOrElse("")} )""")
      // argInsByName = argInsByName :+ s"${quote(p)}"
      }
      emit("  }")

      emit(s"""  class ArgOutBundle() extends Bundle{
    val ports = Vec(${argOuts.length}, Output(UInt(32.W)))""")
      argOuts.zipWithIndex.map { case(p,i) => 
        emit(s"""    //  ${quote(p)} = argOuts($i) ( ${nameOf(p).getOrElse("")} )""")
      // argOutsByName = argOutsByName :+ s"${quote(p)}"
      }
      emit("  }")
    }

    super.emitFileFooter()
  }
}
