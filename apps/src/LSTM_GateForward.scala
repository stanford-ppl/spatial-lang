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
  type X = FixPt[TRUE,_16,_16]

//  def sigmoid[T:Type:Num](t: T) = 1.to[T]/(exp(-t) + 1.to[T])

  @virtualize
  def GateForward[T:Type:Num] (
    /* Input gate */
    W_in: Array[T], U_in: Array[T],
    /* Forget gate */
    W_forget: Array[T], U_forget: Array[T],
    /* Output gate */
    W_output: Array[T], U_output: Array[T],
    /* New memory gate */
    W_new_mem: Array[T], U_new_mem: Array[T],
    /* Inputs */
    x: Array[T], h: Array[T],
    /* Sizes */
    mm: Int, nn: Int, N_classes: Int
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

    // Output gate DRAM
    val Wo = DRAM[T](D_h, d)
    val Uo = DRAM[T](D_h, D_h)

    // New memory gate
    val Wc = DRAM[T](D_h, d)
    val Uc = DRAM[T](D_h, D_h)

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

    // Output gate
    setMem(Wo, W_output)
    setMem(Uo, U_output)

    // New memory gate
    setMem(Wc, W_new_mem)
    setMem(Uc, U_new_mem)

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
        Wi: DRAM2[T], Ui: DRAM2[T],
        /* Forget gate */
        Wf: DRAM2[T], Uf: DRAM2[T],
        /* Output gate */
        Wo: DRAM2[T], Uo: DRAM2[T],
        /* New memory gate */
        Wc: DRAM2[T], Uc: DRAM2[T],
        /* Inputs */
        x: DRAM2[T], h: DRAM2[T],
        /* Sizes */
        m: Int, n: Int, p: Int, q: Int, mm: Int, nn: Int, pp: Int, qq: Int
      ) = {
        Foreach(m by mm, n by nn) { (i,j) =>
          val tile_re = SRAM[T](mm, nn)
          val tile_WiTx = SRAM[T](mm, nn)
          val tile_UiTh = SRAM[T](mm, nn)
          val tile_WfTx = SRAM[T](mm, nn)
          val tile_UfTh = SRAM[T](mm, nn)
          val tile_WoTx = SRAM[T](mm, nn)
          val tile_UoTh = SRAM[T](mm, nn)
          val tile_WcTx = SRAM[T](mm, nn)
          val tile_UcTh = SRAM[T](mm, nn)

          Parallel {
            tileBatchMult(tile_WiTx, Wi, x, i, j, p, pp, mm, nn)
            tileBatchMult(tile_UiTh, Ui, h, i, j, q, qq, mm, nn)
            tileBatchMult(tile_WfTx, Wf, x, i, j, p, pp, mm, nn)
            tileBatchMult(tile_UfTh, Uf, h, i, j, q, qq, mm, nn)
            tileBatchMult(tile_WoTx, Wo, x, i, j, p, pp, mm, nn)
            tileBatchMult(tile_UoTh, Uo, h, i, j, q, qq, mm, nn)
            tileBatchMult(tile_WcTx, Wc, x, i, j, p, pp, mm, nn)
            tileBatchMult(tile_UcTh, Uc, h, i, j, q, qq, mm, nn)
          }

          // For the whole tile, reduce the three tiles and send it back to mem
          Foreach(mm by 1, nn by 1) { (ii, jj) =>
            // TODO: for now just add them together..
            tile_re(ii, jj) = tile_WiTx(ii, jj) + tile_UiTh(ii, jj) +
                              tile_WfTx(ii, jj) + tile_UfTh(ii, jj) +
                              tile_WoTx(ii, jj) + tile_UoTh(ii, jj) +
                              tile_WcTx(ii, jj) + tile_UcTh(ii, jj)
          }

          // TODO: pass to sigmoid here
          result(i::i+mm, j::j+nn) store tile_re
        }
      }

      batchMult(
        /* Input gate */
        Wi, Ui,
        /* Forget gate */
        Wf, Uf,
        /* Output gate */
        Wo, Uo,
        /* New memory gate */
        Wc, Uc,
        /* Inputs */
        xt, h_t_1,
        /* Sizes */
        D_h, N, d, D_h, b_Dh, b_N, b_Wi_d, b_Ui_Dh
      )
    }

    getMem(result)
  }

  @virtualize
  def main() = {
    val D_h = 64
    val d = 64
    val N = 32

    // TODO: load these matrices with real weights
    // val W_i = Array.tabulate(D_h){ j => Array.tabulate(d){ i => ((i + j)).to[X] } }
    // val U_i = Array.tabulate(D_h){ j => Array.tabulate(D_h){ i => ((i + j + 1)).to[X] } }
    // val W_f = Array.tabulate(D_h){ j => Array.tabulate(d){ i => ((i + j + 2)).to[X] } }
    // val U_f = Array.tabulate(D_h){ j => Array.tabulate(D_h){ i => ((i + j + 3)).to[X] } }
    // val W_o = Array.tabulate(D_h){ j => Array.tabulate(d){ i => ((i + j + 4)).to[X] } }
    // val U_o = Array.tabulate(D_h){ j => Array.tabulate(D_h){ i => ((i + j + 5)).to[X] } }
    // val W_c = Array.tabulate(D_h){ j => Array.tabulate(d){ i => ((i + j + 6)).to[X] } }
    // val U_c = Array.tabulate(D_h){ j => Array.tabulate(D_h){ i => ((i + j + 7)).to[X] } }
    // val x_t = Array.tabulate(d) { j => Array.tabulate(N){ i => ((i + 6 + j)).to[X] } }
    // val h_t_1 = Array.tabulate(d) { j => Array.tabulate(N){ i => ((i + 7 + j)).to[X] } }

    val W_i = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_i = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val W_f = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_f = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val W_o = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_o = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val W_c = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_c = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val x_t = loadCSV1D[X]("/home/tianzhao/data/64_by_32_eles.csv", "\n")
    val h_t_1 = loadCSV1D[X]("/home/tianzhao/data/64_by_32_eles.csv", "\n")

    val gateResult = GateForward (
      /* Input gate */
      W_i, U_i,
      /* Forget gate */
      W_f, U_f,
      /* Output gate */
      W_o, U_o,
      /* New memory gate */
      W_c, U_c,
      /* Inputs */
      x_t, h_t_1,
      /* Sizes */
      D_h, d, N
    )

    printArray(gateResult, "Affine gates yields: ")

    // Calculate gold
//    val gold = Array.tabulate(D_h) { i =>
//     val Wi_Row = W_i.slice(i, 64)
//     val Ui_Row = U_i.slice(i, 64)
//     val Wf_Row = W_f.slice(i, 64)
//     val Uf_Row = U_f.slice(i, 64)
//     val Wo_Row = W_o.slice(i, 64)
//     val Uo_Row = U_o.slice(i, 64)
//     val Wc_Row = W_c.slice(i, 64)
//     val Uc_Row = U_c.slice(i, 64)
//     Array.tabulate(N) { j =>
//       val xt_Col = x_t.map(row => row(j))
//       val ht1_Col = h_t_1.map(row => row(j))
//       Wi_Row.zip(xt_Col){_*_}.reduce{_+_} + Ui_Row.zip(ht1_Col){_*_}.reduce{_+_} +
//       Wf_Row.zip(xt_Col){_*_}.reduce{_+_} + Uf_Row.zip(ht1_Col){_*_}.reduce{_+_} +
//       Wo_Row.zip(xt_Col){_*_}.reduce{_+_} + Uo_Row.zip(ht1_Col){_*_}.reduce{_+_} +
//       Wc_Row.zip(xt_Col){_*_}.reduce{_+_} + Uc_Row.zip(ht1_Col){_*_}.reduce{_+_}
//     }
//    }.flatten
//
    // printArray(gold, "Gold result is: ")
  }
}
