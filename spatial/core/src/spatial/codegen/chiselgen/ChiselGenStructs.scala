package spatial.codegen.chiselgen

import argon.core._
import argon.nodes._
import spatial.compiler._
import spatial.nodes._
import spatial.SpatialConfig


trait ChiselGenStructs extends ChiselGenSRAM {

  override protected def spatialNeedsFPType(tp: Type[_]): Boolean = tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
      case FixPtType(s,d,f) => if (s) true else if (f == 0) false else true
      case IntType()  => false
      case LongType() => false
      case FloatType() => true
      case DoubleType() => true
      case _ => super.needsFPType(tp)
  }


  protected def tupCoordinates(tp: Type[_],field: String): (Int,Int) = tp match {
    case x: Tuple2Type[_,_] => field match {
      // A little convoluted because we .reverse simplestructs
      case "_1" => 
        val s = 0
        val width = bitWidth(x.m1)
        (s+width-1, s)
      case "_2" => 
        val s = bitWidth(x.m1)
        val width = bitWidth(x.m2)
        (s+width-1, s)
      }
    case x: StructType[_] =>
      val idx = x.fields.indexWhere(_._1 == field)
      val width = bitWidth(x.fields(idx)._2)
      val prec = x.fields.take(idx)
      val precBits = prec.map{case (_,bt) => bitWidth(bt)}.sum
      (precBits+width-1, precBits)
  }

  override protected def bitWidth(tp: Type[_]): Int = tp match {
      case e: Tuple2Type[_,_]  => super.bitWidth(e.typeArguments(0)) + super.bitWidth(e.typeArguments(1))
      case _ => super.bitWidth(tp)
  }


  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: SimpleStruct[_]) => 
              s"x${lhs.id}_tuple"
            case Def(e: FieldApply[_,_])=>
              s"x${lhs.id}_apply"
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

  override protected def remap(tp: Type[_]): String = tp match {
    // case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SimpleStruct(tuples)  =>
      val items = tuples.map{ t => 
        val width = bitWidth(t._2.tp)
        // if (src"${t._1}" == "offset") {
        //   src"${t._2}"
        // } else {
          if (width > 1 & !spatialNeedsFPType(t._2.tp)) { src"${t._2}(${width-1},0)" } else {src"${t._2}.r"} // FIXME: This is a hacky way to fix chisel/verilog auto-upcasting from multiplies
        // }
      }.reverse.mkString(",")
      val totalWidth = tuples.map{ t => 
        // if (src"${t._1}" == "offset"){
        //   64
        // } else {
          bitWidth(t._2.tp)  
        // }
      }.reduce{_+_}
      emitGlobalWire(src"val $lhs = Wire(UInt(${totalWidth}.W))")
      emit(src"$lhs := chisel3.util.Cat($items)")
    case FieldApply(struct, field) =>
      val (msb, lsb) = tupCoordinates(struct.tp, field)      
      if (spatialNeedsFPType(lhs.tp)) {
        lhs.tp match {
          case FixPtType(s,d,f) => 
            emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""")
            emit(src"""${lhs}.r := ${struct}($msb, $lsb)""")
          case _ => emit(src"val $lhs = ${struct}($msb, $lsb)")
        }
      } else {
        emit(src"val $lhs = ${struct}($msb, $lsb)")
      }

    case _ => super.emitNode(lhs, rhs)
  }
}
