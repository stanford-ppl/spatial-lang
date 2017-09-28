package spatial.transform

import argon.core._
import argon.transform.ForwardTransformer
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

case class RegReadCSE(var IR: State) extends ForwardTransformer {
  override val name = "Register Read CSE"

  // Mechanism to track duplicates that are no longer needed due to CSE'd register reads
  var csedDuplicates = Map[Exp[_], Set[Int]]()
  private def removeDuplicates(reg: Exp[_], dups: Set[Int]) = {
    csedDuplicates += reg -> (dups ++ csedDuplicates.getOrElse(reg, Set.empty))
  }

  override protected def postprocess[T:Type](block: Block[T]): Block[T] = {
    // Remove CSE'd register duplicates from the metadata
    /*for ((k,v) <- subst) {
      dbg(c"$k -> $v")
    }*/

    for ((reg,csed) <- csedDuplicates) {
      val orig = duplicatesOf(reg)
      val duplicates = orig.zipWithIndex.filter{case (dup,i) => !csed.contains(i) }
      duplicatesOf(reg) = duplicates.map(_._1)

      val mapping = duplicates.map(_._2).zipWithIndex.toMap

      val writers = writersOf(reg).map{case (n,c) => (f(n), (f(c._1),c._2)) }.distinct
      val readers = readersOf(reg).map{case (n,c) => (f(n), (f(c._1),c._2)) }.distinct
      val accesses = (writers ++ readers).map(_.node).distinct

      dbg("")
      dbg(u"$reg")
      dbg(c"CSEd duplicates: $csed")
      accesses.foreach{access =>
        dispatchOf.get(access, reg).foreach{orig =>
          dispatchOf(access, reg) = orig.flatMap{o => mapping.get(o) }
        }
        portsOf.get(access,reg).foreach{orig =>
          portsOf.set(access, reg, orig.flatMap{case (i,ps) => mapping.get(i).map{i2 => i2 -> ps} })
        }

        dbg(u"${str(access)}: " + dispatchOf.get(access, reg).map(_.toString).getOrElse(""))
      }
    }

    super.postprocess(block)
  }

  var inInnerCtrl: Boolean = false
  def inInner[A](x: => A): A = {
    val prev = inInnerCtrl
    inInnerCtrl = true
    val result = x
    inInnerCtrl = prev
    result
  }

  // TODO: This creates unused register duplicates in metadata if the inner loop in question was previously unrolled
  // How to handle this?
  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case e@RegRead(reg) if inInnerCtrl =>
      dbg(c"Found reg read $lhs = $rhs")
      val rhs2 = RegRead(f(reg))(typ[T],mbits(e.bT)) // Note that this hasn't been staged yet, only created the node
      val effects = effectsOf(lhs).mirror(f)
      val deps = depsOf(lhs).map(f(_))

      dbg(c"  rhs2 = $rhs2")
      dbg(c"  effects = $effects")
      dbg(c"  deps = $deps")

      val symsWithSameDef = state.defCache.getOrElse(rhs2, Nil) intersect state.context
      val symsWithSameEffects = symsWithSameDef.find{case Effectful(u2, es) => u2 == effects && es == deps }

      dbg(c"  def cache: ${state.defCache.getOrElse(rhs2,Nil)}")
      dbg(c"  context:")
      state.context.foreach{s => dbg(c"    ${str(s)} [effects = ${effectsOf(s)}, deps = ${depsOf(s)}]")}
      dbg(c"  syms with same def: $symsWithSameDef")
      dbg(c"  syms with same effects: $symsWithSameEffects")

      symsWithSameEffects match {
        case Some(lhs2) =>
          lhs2.addCtx(ctx)
          // Dispatch doesn't necessarily need to be defined yet
          dispatchOf.get(lhs,reg) match {
            case Some(dups) => removeDuplicates(f(reg), dups diff dispatchOf(lhs2, f(reg)))
            case None => // No action
          }
          lhs2.asInstanceOf[Exp[T]]

        case None =>
          val lhs2 = mirror(lhs,rhs)
          getDef(lhs2).foreach{d => state.defCache += d -> syms(lhs2).toList }
          lhs2
      }

    case _ if isInnerControl(lhs) =>
      dbgs(str(lhs))
      inInner{ super.transform(lhs,rhs) }
    case _ =>
      dbgs(str(lhs))
      super.transform(lhs,rhs)
  }
}
