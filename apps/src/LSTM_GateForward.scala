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
 * i_t = sigmoid(W^i x_t + U^i h_{t-1})           (Input gate)
 * f_t = sigmoid(W^f x_t + U^f h_{t-1})           (Forget gate)
 * o_t = sigmoid(W^o x_t + U^o h_{t-1})           (Output gate)
 * c_tl_t = tanh(W^c x_t + U^c h_{t-1})           (New memory gate)
 * c_t = f_t \times c_{t-1} + i_t \times c_tl_t   (Final memory cell)
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
  def GateForward[T:Type:Num](
    /* Input gate */
    W_in: Array[T], x: Array[T], U_in: Array[T], h: Array[T], mm: Int, nn: Int, N_classes: Int,
    /* Forget gate */
    W_forget: Array[T], U_forget: Array[T]
  ) = {
    val D_h = ArgIn[Int]
    val d = ArgIn[Int]
    val N = ArgIn[Int]

    setArg(D_h, mm)
    setArg(d, nn)
    setArg(N, N_classes)

    // Param weights matrices
    val xt = DRAM[T](d, N)
    val h_t_1 = DRAM[T](D_h, N)

    // Input gate DRAM
    val Wi = DRAM[T](D_h, d)
    val Ui = DRAM[T](D_h, D_h)

    // Forget gate DRAM
    val Wf = DRAM[T](D_h, d)
    val Uf = DRAM[T](D_h, D_h)

    // Result matrix
    val result = DRAM[T](D_h, N)

    val b_N = 16                  // Stepsize for going through N classes
    val b_Dh = 16                 // Stepsize for going through hidden size
    val b_Wi_d = 16               // First matmult, col step size
    val b_Ui_Dh = 16              // Second matmult, col step size

    setMem(xt, x)
    setMem(h_t_1, h)

    // Input gate
    setMem(Wi, W_in)
    setMem(Ui, U_in)

    // Forget gate
    setMem(Wf, W_forget)
    setMem(Uf, U_forget)

    Accel {
      /*
       * A function that preforms tile-level batch multiplication of a^Tx
       * @param tile_re: result tile
       * @param aT: inner product left
       * @param x: inner product right
       * @param i: row index from caller
       * @param j: col index from caller
       * @param tp: col of aT / row of x
       * @param tpp: step size to iterate over tp
       * @param mm: row size of the tile
       * @param nn: col size of the tile
       */
      def tileBatchMult(tile_re: SRAM2[T], aT: DRAM2[T], x: DRAM2[T], i: Int, j: Int, tp: Int, tpp: Int, mm: Int, nn: Int) = {
        Foreach(tp by tpp) { k =>
          val tile_aT = SRAM[T](mm, tpp)
          val tile_x = SRAM[T](tpp, nn)
          Parallel {
            tile_aT load aT(i::i+mm, k::k+tpp)
            tile_x load x(k::k+tpp, j::j+nn)
          }

          Foreach(mm by 1, nn by 1) { (ii,jj) =>
            val prod_aTx = Reduce(Reg[T])(tpp by 1){ kk =>
              tile_aT(ii,kk) * tile_x(kk,jj)
            }{_+_}

            val prev_aTx = mux(k == 0, 0.to[T], tile_re(ii,jj))
            tile_re(ii,jj) = prev_aTx + prod_aTx.value
          }
        }
      }

      /*
       * A function that does A^Tx + B^Th + Cb
       * where Wi, x, Ui, h, Cb are DRAM locations
       * Assuming that the input matrices are transposed already
       * @param Wi: DRAM matrix
       * @param x: DRAM matrix
       * @param Ui: DRAM matrix
       * @param h: DRAM matrix
       * @param m: row num of Wi, Ui, Cb
       * @param n: col num of x, h, Cb
       * @param p: col num of Wi  / row num of x
       * @param q: col num of Ui  / row num of h
       * @param mm: tile step size of Wi, Ui, Cb row
       * @param nn: tile step size of x, h, Cb col
       * @param pp: tile step size of Wi col / x row
       * @param qq: tile step size of Ui col / h row
       */
      def batchMult(
        /* Input gate */
        Wi: DRAM2[T], x: DRAM2[T], Ui: DRAM2[T], h: DRAM2[T],  m: Int, n: Int, p: Int, q: Int, mm: Int, nn: Int, pp: Int, qq: Int,
        /* Forget gate */
        Wf: DRAM2[T], Uf: DRAM2[T]
      ) = {
        Foreach(m by mm, n by nn) { (i,j) =>
          val tile_re = SRAM[T](mm, nn)
          val tile_WiTx = SRAM[T](mm, nn)
          val tile_UiTh = SRAM[T](mm, nn)
          val tile_WfTx = SRAM[T](mm, nn)
          val tile_UfTh = SRAM[T](mm, nn)

          Parallel {
            tileBatchMult(tile_WiTx, Wi, x, i, j, p, pp, mm, nn)
            tileBatchMult(tile_UiTh, Ui, h, i, j, q, qq, mm, nn)
            tileBatchMult(tile_WfTx, Wf, x, i, j, p, pp, mm, nn)
            tileBatchMult(tile_UfTh, Uf, h, i, j, q, qq, mm, nn)
          }

          // For the whole tile, reduce the three tiles and send it back to mem
          Foreach(mm by 1, nn by 1) { (ii, jj) =>
            // TODO: for now just add them together..
            tile_re(ii, jj) = tile_WiTx(ii, jj) + tile_UiTh(ii, jj) +
                              tile_WfTx(ii, jj) + tile_UfTh(ii, jj)
          }

          // TODO: pass to sigmoid here
          result(i::i+mm, j::j+nn) store tile_re
        }

        result
      }

      batchMult(
        /* Input gate */
        Wi, xt, Ui, h_t_1, D_h, N, d, D_h, b_Dh, b_N, b_Wi_d, b_Ui_Dh,
        /* Forget gate */
        Wf, Uf
      )
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

    val gateResult = GateForward(
      /* Input gate */
      W_i.flatten, x_t.flatten, U_i.flatten, h_t_1.flatten, D_h, d, N,
      /* Forget gate */
      W_f.flatten, U_f.flatten,
     )
    printArray(gateResult, "First gate yields: ")

    // Calculate gold
    val gold = Array.tabulate(D_h) { i =>
      val Wi_Row = W_i(i)
      val Ui_Row = U_i(i)
      val Wf_Row = W_f(i)
      val Uf_Row = U_f(i)
      Array.tabulate(N) { j =>
        val xt_Col = x_t.map(row => row(j))
        val ht1_Col = h_t_1.map(row => row(j))
        Wi_Row.zip(xt_Col){_*_}.reduce{_+_} + Ui_Row.zip(ht1_Col){_*_}.reduce{_+_}  +
        Wf_Row.zip(xt_Col){_*_}.reduce{_+_} + Uf_Row.zip(ht1_Col){_*_}.reduce{_+_}
      }
    }.flatten

    printArray(gold, "Gold result is: ")
  }
}
