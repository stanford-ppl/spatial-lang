package spatial.transform.unrolling

import argon.analysis._
import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.banking._
import spatial.lang.Math
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

case class UnrollingTransformer(var IR: State) extends UnrollingBase { self =>
  override val name = "Unrolling Transformer"

  /**
    * "Pachinko" rules:
    *
    * Given a memory access which depends on indices i1 ... iN,
    * and some parallelization factors P1 ... PN associated with each index,
    *
    *
    * When a new memory access is unrolled:
    *   1. Check other unrolled accesses which have the same original symbol
    *   2.a. Look up all loop iterators used to compute the indices for this access
    *   2.b. Look up the unroll number for each of these iterators (-1 for random accesses)
    *   3. The duplicate of the access is the number of accesses which have already been unrolled with the same numbers
    *   4. Update list of accesses to include current's numbers
    */
  var pachinko = Map[(Exp[_],Exp[_],Int), Seq[Seq[Int]]]()

  def registerAccess(original: Exp[_], unrolled: Exp[_] /*, pars: Option[Seq[Int]]*/): Unit = {
    val reads = unrolled match { case LocalReader(rds) => rds.map(_._1); case _ => Nil }
    val resets = unrolled match { case LocalResetter(rsts) => rsts.map(_._1); case _ => Nil }
    val writes = unrolled match { case LocalWriter(wrts) => wrts.map(_._1); case _ => Nil }
    //val accesses = reads ++ writes

    val unrollInts: Seq[Int] = accessPatternOf.get(original).map{patterns =>
      patterns.flatMap{
        case AffineAccess(prods,_) => prods.map{prod => unrollNum.getOrElse(prod.i, -1) }
        case _ => Seq(-1)
      }
    }.getOrElse(Seq(-1))

    // Total number of address channels needed
    val channels = accessWidth(unrolled)

    // For each memory this access reads, set the new dispatch value
    reads.foreach{mem =>
      dbgs(u"Registering read of $mem: " + c"$original -> $unrolled")
      dbgs(c"  Channels: $channels")
      dbgs(c"  ${str(original)}")
      dbgs(c"  ${str(unrolled)}")
      dbgs(c"  Unroll numbers: $unrollInts")
      val uaccess = (unrolled, unrollInts)
      val dispatchers = dispatchOf.get(uaccess, mem)

      if (dispatchers.isEmpty) {
        warn(c"Memory $mem had no dispatchers for $unrolled")
        warn(c"(This is likely a compiler bug where an unused memory access was not removed)")
      }

      dispatchers.foreach{origDispatches =>
        if (origDispatches.size != 1) {
          bug(c"Readers should have exactly one dispatch, $original -> $unrolled had ${origDispatches.size}")
          bug(original.ctx)
        }
        val dispatches = origDispatches.flatMap{orig =>
          dbgs(c"  Dispatch #$orig: ")
          dbgs(c"    Previous unroll numbers: ")
          val others = pachinko.getOrElse( (original,mem,orig), Nil)

          val banking = duplicatesOf(mem).apply(orig).banking.map(_.nBanks)

          // Address channels taken care of by banking
          val bankedChannels = if (!isAccessWithoutAddress(original)) {
            accessPatternOf.get(original).map{ patterns =>
              dbgs(c"  Access pattern $patterns")
              if (patterns.exists(_.isGeneral)) {
                // TODO: Not sure if this is even remotely correct...
                // (unrollFactorsOf(original) diff unrollFactorsOf(mem)).flatten.map{case Exact(c) => c.toInt }

                var used: Set[Exp[Index]] = Set.empty
                def bankFactor(i: Exp[Index]): Int = {
                  if (!used.contains(i)) {
                    used += i
                    parFactorOf(i) match {case Exact(c) => c.toInt }
                  }
                  else 1
                }

                patterns.zip(banking).map{
                  case (GeneralAffine(prods,_),actualBanks) =>
                    val requiredBanks = prods.map(p => bankFactor(p.i) ).product
                    java.lang.Math.min(requiredBanks, actualBanks)

                  case (pattern, actualBanks) => pattern.index match {
                    case Some(i) =>
                      val requiredBanking = bankFactor(i)
                      java.lang.Math.min(requiredBanking, actualBanks)
                    case None => 1
                  }
                }
              }
              else {
                val iters = patterns.map(_.index)
                iters.distinct.map{
                  case x@Some(i) =>
                    dbgs(c"      Index: $i")
                    val requiredBanking = parFactorOf(i) match {case Exact(p) => p.toInt }
                    val actualBanking = banking(iters.indexOf(x))
                    dbgs(c"      Actual banking: $actualBanking")
                    dbgs(c"      Required banking: $requiredBanking")
                    java.lang.Math.min(requiredBanking, actualBanking) // actual may be higher than required, or vice versa
                  case None => 1
                }
              }
            }.getOrElse(Seq(1))
          }
          else {
            banking
          }

          val banks = bankedChannels.product
          val duplicates = (channels + banks - 1) / banks // ceiling(channels/banks)

          dbgs(c"    Bankings: $banking")
          dbgs(c"    Banked Channels: $bankedChannels ($banks)")
          dbgs(c"    Duplicates: $duplicates")

          others.foreach{other => dbgs(c"      $other") }

          val dispatchStart = orig + duplicates * others.count{p => p == unrollInts }
          pachinko += (original,mem,orig) -> (unrollInts +: others)

          val dispatches = List.tabulate(duplicates){i => dispatchStart + i}.toSet

          dbgs(c"    Setting new dispatch values: $dispatches")

          //dispatchOf(unrolled, mem) = Set(dispatch)
          dispatches.foreach{d => portsOf(unrolled, mem, d) = portsOf(unrolled, mem, orig) }
          dispatches
        }

        if (dispatches.isEmpty) {
          dbg(c"Dispatches created for $unrolled on memory $mem (dispatches: $origDispatches) was empty")
          bug(unrolled.ctx, c"Dispatches created for $unrolled on memory $mem (dispatches: $origDispatches) was empty.")
          bug(c"${str(unrolled)}")
        }

        dispatchOf(unrolled, mem) = dispatches
      }
    }
  }

  override def transform[A:Type](lhs: Sym[A], rhs: Op[A])(implicit ctx: SrcCtx): Exp[A] = (rhs match {
    case e:Hwblock =>
      inHwScope = true
      val lhs2 = super.transform(lhs,rhs)
      inHwScope = false
      lhs2
    case e:OpForeach        => unrollForeachNode(lhs, e)
    case e:OpReduce[_]      => unrollReduceNode(lhs, e)
    case e:OpMemReduce[_,_] => unrollMemReduceNode(lhs, e)
    case _ => super.transform(lhs, rhs)
  }).asInstanceOf[Exp[A]]
















}
