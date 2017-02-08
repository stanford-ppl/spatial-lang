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
    case SetArg(reg, v) => 
      emit(src"interface.ArgIns[0] = (${reg.tp}*) $v; // $lhs", forceful = true)
      emit(src"${reg.tp} $reg = $v;")
    case GetArg(reg)    => emit(src"${lhs.tp} $lhs = *$reg;", forceful = true)
    case SetMem(dram, data) => 
      emit(src"// Temporarily do nothing here.  ${lhs.tp} $lhs = System.arraycopy($data, 0, $dram, 0, $data.length)", forceful = true)
    case GetMem(dram, data) => 
      open(src"for (int i = 0; i < interface.memOut_length(); i++) { // Will be 0 if this app has an argout")
      emit(src"${dram}->add_mem(interface.get_mem(i));")
      close("}")
      emit(src"// ${data.tp} $lhs = something related to interface", forceful = true)
    case _ => super.emitNode(lhs, rhs)
  }



}
