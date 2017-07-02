import spatial._
import org.virtualized._

/*
 * LSTM expression:
 *
 * Reference: CS224N, Stanford
 *
 * Dimensions:
 * x_t \in d         : input word vector at time t
 * W_i \in D_h*d     : weights matrix to condition x_t
 * h_{t-1} \in D_h   : D_h is the size of hidden layer
 * U^i \in D_h * D_h : weights matrix to condition the previous hidden state 
 *
 * Forward network:
 * i_t = sigmoid(W^i x_t + U^i h_{t-1})
 * f_t = sigmoid(W^f x_t + U^f h_{t-1})
 * o_t = sigmoid(W^o x_t + U^o h_{t-1})
 * g_t =    tanh(W^g x_t + U^g h_{t-1})
 * c_t = f_t \times c_{t-1} + i_t \times g_t
 * h_t = o_t \times tanh(c_t)
 *
 */

/*
 * Lookup table for LSTM sigmoid 
 *
 * Reference: https://arxiv.org/pdf/1612.00694.pdf 
 *
 * Sigmoid input: min: -64, max: 64, sampling point = 2048
 */

/*
 * Step 1: implement linear transformation
 * Step 2: Sigmoid
 * Step 3: tanh
 * Step 4: element-wise mul
 * Step 5: foreach loop
 */

object LSTM_GateForward extends SpatialApp { 
  import IR._

  // TODO: need to rethink of precision
  type X = FixPt[TRUE,_32,_32]

//  def sigmoid[T:Type:Num](t: T) = 1.to[T]/(exp(-t) + 1.to[T])

  @virtualize
  def GateForward[T:Type:Num](W: Array[T], x: Array[T], U: Array[T], h: Array[T], mm: Int, nn: Int, N_classes: Int) = {
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

    // Result matrix
    val result = DRAM[T](D_h, N)

    // Stepsize for going through N classes
    val b_N = 16

    // Stepsize for going through hidden size
    val b_Dh = 16

    // First matmult, col step size
    val b_Wi_d = 16
    
    // Second matmult, col step size
    val b_Ui_Dh = 16

    setMem(Wi, W)
    setMem(xt, x)
    setMem(Ui, U)
    setMem(h_t_1, h)

    Accel {
      /*
       * A function that preforms tile-level batchMult
       */




      /*
       * A function that does A^Tx + B^Th + Cb
       * where At, x, Bt, h, Cb are DRAM locations
       * Assuming that the input matrices are transposed already
       * @param At: DRAM matrix
       * @param x: DRAM matrix
       * @param Bt: DRAM matrix
       * @param h: DRAM matrix
       * @param m: row num of At, Bt, Cb
       * @param n: col num of x, h, Cb 
       * @param p: col num of At  / row num of x
       * @param q: col num of Bt  / row num of h
       * @param mm: tile step size of At, Bt, Cb row
       * @param nn: tile step size of x, h, Cb col
       * @param pp: tile step size of At col / x row 
       * @param qq: tile step size of Bt col / h row
       */
      def batchMult(At: DRAM2[T], x: DRAM2[T], Bt: DRAM2[T], h: DRAM2[T],  m: Int, n: Int, p: Int, q: Int, mm: Int, nn: Int, pp: Int, qq: Int): DRAM2[T] = {
        Foreach(m by mm, n by nn) { (i,j) =>
          val tile_re = SRAM[T](mm, nn)
          val tile_Cb = SRAM[T](mm, nn) 
          val tile_ATx = SRAM[T](mm, nn)
          val tile_BTh = SRAM[T](mm, nn)

          Parallel {
            // A^Tx 
            Foreach(p by pp) { k =>
              val tile_At = SRAM[T](mm, pp) 
              val tile_x = SRAM[T](pp, nn)
              Parallel {
                tile_At load At(i::i+mm, k::k+pp)
                tile_x load xt(k::k+pp, j::j+nn)
              }

              Foreach(mm by 1, nn by 1) { (ii,jj) =>
                val prodATx = Reduce(Reg[T])(pp by 1){ kk => 
                  tile_At(ii,kk) * tile_x(kk,jj)
                }{_+_}

                val prevATx = mux(k == 0, 0.to[T], tile_ATx(ii,jj))
                tile_ATx(ii,jj) = prevATx + prodATx.value
              }
            }

            // U_i^Th_{t-1}
            Foreach(d by b_Ui_Dh) { k =>
              val tile_Bt = SRAM[T](mm, qq) // TODO: what we really need here is 1 step size for row iter and another for col iter
              val tile_h = SRAM[T](qq, nn)
              Parallel {
                tile_Bt load Bt(i::i+mm, k::k+qq)
                tile_h load h(k::k+qq, j::j+nn)
              }

              Foreach(mm by 1, nn by 1) {(ii,jj) =>
                val prodBTh = Reduce(Reg[T])(qq by 1){ kk => 
                  tile_Bt(ii,kk) * tile_h(kk,jj)
                }{_+_}

                val prevBTh = mux(k == 0, 0.to[T], tile_BTh(ii,jj))
                tile_BTh(ii,jj) = prevBTh + prodBTh.value
              }
            }
          }

          // For the whole tile, reduce the three tiles and send it back to mem
          // TODO: do we need an extra pipe here?
          Foreach(mm by 1, nn by 1) { (ii, jj) =>
            tile_re(ii, jj) = tile_ATx(ii, jj) + tile_BTh(ii, jj) 
          } 
  
          // TODO: pass to sigmoid here
          result(i::i+mm, j::j+nn) store tile_re
        }

        result
      } 

      // Main loop
      // First affine
      val gate1DRAM2 = batchMult(Wi, xt, Ui, h_t_1, D_h, N, d, D_h, b_Dh, b_N, b_Wi_d, b_Ui_Dh)
      
      // Second affine 
    }

    getMem(result)
  }

  @virtualize
  def main() = {
    val D_h = 64
    val d = 64
    val N = 32

    val W_i = Array.tabulate(D_h){ j => Array.tabulate(d){ i => (i + j).to[X] } } 
    val U_i = Array.tabulate(D_h){ j => Array.tabulate(D_h){ i => (i + j + 1).to[X] } } 

    val W_f = Array.tabulate(D_h){ j => Array.tabulate(d){ i => (i + j + 2).to[X] } } 
    val U_f = Array.tabulate(D_h){ j => Array.tabulate(D_h){ i => (i + j + 3).to[X] } } 
    
    val W_o = Array.tabulate(D_h){ j => Array.tabulate(d){ i => (i + j + 4).to[X] } } 
    val U_o = Array.tabulate(D_h){ j => Array.tabulate(D_h){ i => (i + j + 5).to[X] } } 

    val W_g = Array.tabulate(D_h){ j => Array.tabulate(d){ i => (i + j + 6).to[X] } } 
    val U_g = Array.tabulate(D_h){ j => Array.tabulate(D_h){ i => (i + j + 7).to[X] } } 

    val x_t = Array.tabulate(d) { j => Array.tabulate(N){ i => ((i + 6) + j).to[X] } } 

    val h_t_1 = Array.tabulate(d) { j => Array.tabulate(N){ i => ((i + 7) + j).to[X] } } 

    val gateResult = GateForward(W_i.flatten, x_t.flatten, U_i.flatten, h_t_1.flatten, D_h, d, N)
    printArray(gateResult, "First gate yields: ")

    // Calculate gold
    val gold = Array.tabulate(D_h) { i =>
      val Wi_Row = W_i(i) 
      val Ui_Row = U_i(i)
//      val biasRow = bias(i)
      Array.tabulate(N) { j =>
        val xt_Col = x_t.map(row => row(j))
        val ht1_Col = h_t_1.map(row => row(j))
//       val bias_col = bias.map(row => row(j))
        Wi_Row.zip(xt_Col){_*_}.reduce{_+_} + Ui_Row.zip(ht1_Col){_*_}.reduce{_+_} // + bias_col(i)
      }
    }.flatten

    printArray(gold, "Gold result is: ")
  }
}
