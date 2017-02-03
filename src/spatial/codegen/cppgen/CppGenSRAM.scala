package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.SRAMExp
import spatial.SpatialConfig
trait CppGenSRAM extends CppCodegen {
  val IR: SRAMExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.bits}]"
    case _ => super.remap(tp)
  }

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case SRAMNew(dims)=> 
              s"x${lhs.id}_sram"
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

  def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Option[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s" }.mkString(" + ") + ofs.map{o => src" + $o"}.getOrElse("")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case _ => super.emitNode(lhs, rhs)
  }
}
