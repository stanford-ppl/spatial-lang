package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.HostTransferExp
import spatial.SpatialConfig

trait ChiselGenHostTransfer extends ChiselCodegen  {
  val IR: HostTransferExp
  import IR._

  override def quote(s: Exp[_]): String = {
  	if (SpatialConfig.enableNaming) {
	    s match {
	      case lhs: Sym[_] =>
	        val Op(rhs) = lhs
	        rhs match {
	          case SetArg(reg:Sym[_],_) => s"x${lhs.id}_set${reg.id}"
	          case GetArg(reg:Sym[_]) => s"x${lhs.id}_get${reg.id}"
	          case SetMem(_,_) => s"x${lhs.id}_setMem"
	          case GetMem(_,_) => s"x${lhs.id}_getMem"
	          case _ => super.quote(s)
	        }
	      case _ =>
	        super.quote(s)
	    }
    } else {
    	super.quote(s)
    }
  } 

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SetArg(reg, v) => emit(src"val $lhs = $reg.update(0, $v)")
    case GetArg(reg)    => emit(src"val $lhs = $reg.apply(0)")
    case SetMem(dram, data) => emit(src"val $lhs = System.arraycopy($data, 0, $dram, 0, $data.length)")
    case GetMem(dram, data) => emit(src"val $lhs = System.arraycopy($dram, 0, $data, 0, $dram.length)")
    case _ => super.emitNode(lhs, rhs)
  }



}
