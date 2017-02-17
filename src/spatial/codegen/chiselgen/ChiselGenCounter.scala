package spatial.codegen.chiselgen

import argon.codegen.FileDependencies
import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.CounterExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait ChiselGenCounter extends ChiselCodegen with FileDependencies {
  val IR: CounterExp with SpatialExp
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
      emit(src"""val ${lhs}_strides = List(${counter_data.map(_._3).mkString(",")}) // TODO: Safe to get rid of this and connect directly?""")
      emit(src"""val ${lhs}_maxes = List(${counter_data.map(_._2).mkString(",")}) // TODO: Safe to get rid of this and connect directly?""")
      emit(src"""val ${lhs} = Module(new Counter(List(${counter_data.map(_._4).mkString(",")})))""")
      emit(src"""${lhs}.io.input.maxes.zip(${lhs}_maxes).foreach { case (port,max) => port := max }""")
      emit(src"""${lhs}.io.input.strides.zip(${lhs}_strides).foreach { case (port,stride) => port := stride }""")
      emit(src"""${lhs}.io.input.enable := ${lhs}_ctr_en""")
      emit(src"""${lhs}.io.input.reset := ${lhs}_resetter""")
      emit(src"""val ${lhs}_maxed = ${lhs}.io.output.saturated""")
      ctrs.zipWithIndex.foreach { case (c, i) =>
        val Def(CounterNew(_,_,_,p)) = c 
        val Const(x: BigDecimal) = p // TODO: Method for extracting counter par??
        emit(s"""val ${quote(c)} = (0 until $x).map{ j => ${quote(lhs)}.io.output.counts($i + j) }""")
      }

    case _ => super.emitNode(lhs, rhs)
  }

}
