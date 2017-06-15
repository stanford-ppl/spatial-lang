import spatial._
import org.virtualized._

/*
 * Reference: CS224N, Stanford
 *
 * Dimensions:
 * x_t \in d         : input word vector at time t
 * W_i \in D_h*d     : weights matrix to condition x_t
 * h_{t-1} \in D_h   : D_h is the size of hidden layer
 * U^i \in D_h * D_h : weights matrix to condition the previous hidden state 
 * Forward network:
 * i_t = sigmoid(W^i x_t + U^i h_{t-1})
 * f_t = sigmoid(W^f x_t + U^f h_{t-1})
 * o_t = sigmoid(W^o x_t + U^o h_{t-1})
 * g_t = tanh(W^g x_t + U^g h_{t-1})
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

object LSTMForward extends SpatialApp { 
  import IR._

  type X = FixPt[TRUE,_16,_16]

  @virtualize
  def GateForward[T:Type:Num](W: Array[T], x: Array[T], U: Array[T], h: Array[T], bias: Array[T],  mm: Int, nn: Int, N_classes: Int) = {
    val D_h = ArgIn[Int]
    val d = ArgIn[Int]
    val N = ArgIn[Int]
    setArg(D_h, mm)
    setArg(d, nn)
    setArg(N, N_classes)

    val W_i = DRAM[T](D_h, d)
    val x_t = DRAM[T](d, N)
    val b_i = DRAM[T](D_h, N)
    val U_i = DRAM[T](D_h, D_h)
    val h_t_1 = DRAM[T](D_h, N)

    val bDh = 5
    val bd = 5
    val bN = 1

    setMem(W_i, W)
    setMem(x_t, x)
    setMem(b_i, bias)
    setMem(U_i, U)
    setMem(h_t_1, h)

    Accel {
      Sequential.Foreach(D_h by bDh, N by bN) { (i,j) =>
        val tileBias = SRAM[T](bDh, bN)
        tileBias load b_i(i::i+bDh, j::j+bN)

        MemFold(tileBias)(d by bd) { k =>
          val tileA = SRAM[T](bDh, bd) 
          val tileB = SRAM[T](bd, bN)
          val accum = SRAM[T](bDh, bN)
          Parallel {
            tileA load W_i(i::i+bDh, k::k+bd)
            tileB load x_t(k::k+bd, j::j+bN)
          }

          MemReduce(accum)(bd by 1){ kk =>
            val tileBias_partial = SRAM[T](bDh, bN)
            Foreach(bDh by 1, bN by 1){ (ii,jj) =>
              tileBias_partial(ii,jj) = tileA(ii,kk) * tileB(kk, jj)
            }
            tileBias_partial 
          }{_+_}
        }{_+_}

        b_i(i::i+bDh, j::j+bN) store tileBias



      }
    }
    getMem(b_i)
  }

  @virtualize
  def main() = {
    val D_h = 10
    val d = 10 
    val N = 1 

    val W_i = Array.tabulate(D_h){ j => Array.tabulate(d){ i => (i + j).to[X] } } 
    val x_t = Array.tabulate(d) { j => Array.tabulate(N){ i => (i * j).to[X] } } 
    val bias = Array.tabulate(D_h) { j => Array.tabulate(N){ i => 0.to[X] } } 

    val U_i = Array.tabulate(D_h){ j => Array.tabulate(D_h){ i => (i + j).to[X] } } 
    val h_t_1 = Array.tabulate(d) { j => Array.tabulate(N){ i => (i * j).to[X] } } 

    val result = GateForward(W_i.flatten, x_t.flatten, U_i.flatten, h_t_1.flatten,  bias.flatten, D_h, d, N)

    println("result cksum: " + result.map(a => a).reduce{_+_})
  }
}
