package spatial.api

import spatial._
import forge._

trait SwitchApi extends SwitchExp { this: SpatialApi => }

trait SwitchExp { this: SpatialExp =>

  @util def create_switch[T:Type](selects: Seq[Exp[Bool]], cases: Seq[() => Exp[T]]): Exp[T] = {
    var cs: Seq[Exp[T]] = Nil
    val body = stageHotBlock{ cs = cases.map{c => c() }; cs.last }
    val effects = body.summary
    stageEffectful(Switch(body, selects, cs), effects)(ctx)
  }

  case class SwitchCase[T:Type](body: Block[T]) extends Op[T] {
    def mirror(f:Tx) = op_case(() => f(body))

    override def freqs = cold(body)
  }

  case class Switch[T:Type](body: Block[T], selects: Seq[Exp[Bool]], cases: Seq[Exp[T]]) extends Op[T] {
    def mirror(f:Tx) = {
      val body2 = stageHotBlock{ f(body) }
      op_switch(body2, f(selects), f(cases))
    }
    override def inputs = dyns(selects)
    override def binds = dyns(cases)
    override def freqs = hot(body)   // Move everything except cases out of body
    val mT = typ[T]
  }

  @util def op_case[T:Type](body: () => Exp[T]): Exp[T] = {
    val block = stageColdBlock{ body() }
    val effects = block.summary.star andAlso Cold
    stageEffectful(SwitchCase(block), effects)(ctx)
  }

  @util def op_switch[T:Type](body: Block[T], selects: Seq[Exp[Bool]], cases: Seq[Exp[T]]): Exp[T] = {
    val effects = body.summary
    stageEffectful(Switch(body, selects, cases), effects)(ctx)
  }


}