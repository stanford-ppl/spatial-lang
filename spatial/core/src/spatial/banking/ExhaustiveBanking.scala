package spatial.banking

import argon.analysis._
import argon.core._
import argon.util._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ExhaustiveBanking extends BankingStrategy {
  this: MemoryConfigurer =>

  protected var cyclicIndexBounds: String = ""
  protected var blockCyclicIndexBounds: String = ""

  /**
    * Register all of the indices for all associated accesses.
    * Attempt to find the minimum and maximum value for each counter index, allowing
    * for the min/max to be affine functions of other counter values.
    * If the function is not analyzable, assume Int.min or Int.max for min and max, respectively.
    */
  def registerIndices(indices: Seq[Exp[Index]]): Unit = {
    val idxBnds = indices.zipWithIndex.map{case (i,iIdx) =>
      def sparseBound(i: Option[IndexPattern], default: Int) = i match {
        case Some(BankableAffine(as,is,b)) => DenseBoundVector(as,is,b).sparsify(indices)
        case _ => DenseBoundVector(Array.empty,Nil,default).sparsify(indices)
      }
      val (min,max) = ctrOf(i) match {
        case Some(ctr) =>
          val min = sparseBound(accessPatternOf(counterStart(ctr)).headOption, Integer.MIN_VALUE)
          val max = sparseBound(accessPatternOf(counterEnd(ctr)).headOption, Integer.MAX_VALUE)
          (min,max)
        case _ => (sparseBound(None,Integer.MIN_VALUE), sparseBound(None, Integer.MAX_VALUE))
      }
      val constraintMin = min.as.zipWithIndex.map{case (x,d) => if (d == iIdx) 1 else -x} :+ min.b
      val constraintMax = max.as :+ max.b
      constraintMax(iIdx) = - 1
      (constraintMin, constraintMax)
    }
    val cyclic = idxBnds.flatMap{case (min,max) =>
      Seq(s"""1 ${min.mkString(" ")} 0""", s"""1 ${max.mkString(" ")} 0""") // 0 in the K column
    }
    val blockCyclic = cyclic.map{line => line + " 0" } // 0 in the K1 column too

    cyclicIndexBounds = cyclic.mkString("\n")
    blockCyclicIndexBounds = blockCyclic.mkString("\n")
  }


  /**
    * Generates an iterator over all vectors of given rank, with values up to N
    * Prioritizes vectors which are entirely powers of 2 first
    * Note that the total size is O(N**rank)
    */
  def Alphas(rank: Int, N: Int): Iterator[Seq[Int]] = {
    val a2 = (1 until N).filter(isPow2)
    def Alphas2(dim: Int, prev: Seq[Int]): Iterator[Seq[Int]] = {
      if (dim < rank) {
        a2.iterator.flatMap{aD => Alphas2(dim+1, prev :+ aD) }
      }
      else a2.iterator.map{aR => prev :+ aR }
    }
    def AlphasX(dim: Int, prev: Seq[Int]): Iterator[Seq[Int]] = {
      if (dim < rank) {
        (0 until N).iterator.flatMap{aD => AlphasX(dim+1, prev :+ aD) }
      }
      else (0 until N).iterator.map{aR => prev :+ aR }.filterNot(_.forall(isPow2))
    }
    Alphas2(1, Nil) ++ AlphasX(1, Nil)
  }



  def checkConflicts(grp: Seq[AccessPair])(func: (Matrix,Matrix,Array[Int],Array[Int]) => Boolean): Boolean = {
    // subsets(2).forall is a bit simpler, but that instantiates many Sets of 2 elements
    grp.indices.forall{i =>
      val (a0,c0) = grp(i)
      (i+1 until grp.size).forall{j =>
        val (a1,c1) = grp(j)
        func(a0,a1,c0,c1)
      }
    }
  }

  def sum(n: Int)(func: Int => Int): Int = {
    var acc: Int = 0
    (0 until n).foreach{i => acc += func(i) }
    acc
  }

  def checkCyclic(N: Int, alpha: Seq[Int], grp: Seq[AccessPair]): Boolean = checkConflicts(grp){(a0,a1,c0,c1) =>
    val row0_is = Array.tabulate(a0.cols){j => sum(a0.rows){i => alpha(i)*(a0(i,j) - a1(i,j))} }   // alpha*(A0 - A1)
    val row0_c  = sum(a0.rows){i => alpha(i)*(c0(i) - c1(i)) }                                     // alpha*(C0 - C1)
    val row0_K  = N

    val row1_is = row0_is.map{x => -x}
    val row1_c  = -row0_c
    val row1_K  = -row0_K
    // TODO: Call emptiness test here
    false
  }

  def checkBlockCyclic(N: Int, B: Int, alpha: Seq[Int], grp: Seq[AccessPair]): Boolean = checkConflicts(grp){(a0,a1,c0,c1) =>
    val row2_is = Array.tabulate(a0.cols){j => sum(a0.rows){i => alpha(i) * a0(i,j) }}   // alpha * A0
    val row2_c  = sum(a0.rows){i => alpha(i)*c0(i) }                                     // alpha * C0
    val row2_K0 = -B*N
    val row2_K1 = -B

    val row3_is = Array.tabulate(a0.cols){j => sum(a0.rows){i => alpha(i) * a1(i,j) }}   // alpha * A1
    val row3_c  = sum(a1.rows){i => alpha(i)*c1(i) }                                     // alpha * C1
    val row3_K0 = 0
    val row3_K1 = -B

    val row0_is = row2_is.map{x => -x } // -alpha * A0
    val row0_c  = -row2_c + B - 1       // -alpha * C0
    val row0_K0 = B*N                   // B*N
    val row0_K1 = B

    val row1_is = row3_is.map{x => -x}  // -alpha * A1
    val row1_c  = -row3_c + B - 1       // -alpha * C1 + B - 1
    val row1_K0 = 0
    val row1_K1 = B

    // TODO: Call emptiness test here
    false
  }

  /**
    * TODO: Absolutely need a better exploration method here!
    * This has a worst case runtime of O[A**(rank+3)], where A is max parallel accesses to mem, rank is # of dimensions
    */
  final val Bs = Seq(2, 4, 8, 16, 32, 64, 128, 256) ++ (3 until 256).filterNot(isPow2)
  def findBanking(rank: Int, grps: Seq[Seq[AccessPair]]): Option[ModBanking] = {
    val Nmin = grps.map(_.size).max
    val (n2,nx) = (Nmin to 8*Nmin).partition(isPow2)
    val Ns = (n2 ++ nx).iterator

    var banking: Option[ModBanking] = None

    // O( #Accesses)
    while (Ns.hasNext && banking.isEmpty) {
      val N = Ns.next()
      // O( #Accesses ^ rank )
      val As = Alphas(rank, N)
      while (As.hasNext && banking.isEmpty) {
        val alpha = As.next
        // O( 256 * #Accesses^2)
        if (grps.forall(grp => checkCyclic(N, alpha,grp))) {
          banking = Some(ModBanking(N, 1, alpha))
        }
        else {
          val B = Bs.find{b => grps.forall(grp => checkBlockCyclic(N, b, alpha, grp)) }
          banking = B.map{b => ModBanking(N, b, alpha) }
        }
      }
    }

    banking
  }

  def bankAccesses(reads: Set[AccessMatrix], writes: Set[AccessMatrix]): Seq[ModBanking] = {
    val accessGrps: Seq[Set[AccessMatrix]] = reads.groupBy(_.access.ctrl).toSeq.map(_._2) ++
                                             writes.groupBy(_.access.ctrl).toSeq.map(_._2)

    val dimIndices = dims.indices
    val hierarchical = dimIndices.map{d => Seq(d) }   // Fully hierarchical (each dimension has separate bank addr.)
    val fullDiagonal = Seq(dimIndices)                // Fully diagonal (all dimensions contribute to single bank addr.)
    val strategies = Seq(hierarchical, fullDiagonal)  // TODO: try all possible dimension orderings?
    val options = strategies.flatMap{strategy =>
      // Split up accesses based on the dimension groups in this banking strategy
      // Then, for each dimension group, determine the banking
      val banking = strategy.map{dimensions =>        // Set of dimensions to bank together
        val accesses = accessGrps.map{grp =>
          // Slice out the dimensions from this each access in this group
          val slice: Set[AccessMatrix] = grp.map{a => a.takeDims(dimensions) }
          // Only keep accesses which do not contain a random access
          val linearOnly = slice.filterNot(_.exists{case _:UnrolledRandomVector => true; case _ => false})
                                .map(_.asInstanceOf[Seq[UnrolledAffineVector]])
          // Convert all purely affine accesses to matrices + offset vectors
          linearOnly.map{access =>
            val a = Matrix(access.map{row => row.as}.toArray)
            val c = access.map{row => row.b}.toArray
            (a,c) : AccessPair
          }.toSeq
        }
        findBanking(dims.length, accesses)
      }
      if (banking.forall(_.isDefined)) Some(banking.map(_.get)) else None
    }

    // TODO: Replace with cost model
    // TODO: What to do in the case where banking fails? Fall back to duplication/time multiplexing?
    options.headOption.getOrElse{
      bug(mem.ctx, c"Could not bank reads of memory $mem: ")
      reads.map(_.access).foreach{rd => bug(s"  ${str(rd.node)}") }
      bug(mem.ctx)
      Nil
    }
  }

}
