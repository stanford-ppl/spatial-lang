package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.HostTransferExp
import spatial.SpatialConfig

trait CppGenHostTransfer extends CppCodegen  {
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
    case SetArg(reg, v) => emit(src"interface.ArgIns[0] = (int32_t*) $v; // $lhs", forceful = true)
    case GetArg(reg)    => emit(src"interface.ArgOuts[0] = $reg; // $lhs", forceful = true)
    case SetMem(dram, data) => emit(src"val $lhs = System.arraycopy($data, 0, $dram, 0, $data.length)", forceful = true)
    case GetMem(dram, data) => emit(src"val $lhs = System.arraycopy($dram, 0, $data, 0, $dram.length)", forceful = true)
    case _ => super.emitNode(lhs, rhs)
  }



}
