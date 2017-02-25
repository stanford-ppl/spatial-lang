package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.SpatialConfig
import spatial.SpatialExp


trait ChiselGenStructs extends ChiselCodegen {
  val IR: SpatialExp
  import IR._

  protected def tupCoordinates(tp: Staged[_],field: String): (Int,Int) = tp match {
    case x: Tup2Type[_,_] => field match {
      case "_1" => 
        val s = bitWidth(x.m1)
        val width = bitWidth(x.m2)
        (s, width)
      case "_2" => 
        val s = 0
        val width = bitWidth(x.m1)
        (s, width)
      }
  }


  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case e: SimpleStruct[_]=> 
              s"x${lhs.id}_tuple"
            case e: FieldApply[_,_] =>
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

  override protected def remap(tp: Staged[_]): String = tp match {
    // case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SimpleStruct(tuples)  =>
      val items = tuples.map{ t => src"${t._2}" }.mkString(",")
      emit(src"val $lhs = util.Cat($items)")
    case FieldApply(struct, field) =>
      val (start, width) = tupCoordinates(struct.tp, field)      
      emit(src"val $lhs = ${struct}($start, ${start+width-1})")

      // }

    case _ => super.emitNode(lhs, rhs)
  }



}
