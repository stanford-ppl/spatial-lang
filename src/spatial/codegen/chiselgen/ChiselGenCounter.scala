package spatial.codegen.chiselgen

import argon.codegen.FileDependencies
import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.CounterExp
import spatial.SpatialConfig

trait ChiselGenCounter extends ChiselCodegen with FileDependencies {
  val IR: CounterExp
  import IR._

  dependencies ::= AlwaysDep(s"${SpatialConfig.HOME}/src/spatial/codegen/chiselgen/resources/Counter.chisel")

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case CounterNew(s,e,st,p)=> 
              s"x${lhs.id}_ctr"
            case CounterChainNew(ctrs) =>
              s"x${lhs.id}_ctrchain"
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
    case CounterType      => src"Counter"
    case CounterChainType => src"Array[Counter]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case CounterNew(start,end,step,par) => emit(src"val $lhs = Counter($start, $end, $step, $par)")
    case CounterChainNew(ctrs) => emit(src"""val $lhs = Array(${ctrs.map(quote).mkString(",")})""")
    case _ => super.emitNode(lhs, rhs)
  }

}
