package spatial.codegen.chiselgen

import argon.codegen.FileDependencies
import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.CounterExp
import spatial.SpatialConfig

trait ChiselGenCounter extends ChiselCodegen with FileDependencies {
  val IR: CounterExp
  import IR._

  // dependencies ::= AlwaysDep(s"${SpatialConfig.HOME}/src/spatial/codegen/chiselgen/resources/Counter.chisel")

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
    case CounterNew(start,end,step,par) => 
      emit(s"// Acknowledge counter $lhs")
    case CounterChainNew(ctrs) => 
      val counter_data = ctrs.map{ c =>
        val Def(CounterNew(start, end, step, par)) = c
        (src"$start", src"$end", src"$step", {src"$par"}.split('.').take(1)(0))
      }
      emitGlobal(src"""val ${lhs}_en = Wire(Bool())""")
      emitGlobal(src"""val ${lhs}_resetter = Wire(Bool())""")
      emit(src"""val ${quote(lhs)}_strides = List(${counter_data.map(_._3).mkString(",")})""")
      emit(src"""val ${quote(lhs)}_maxes = List(${counter_data.map(_._2).mkString(",")})""")
      emit(src"""val ${quote(lhs)} = Module(new Counter(List(${counter_data.map(_._4).mkString(",")})))""")
      emit(src"""${quote(lhs)}.io.input.enable := ${quote(lhs)}_en""")
      emit(src"""${quote(lhs)}.io.input.reset := ${lhs}_resetter""")
      emit(src"""val ${quote(lhs)}_maxed = ${quote(lhs)}.io.output.saturated""")

    case _ => super.emitNode(lhs, rhs)
  }

}
