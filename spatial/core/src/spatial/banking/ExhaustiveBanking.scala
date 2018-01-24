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
    val a2 = (0 until N).filter(x => isPow2(x) || x == 1 || x == 0)
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
    Alphas2(1, Nil).filterNot(_.forall(_ == 0)) ++ AlphasX(1, Nil).filterNot(_.forall(_ == 0))
  }

  def checkConflicts(grp: Seq[AccessPair])(func: (Matrix,Matrix,Seq[Int],Seq[Int]) => Boolean): Boolean = {
    grp.indices.forall{i =>
      val m0 = grp(i)
      (i+1 until grp.size).forall{j =>
        val m1 = grp(j)
        func(m0.a, m1.a, m0.c, m1.c)
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
    * This has a worst case runtime of O[A**(rank+3)], where A is max parallel accesses to mem
    */
  final val Bs = Seq(2, 4, 8, 16, 32, 64, 128, 256) ++ (3 until 256).filterNot(isPow2)
  def findBanking(grps: Seq[Seq[AccessPair]], dims: Seq[Int], dC: String, dBC: String): ModBanking = {
    val rank = dims.length
    if (grps.isEmpty) return ModBanking.Unit(dims.length)

    //    if (grps.isEmpty) throw new Exception("Cannot find banking for empty groups!")
    val Nmin = grps.map(_.size).max
    val (n2,nx) = (Nmin to 8*Nmin).partition(x => isPow2(x) || x == 1)
    // TODO: Magic number. What should the cutoff point be here, if any?
    val n2Head = if (n2.head.toDouble/Nmin > 1.4) Seq(Nmin) else Nil
    val Ns = (n2Head ++ n2 ++ nx).iterator

    dbg(s"      Nmin = $Nmin")
    dbg("")
    //dbg(s"[Result]")
    grps.zipWithIndex.foreach{case (grp,i) =>
      dbg(s"      Group $i: ")
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
        dbg(s"        N=$N, B=1, alpha=$alpha")
        if (grps.forall{grp => checkCyclic(N, alpha,grp,dC)}) {
          banking = Some(ModBanking(N, 1, alpha, dims))
        }
        else {
          val B = Bs.find{b =>
            dbg(s"        N=$N, B=$b, alpha=$alpha")
            grps.forall{grp => checkBlockCyclic(N, b, alpha, grp,dBC) }
          }
          banking = B.map{b => ModBanking(N, b, alpha, dims) }
        }
      }
    }

    banking.getOrElse(ModBanking.Unit(dims.length))
  }

  // TODO: This may not be correct in more complex cases!
  // Checks if this banking strategy is legal - assumes this is the case when total # of banks >= # parallel accesses
  def isValidBanking(banking: Seq[ModBanking], reads: Seq[Set[AccessMatrix]], writes: Seq[Set[AccessMatrix]]): Boolean = {
    val banks = banking.map(_.nBanks).product
    reads.forall(_.size <= banks) && writes.forall(_.size <= banks)
  }

  def bankAccesses(mem: Exp[_], dims: Seq[Int], reads: Seq[Set[AccessMatrix]], writes: Seq[Set[AccessMatrix]], domain: IndexDomain, dimGrps: Seq[Seq[Seq[Int]]]): Seq[Seq[ModBanking]] = {
    val cyclicIndexBounds = domain.map{a => Constraint(1, Vector(a.dropRight(1) ++ Array(0, a.last))) }
    val blockCyclicIndexBounds = domain.map{a => Constraint(1, Vector(a.dropRight(1) ++ Array(0, 0, a.last))) }
    val baseCols = domain.nCols + 1 // Account for the Type column

    val cyclicIndexStr = cyclicIndexBounds.map(_.toString).mkString("\n")
    val dC = s"${cyclicIndexBounds.length+1} ${baseCols + 1}\n$cyclicIndexStr\n" // Adds K

    val blockCyclicIndexStr = blockCyclicIndexBounds.map(_.toString).mkString("\n")
    val dBC = s"${blockCyclicIndexBounds.length+4} ${baseCols + 2}\n$blockCyclicIndexStr\n"   // Adds K0 and K1

    val groups: Seq[Set[AccessMatrix]] = reads ++ writes

    dbg("  Attempting to find banking with access matrix groups: ")
    groups.zipWithIndex.foreach{case (grp,i) =>
      dbg(s"    Group #$i: ")
      grp.foreach{m => m.printWithTab("      ") }
    }

    if (groups.forall(_.size <= 1)) {
      // Only a single bank needed, since only 1 access per group
      // Note this case is required for banking random accesses
      // TODO: Assumes random accesses should never be grouped together; we assume they can never be banked together
      Seq(Seq(ModBanking(1, 1, List.fill(dims.length){1}, List.tabulate(dims.length){i => i})))
    }
    else {
      val options = dimGrps.flatMap { strategy =>
        dbg(s"  Trying dimension strategy $strategy")
        // Split up accesses based on the dimension groups in this banking strategy
        // Then, for each dimension group, determine the banking
        val banking = strategy.map { dimensions => // Set of dimensions to bank together
          //dbg(s"    Creating affine access pairs..")
          val accesses = groups.map{grp =>
            // Convert all AccessMatrix instances into pairs of (Matrix, Offset Vector)
            grp.toSeq.flatMap{access => access.getAccessPairsIfAffine(dimensions) }.distinct
          }
          dbg(s"    Dimensions: $dimensions")
          findBanking(accesses, dimensions, dC, dBC)
        }
        if (isValidBanking(banking,reads,writes)) Some(banking) else None
      }

      // TODO: Replace with cost model
      // TODO: What to do in the case where banking fails? Fall back to duplication/time multiplexing?
      /*options.headOption.getOrElse {
        dbg(c"BANKING FAILED")
        bug(mem.ctx, c"Could not bank reads of memory $mem: ")
        reads.flatten.map(_.access).foreach { rd => bug(s"  ${str(rd.node)}") }
        bug(mem.ctx)
        Nil
      }*/
      options
    }
  }

}
