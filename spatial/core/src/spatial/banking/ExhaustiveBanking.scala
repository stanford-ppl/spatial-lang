package spatial.banking

import argon.core._
import argon.util._
import spatial.poly._
import spatial.utils._
import spatial.Subproc

class ExhaustiveBanking()(implicit val IR: State) extends BankingStrategy {

  /**
    * Generates an iterator over all vectors of given rank, with values up to N
    * Prioritizes vectors which are entirely powers of 2 first
    * Note that the total size is O(N**rank)
    */
  def Alphas(rank: Int, N: Int): Iterator[Seq[Int]] = {
    val a2 = (1 until N).filter(x => isPow2(x) || x == 1)
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
      else (0 until N).iterator.map{aR => prev :+ aR }.filterNot(_.forall(x => isPow2(x) || x == 1))
    }
    Alphas2(1, Nil) ++ AlphasX(1, Nil)
  }

  def checkConflicts(grp: Seq[AccessPair])(func: (Matrix,Matrix,Array[Int],Array[Int]) => Boolean): Boolean = {
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

  def checkCyclic(N: Int, alpha: Seq[Int], grp: Seq[AccessPair], dC: String): Boolean = checkConflicts(grp){(a0,a1,c0,c1) =>
    val row0_is = Array.tabulate(a0.cols){j => sum(a0.rows){i => alpha(i)*(a0(i,j) - a1(i,j))} }   // alpha*(A0 - A1)
    val row0_c  = sum(a0.rows){i => alpha(i)*(c0(i) - c1(i)) }                                     // alpha*(C0 - C1)
    val row0_K  = N

    //val row0 = Constraint(0, Vector(row0_is ++ Array(row0_K, row0_c)))  // Equality constraint ( = 0)
    val row0 = s"""0 ${row0_is.mkString(" ")} $row0_K $row0_c"""
    val mat = dC + row0
    Polytope.isEmpty(mat)
  }

  def checkBlockCyclic(N: Int, B: Int, alpha: Seq[Int], grp: Seq[AccessPair], dBC: String): Boolean = checkConflicts(grp){(a0,a1,c0,c1) =>
    val row2_is = Array.tabulate(a0.cols){j => sum(a0.rows){i => alpha(i) * a0(i,j) }}   // alpha * A0
    val row2_c  = sum(a0.rows){i => alpha(i)*c0(i) }                                     // alpha * C0
    val row2_K0 = -B*N
    val row2_K1 = -B
    val row2 = s"""0 ${row2_is.mkString(" ")} $row2_K0 $row2_K1 $row2_c"""

    val row3_is = Array.tabulate(a0.cols){j => sum(a0.rows){i => alpha(i) * a1(i,j) }}   // alpha * A1
    val row3_c  = sum(a1.rows){i => alpha(i)*c1(i) }                                     // alpha * C1
    val row3_K0 = 0
    val row3_K1 = -B
    val row3 = s"""0 ${row3_is.mkString(" ")} $row3_K0 $row3_K1 $row3_c"""

    val row0_is = row2_is.map{x => -x } // -alpha * A0
    val row0_c  = -row2_c + B - 1       // -alpha * C0
    val row0_K0 = B*N                   // B*N
    val row0_K1 = B
    val row0 = s"""0 ${row0_is.mkString(" ")} $row0_K0 $row0_K1 $row0_c"""

    val row1_is = row3_is.map{x => -x}  // -alpha * A1
    val row1_c  = -row3_c + B - 1       // -alpha * C1 + B - 1
    val row1_K0 = 0
    val row1_K1 = B
    val row1 = s"""0 ${row1_is.mkString(" ")} $row1_K0 $row1_K1 $row1_c"""

    val mat = dBC + s"$row0\n$row1\n$row2\n$row3"
    //val row0 = Constraint(0, Vector(row0_is ++ Array(row0_K0, row0_K1, row0_c)))
    //val row1 = Constraint(0, Vector(row1_is ++ Array(row1_K0, row1_K1, row1_c)))
    Polytope.isEmpty(mat)
  }

  /**
    * TODO: Absolutely need a better exploration method here!
    * This has a worst case runtime of O[A**(rank+3)], where A is max parallel accesses to mem, rank is # of dimensions
    */
  final val Bs = Seq(2, 4, 8, 16, 32, 64, 128, 256) ++ (3 until 256).filterNot(isPow2)
  def findBanking(rank: Int, grps: Seq[Seq[AccessPair]], dims: Seq[Int], dC: String, dBC: String): Option[ModBanking] = {
    if (grps.isEmpty) throw new Exception("Cannot find banking for empty groups!")

    val Nmin = grps.map(_.size).max
    val (n2,nx) = (Nmin to 8*Nmin).partition(x => isPow2(x) || x == 1)
    val Ns = (n2 ++ nx).iterator

    dbg(s"    FIND BANKING: Nmin = $Nmin")
    dbg("")
    //dbg(s"[Result]")
    grps.zipWithIndex.foreach{case (grp,i) =>
      dbg(s"    Group $i: ")
      grp.foreach{pair => pair.printWithTab("      ") }
    }

    var banking: Option[ModBanking] = None

    // O( #Accesses)
    while (Ns.hasNext && banking.isEmpty) {
      val N = Ns.next()
      // O( #Accesses ^ rank )
      val As = Alphas(rank, N)
      while (As.hasNext && banking.isEmpty) {
        val alpha = As.next
        // O( 256 * #Accesses^2)
        dbg(s"      N=$N, B=1, alpha=$alpha")
        if (grps.forall{grp => checkCyclic(N, alpha,grp,dC)}) {
          banking = Some(ModBanking(N, 1, alpha, dims))
        }
        else {
          val B = Bs.find{b =>
            dbg(s"      N=$N, B=$b, alpha=$alpha")
            grps.forall{grp => checkBlockCyclic(N, b, alpha, grp,dBC) }
          }
          banking = B.map{b => ModBanking(N, b, alpha, dims) }
        }
      }
    }

    banking
  }

  def bankAccesses(mem: Exp[_], dims: Seq[Int], reads: Seq[Set[AccessMatrix]], writes: Seq[Set[AccessMatrix]], domain: IndexDomain): Seq[ModBanking] = {
    val cyclicIndexBounds = domain.map{a => Constraint(1, Vector(a.dropRight(1) ++ Array(0, a.last))) }
    val blockCyclicIndexBounds = domain.map{a => Constraint(1, Vector(a.dropRight(1) ++ Array(0, 0, a.last))) }
    val nIters = domain.headOption.map(_.length - 1).getOrElse(0)

    val cyclicIndexStr = cyclicIndexBounds.map(_.toString).mkString("\n")
    val dC = s"${cyclicIndexBounds.length+1} ${nIters + 3}\n$cyclicIndexStr\n"

    val blockCyclicIndexStr = blockCyclicIndexBounds.map(_.toString).mkString("\n")
    val dBC = s"${blockCyclicIndexBounds.length+4} ${nIters + 4}\n$blockCyclicIndexStr\n"

    val groups: Seq[Set[AccessMatrix]] = reads ++ writes

    dbg("  Attempting to find banking with access matrix groups: ")
    groups.zipWithIndex.foreach{case (grp,i) =>
      dbg(s"    Group #$i: ")
      grp.foreach{m => m.printWithTab("      ") }
    }

    val dimIndices = dims.indices
    val hierarchical = dimIndices.map{d => Seq(d) }   // Fully hierarchical (each dimension has separate bank addr.)
    val fullDiagonal = Seq(dimIndices)                // Fully diagonal (all dimensions contribute to single bank addr.)
    // TODO: try other/all possible dimension orderings?
    val strategies = if (dims.length > 1) Seq(hierarchical, fullDiagonal) else Seq(hierarchical)

    if (groups.forall(_.size <= 1)) {
      // Only a single bank needed, since only 1 access per group
      // Note this case is required for banking random accesses
      // TODO: Assumes random accesses should never be grouped together; we assume they can never be banked together
      Seq(ModBanking(1, 1, List.fill(dims.length){1}, List.tabulate(dims.length){i => i}))
    }
    else {
      val options = strategies.flatMap { strategy =>
        dbg(s"  Trying dimension strategy $strategy")
        // Split up accesses based on the dimension groups in this banking strategy
        // Then, for each dimension group, determine the banking
        val banking = strategy.map { dimensions => // Set of dimensions to bank together
          //dbg(s"    Creating affine access pairs..")
          val accesses = groups.zipWithIndex.map { case (grp, i) =>
            // Convert all AccessMatrix instances into pairs of (Matrix, Offset Vector)
            grp.toSeq.flatMap { access =>
              //access.printWithTab("      ")
              access.getAccessPairsIfAffine(dimensions)
            }
          }
          dbg(s"    Dimension group: $dimensions")
          findBanking(dims.length, accesses, dimensions, dC, dBC)
        }
        if (banking.forall(_.isDefined)) Some(banking.map(_.get)) else None
      }

      // TODO: Replace with cost model
      // TODO: What to do in the case where banking fails? Fall back to duplication/time multiplexing?
      options.headOption.getOrElse {
        dbg(c"BANKING FAILED")
        bug(mem.ctx, c"Could not bank reads of memory $mem: ")
        reads.flatten.map(_.access).foreach { rd => bug(s"  ${str(rd.node)}") }
        bug(mem.ctx)
        Nil
      }
    }
  }

}
