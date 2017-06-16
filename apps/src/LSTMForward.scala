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
 *
 * Forward network:
 * i_t = sigmoid(W^i x_t + U^i h_{t-1} + bias0)
 * f_t = sigmoid(W^f x_t + U^f h_{t-1} + bias1)
 * o_t = sigmoid(W^o x_t + U^o h_{t-1} + bias2)
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

//  def sigmoid[T:Type:Num](t: T) = 1.to[T]/(exp(-t) + 1.to[T])

  @virtualize
  def GateForward[T:Type:Num](W: Array[T], x: Array[T], U: Array[T], h: Array[T], bias: Array[T], mm: Int, nn: Int, N_classes: Int) = {
    val D_h = ArgIn[Int]
    val d = ArgIn[Int]
    val N = ArgIn[Int]
    setArg(D_h, mm)
    setArg(d, nn)
    setArg(N, N_classes)

    // Param weights matrices
    val Wi = DRAM[T](D_h, d)
    val xt = DRAM[T](d, N)
    val Ui = DRAM[T](D_h, D_h)
    val h_t_1 = DRAM[T](D_h, N)
    val bi = DRAM[T](D_h, N)

    // Result matrix
    val result = DRAM[T](D_h, N)

    // Stepsize for going through N classes
    val b_N = 1

    // Stepsize for going through hidden size
    val b_Dh = 5

    // First matmult, col step size
    val b_Wi_d = 5
    
    // Second matmult, col step size
    val b_Ui_Dh = 2 

    setMem(Wi, W)
    setMem(xt, x)
    setMem(bi, bias)
    setMem(Ui, U)
    setMem(h_t_1, h)

    Accel {
      Foreach(D_h by b_Dh, N by b_N) { (i,j) =>
        val tile_re = SRAM[T](b_Dh, b_N)
        val tile_bias = SRAM[T](b_Dh, b_N) 
        val tile_WTx = SRAM[T](b_Dh, b_N)
        val tile_UTh = SRAM[T](b_Dh, b_N)

        Parallel {
          // W_i^Tx 
          Foreach(d by b_Wi_d) { k =>
            val tile_Wi = SRAM[T](b_Dh, b_Wi_d) 
            val tile_xt = SRAM[T](b_Wi_d, b_N)
            Parallel {
              tile_Wi load Wi(i::i+b_Dh, k::k+b_Wi_d)
              tile_xt load xt(k::k+b_Wi_d, j::j+b_N)
            }

            Foreach(b_Dh by 1, b_N by 1) { (ii,jj) =>
              val prodWTx = Reduce(Reg[T])(b_Wi_d by 1){ kk => 
                tile_Wi(ii,kk) * tile_xt(kk,jj)
              }{_+_}

              val prevWTx = mux(k == 0, 0.to[T], tile_WTx(ii,jj))
              tile_WTx(ii,jj) = prevWTx + prodWTx.value
            }
          }

          // U_i^Th_{t-1}
          Foreach(d by b_Ui_Dh) { k =>
            val tile_Ui = SRAM[T](b_Ui_Dh, b_Ui_Dh)
            val tile_ht_1 = SRAM[T](b_Ui_Dh, b_N)
            Parallel {
              tile_Ui load Ui(i::i+b_Ui_Dh, k::k+b_Ui_Dh)
              tile_ht_1 load h_t_1(k::k+b_Ui_Dh, j::j+b_N)
            }

            Foreach(b_Dh by 1, b_N by 1) {(ii,jj) =>
              val prodUTh = Reduce(Reg[T])(b_Ui_Dh by 1){ kk => 
                tile_Ui(ii,kk) * tile_ht_1(kk,jj)
              }{_+_}

              val prevUTh = mux(k == 0, 0.to[T], tile_UTh(ii,jj))
              tile_UTh(ii,jj) = prevUTh + prodUTh.value
            }
          }
          
          // Load a tile from bias
          tile_bias load bi(i::i+b_Dh, j::j+b_N)
        }

        // For the whole tile, reduce the three tiles and send it back to mem
        Pipe {
          Foreach(b_Dh by 1, b_N by 1) { (ii, jj) =>
//            tile_re(ii, jj) = sigmoid(tile_UTh(ii, jj) + tile_WTx(ii, jj) + tile_bias(ii, jj))
            tile_re(ii, jj) = tile_UTh(ii, jj) + tile_WTx(ii, jj) + tile_bias(ii, jj)
          } 

          result(i::i+b_Dh, j::j+b_N) store tile_re
        }
      }
    }

    getMem(result)
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

    val gateResult = GateForward(W_i.flatten, x_t.flatten, U_i.flatten, h_t_1.flatten, bias.flatten, D_h, d, N)

    println("result cksum: " + gateResult.map(a => a).reduce{_+_})
  }
}
