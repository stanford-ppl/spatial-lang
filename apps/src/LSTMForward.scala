import spatial._
import org.virtualized._

/*
 * Forward network:
 * i_t = sigmoid(W^i x_t + U^i h_{t-1} + b^i)
 * f_t = sigmoid(W^f x_t + U^f h_{t-1} + b^f)
 * o_t = sigmoid(W^o x_t + U^o h_{t-1} + b^o)
 * g_t = tanh(W^g x_t + U^g h_{t-1} + b^g)
 * c_t = f_t \times c_{t-1} + i_t \times g_t
 * h_t = o_t \times tanh(c_t)
 *
 */

/*
 * Step 1: implement linear transformation
 * Step 2: Sigmoid
 * Step 3: tanh
 * Step 4: element-wise mul
 * Step 5: foreach loop
 */

// This performs one step
// Wi \in  bN * N
// Ui \in  bN * hN 
// x_t \in N
// h \in hN
// b \in bN
//

object LSTMForward extends SpatialApp { 
  import IR._

  type X = FixPt[TRUE,_16,_16]

  val innerPar = 16
  val midPar = 2
  val outerPar = 2

  val tsm = 16
  val tsn = 64
  val tsp = 64

  @virtualize
  def LSTMForward[T:Type:Num](A: Array[T], B: Array[T], bias_i: Array[T], mm: Int, nn: Int, pp: Int) = {
    val bN = ArgIn[Int]
    val N = ArgIn[Int]
    val hN = ArgIn[Int]
    setArg(bN,mm)
    setArg(N,nn)
    setArg(hN,pp)

    val a = DRAM[T](bN, hN)
    val b = DRAM[T](hN, N)
    val c = DRAM[T](bN, N)

    val op = outerPar (1 -> 1)
    val mp = midPar (1 -> 16)
    val ip = innerPar (1 -> 64)
    val px = 1 (1 -> 1) // Cannot parallelize accum across k blocks

    val bm = tsm (48 -> 48 -> 1920)
    val bn = tsn (48 -> 48 -> 1920)
    val bp = tsp (48 -> 48 -> 1920)

    setMem(a, A)
    setMem(b, B)
    setMem(c, bias_i)

    Accel {
      Sequential.Foreach(bn by bm, N by bn par op) { (i,j) =>
        val tileC = SRAM[T](bm, bn)
        tileC load c(i::i+bm, j::j+bn par ip)
        MemFold(tileC)(hN by bp) { k =>
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
    val bN = 10
    val N = 10 
    val hN = 10

    val a = Array.tabulate(bN){ j => Array.tabulate(hN){ i => (i + j).to[X] } } 
    val b = Array.tabulate(hN){ j => Array.tabulate(N){ i => (i + j).to[X] } } 
    val bias_i = Array.fill(bN){ Array.fill(N){ 0.to[X] } }

    val result = LSTMForward(a.flatten, b.flatten, bias_i.flatten, bN, N, hN)

    println("result cksum: " + result.map(a => a).reduce{_+_})
  }
}
