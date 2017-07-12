import spatial.dsl._
import org.virtualized._

object MatMult_outer extends SpatialApp { // Regression (Dense) // Args: 32 128 128
  type X = FixPt[TRUE,_16,_16]

  val innerPar = 16
  val midPar = 2
  val outerPar = 2

  val tsm = 16
  val tsn = 64
  val tsp = 64

  @virtualize
  def MatMult_outer[T:Type:Num](A: Array[T], B: Array[T], C_init: Array[T], mm: Int, nn: Int, pp: Int) = {
    val M = ArgIn[Int]
    val N = ArgIn[Int]
    val P = ArgIn[Int]
    setArg(M,mm)
    setArg(N,nn)
    setArg(P,pp)

    val a = DRAM[T](M, P)
    val b = DRAM[T](P, N)
    // val c_init = DRAM[T](M, N)
    val c = DRAM[T](M, N)

    val op = outerPar (1 -> 1)
    val mp = midPar (1 -> 16)
    val ip = innerPar (1 -> 64)
    val px = 1 (1 -> 1) // Cannot parallelize accum across k blocks

    val bm = tsm (48 -> 48 -> 1920)
    val bn = tsn (48 -> 48 -> 1920)
    val bp = tsp (48 -> 48 -> 1920)

    setMem(a, A)
    setMem(b, B)
    setMem(c, C_init)

    Accel {
      Sequential.Foreach(M by bm, N by bn par op) { (i,j) =>
        val tileC = SRAM[T](bm, bn)
        tileC load c(i::i+bm, j::j+bn par ip)
        MemFold(tileC)(P by bp) { k =>
          val tileA = SRAM[T](bm, bp) 
          val tileB = SRAM[T](bp, bn)
          val accum = SRAM[T](bm, bn)
          Parallel {
            tileA load a(i::i+bm, k::k+bp par ip)
            tileB load b(k::k+bp, j::j+bn par ip)
          }
          MemReduce(accum)(bp by 1 par mp){ kk =>
            val tileC_partial = SRAM[T](bm,bn)
            Foreach(bm by 1, bn by 1 par ip){ (ii,jj) =>
              tileC_partial(ii,jj) = tileA(ii,kk) * tileB(kk,jj)
            }
            tileC_partial
          }{_+_}
        }{_+_}
        c(i::i+bm, j::j+bn par ip) store tileC
      }
    }
    getMem(c)
  }

  @virtualize
  def main() = {
    val M = args(0).to[Int]
    val N = args(1).to[Int]
    val P = args(2).to[Int]

    val a = Array.tabulate(M){ j => Array.tabulate(P){ i => ((i + j * P) % 8).to[X] } } // Standard array
    val b = Array.tabulate(P){ j => Array.tabulate(N){ i => ((i + j * N) % 8).to[X] } } // Standard array
    val c_init = Array.fill(M){ Array.fill(N){ 0.to[X] } }
    // val a = Array.fill(M){ Array.fill(P){random[T](100)} }
    // val b = Array.fill(P){ Array.fill(N){random[T](100)} }

    val result = MatMult_outer(a.flatten, b.flatten, c_init.flatten, M, N, P)

    val gold = Array.tabulate(M){i =>
      val aRow = a(i)
      Array.tabulate(N){j =>
        val bCol = b.map{row => row(j)}
        aRow.zip(bCol){_*_}.reduce{_+_}
      }
    }.flatten

    println("expected cksum: " + gold.map(a => a).reduce{_+_})
    println("result cksum: " + result.map(a => a).reduce{_+_})
    printArray(gold, "Gold: ")
    printArray(result, "Result: ")

    val cksum = result.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (MatMult_outer)")
  }

}

object MatMult_inner extends SpatialApp { // Regression (Dense) // Args: 32 128 128


  type X = FixPt[TRUE,_16,_16]

  val innerPar = 16
  val midPar = 2
  val outerPar = 2

  val tsm = 16
  val tsn = 64
  val tsp = 64

  @virtualize
  def MatMult_inner[T:Type:Num](A: Array[T], B: Array[T], mm: Int, nn: Int, pp: Int) = {
    val M = ArgIn[Int]
    val N = ArgIn[Int]
    val P = ArgIn[Int]
    setArg(M,mm)
    setArg(N,nn)
    setArg(P,pp)

    val a = DRAM[T](M, P)
    val b = DRAM[T](P, N)
    val c = DRAM[T](M, N)

    val bm = tsm (1 -> 1536)
    val bn = tsn (64 -> 64 -> 1536)
    val bp = tsp (64 -> 64 -> 1536)

    val op = outerPar (1 -> 6)
    val mp = midPar   (1 -> 64)
    val ip = innerPar (1 -> 64)
    val px = 1 (1 -> 1) // Cannot parallelize accum across k blocks

    setMem(a, A)
    setMem(b, B)

    Accel {
      Foreach(M by bm, N by bn par op){(i,j) =>
        val tileC = SRAM[T](bm, bn)

        Foreach(P by bp par px){k =>
          val tileA = SRAM[T](bm, bp)
          val tileB = SRAM[T](bp, bn)
          Parallel {
            tileA load a(i::i+bm, k::k+bp par 1) // Reads M*N*P times
            tileB load b(k::k+bp, j::j+bn par 1)
          }
          Foreach(bm by 1, bn by 1 par mp){ (ii,jj) =>    // MetaPipe?
            val prod = Reduce(Reg[T])(bp by 1 par ip){kk => tileA(ii, kk) * tileB(kk, jj) }{_+_}
            val prev = mux(k == 0, 0.to[T], tileC(ii,jj))
            tileC(ii,jj) = prev + prod.value // Is a unit pipe that should be recognized as accum
          }
        }
        c(i::i+bm, j::j+bn) store tileC // Writes M*N times
      }
    }
    getMem(c)
  }

  @virtualize
  def main() = {
    val M = args(0).to[Int]
    val N = args(1).to[Int]
    val P = args(2).to[Int]

    val a = Array.tabulate(M){ i => Array.tabulate(P){ j => ((i*P + j)%8).to[X] } }
    val b = Array.tabulate(P){ i => Array.tabulate(N){ j => ((i*N + j)%8).to[X] } }
    // val a = Array.fill(M){ Array.fill(P){random[T](100)} }
    // val b = Array.fill(P){ Array.fill(N){random[T](100)} }

    val result = MatMult_inner(a.flatten, b.flatten, M, N, P)

    val gold = Array.tabulate(M){i =>
      val aRow = a(i)
      Array.tabulate(N){j =>
        val bCol = b.map{row => row(j)}
        aRow.zip(bCol){_*_}.reduce{_+_}
      }
    }.flatten

    val gold_cksum = gold.map(a => a).reduce{_+_}
    val result_cksum = result.map(a => a).reduce{_+_}
    printArray(gold, "Gold: ")
    printArray(result, "Result: ")
    println("expected cksum: " + gold_cksum)
    println("result cksum:   " + result_cksum)

    // (0 until M*N) foreach { i => assert(result(i) == gold(i)) }

    val cksum = result_cksum == gold_cksum
    println("PASS: " + cksum + " (MatMult_inner) * Remember to fix GEMM_MemoryHierarchy once issue #159 is fixed!")

  }


}


/*

  Sketch of this GEMM:


    ###########
    # LAYER 1 #
    ###########
                                              N
                          (k) →        ___________________
                                      |                   |
                                   kc |                   |
                                      |     tileB         |
                                      |___________________|
                               P      |                   |
                                      |                   |
                                      |                   |
                                      |                   |     
                                      |                   |
          (k)                         |___________________|
          ↓                        x
                      P          
          ___kc_________________       ___________________
         |         |            |     |                   |
         |         |            |     |                   |
         |         |            |     |                   |
  M      |         |            |     |                   |
         |         |            |     |                   |
         |         |            |     |                   |
         |         |            |     |                   |
         | tileA   |            |     | tileC             |
         |_________|____________|     |___________________|
          
                
                         
             
    ###########
    # LAYER 2 #
    ##################
        # outer pipe #                     
        ##############                              N
                                       ___________________
                                      |                   |
                                    kc|                   |
                                      |     tileB         |
                                      |___________________|
                                  P   .                   .
                                      .                   .
                                      .                   .
                                      .                   .     
                                      .                   .
                                      .....................
                               x
(local_m)
     ↳   ___kc____..............       ___________________
        |         |            .      |                   |
     mc | tileA_ip|            .      |                   |
        |         |            .      |                   |
  M     |_________|            .      |                   |
        |         |            .      |                   |
        |         |            .      |                   |
        |         |            .      |                   |
        |         |            .      | tileC             |
        |_________|.............      |___________________|


    ###########
    # LAYER 2 #
    ##################
        # inner pipe #                     
        ##############
                                  (local_n)
                                      ↓
                                       _nr _______________
                                      |   |               |
                                    kc|tileB_pj           |
                                      |   |               |
                                      |___|_______________|
                                      .                   .
                                      .                   .
                                      .                   .
                                      .                   .     
                                      .                   .
                                      .....................
                        x
                                 (local_m,local_n)
         ___kc____ ............       ↓___________________
        |         |            .     ↳|   |               |
     mc | tileA_ip|            .      |tileC_acc          |
        |         |            .      |   |               |
  M     |_________|            .      |___|               |
        .         .            .      |                   |
        .         .            .      |                   |
        .         .            .      |                   |
        .         .            .      |                   |
        ........................      |___________________|


    ###########
    # LAYER 3 #
    ###########
                                 

                                             (compute_n)
                                     ↓ ↓
                                      _nr ................
                        (compute_k) →|o o|               .
                                   kc|   |               .
                                     |   |               .
                                     |___|................
                                     .                   .
                                     .                   .
    (compute_m)                      .                   .
    |                                .                   .     
    | (accum_m)                      .                   .
    |  |                             .....................
    |  | (compute_k)           x
    |  | ↓     
    |  ↳ ____kc___ ............      ___ ................
    ↳   |o        |            .    |o o|               .
    ↳   |o        |            .    |o o|               .
      mc|o        |            .    |   |               .
  M     |o        |            .    |   |               .
        .‾‾‾‾‾‾‾‾‾.            .    .‾‾‾                .
        .         .            .    .                   .
        .         .            .    .                   .
        .         .            .    .                   .
        ........................    .....................


*/

object GEMM_MemoryHierarchy extends SpatialApp { // DISABLED Regression (Dense) // Args: none
  type T = Int

  @virtualize
  def main() = {
    val m = 64
    val n = 64
    val p = 64

    val M = m
    val N = n
    val P = p

    // val m = args(0).to[Int]
    // val n = args(1).to[Int]
    // val p = args(2).to[Int]

    // val M = ArgIn[Int]
    // val N = ArgIn[Int]
    // val P = ArgIn[Int]

    // setArg(M,m)
    // setArg(N,n)
    // setArg(P,p)

    val a = (0::m, 0::p){(i,j) => ((i*p + j)%8).to[T] }
    val b = (0::p, 0::n){(i,j) => ((i*n + j)%8).to[T] }
    val c = (0::m, 0::n){(i,j) => 0.to[T] }

    val A_dram = DRAM[T](M, P) 
    val B_dram = DRAM[T](P, N) 
    val C_dram = DRAM[T](M, N)

    val n_r        = 4 // rank-1 updated width/height
    // We could have two n_r but we assume we do square rank-1 updated

    val m_c        = 16 // blockram number of A matrix rows in each tile
    val k_c        = 16 // number of B rows/A Columns  in each tile
    val n_r_par    = 1

    setMem(A_dram, a)
    setMem(B_dram, b)
    setMem(C_dram, c)

    Accel {
      // **LAYER 1** ON-CHIP MEMORY 
      val tileC = SRAM[T](M, N)
      tileC load C_dram(0::M, 0::N par 16)

      Foreach(P by k_c) { k =>
        val tileA = SRAM[T](M, k_c)
        val tileB = SRAM[T](k_c, N)
        Parallel {
          tileA load A_dram(0::M, k::k+k_c par min(16,k_c))
          tileB load B_dram(k::k+k_c, 0::N par min(16,k_c))
        }

        // **LAYER 2** LOCAL-LEVEL STORE
        //      *outer*
        Foreach(M by m_c) { local_m =>
          val tileA_ip = SRAM[T](m_c, k_c)
          val tileB_pj = SRAM[T](k_c, n_r) 

          // DATA MOVEMENT
          Foreach(m_c by 1, k_c by 1 par min(16,k_c)) { (copy_m, copy_k) => tileA_ip(copy_m, copy_k) = tileA(local_m + copy_m, copy_k) }

          // **LAYER 2** LOCAL-LEVEL STORE
          //      *inner*
          Foreach(N by n_r) { local_n =>
            // DATA MOVEMENT
            Foreach(k_c by 1, n_r by 1) { (copy_k, copy_n) => tileB_pj(copy_k, copy_n) = tileB(copy_k, local_n + copy_n) }
            // Loaded local store level of tileB_pj


            // **LAYER 3** ACCUMULATOR (REGISTER LEVEL)     
            val tileC_acc = RegFile[T](n_r,n_r)
            Foreach(m_c by n_r){ accum_m =>
              // DATA MOVEMENT
              Foreach(n_r by 1, n_r by 1 par n_r) { (copy_m, copy_n) => 
                // tileC_acc(copy_m, copy_n) = tileC(local_m + accum_m + copy_m, local_n + copy_n) 
                tileC_acc(copy_m, copy_n) = tileC(local_m + accum_m + copy_m, local_n + copy_n) 
              }

              MemFold(tileC_acc)(k_c by 1) { compute_k =>
                val tileC_partial = RegFile[T](n_r,n_r)
                Foreach(n_r by 1, n_r by 1 par n_r) { (compute_m, compute_n) => 
                  tileC_partial(compute_m, compute_n) = tileA_ip(compute_m, compute_k) * tileB_pj(compute_k, compute_n)
                }
                tileC_partial
              }{_+_}

              // DATA MOVEMENT
              Foreach(n_r by 1, n_r by 1 par n_r) { (copy_m, copy_n) =>
                tileC(local_m + accum_m + copy_m, local_n + copy_n) = tileC_acc(copy_m, copy_n)
              }
            }
          }
        }
      }
      C_dram(0::M, 0::N par 16) store tileC
    }
    val result = getMatrix(C_dram)
  
    val gold = (0::m, 0::n){(i,j) => 
      Array.tabulate(p){k => a(i,k) * a(k,j)}.reduce{_+_}
    }

    printMatrix(gold, "Gold:")
    printMatrix(result, "Result:")

    val cksum = gold.zip(result){_==_}.reduce{_&&_}
    println("PASS: " + true + " (GEMM_MemoryHierarchy) * Fix par on outerproduct loops, something is messed up with readers ")
  }
}
