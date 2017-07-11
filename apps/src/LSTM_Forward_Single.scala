import spatial.dsl._
import org.virtualized._

/*
 * LSTM expression:
 *
 * Reference: CS224N, Stanford
 *
 * Dimensions:
 * x_t     \in d by N         : input word vector at time t
 * W       \in D_h by d       : weights matrices to condition x_t
 * U       \in D_h by D_h     : weights matrix to condition the previous hidden state
 * i_t     \in D_h by N       : weights matrix of the input gate
 * f_t     \in D_h by N       : weights matrix of the forget gate
 * o_t     \in D_h by N       : weights matrix of the output gate
 * h_{t-1} \in D_h by N       : weights of the last hidden state
 * h_t \in D_h by N           : weights of the current hidden state
 *
 */

/*
 * Lookup table for LSTM sigmoid
 * Reference: https://arxiv.org/pdf/1612.00694.pdf
 * Sigmoid input: min: -64, max: 64, sampling point = 2048
 */

/*
 * Foreach loop: store things in local mems instead of in DRAM
 */

/*
 * TODO:
 */

object LSTM_Forward_Single extends SpatialApp {
  import IR._

  // TODO: need to rethink of precision
  type X = FixPt[TRUE,_32,_32]

  @virtualize
  def GateForward[T:Type:Num] (
    /* Input gate */
    W_in: Matrix[T], U_in: Matrix[T],
    /* Forget gate */
    W_forget: Matrix[T], U_forget: Matrix[T],
    /* Output gate */
    W_output: Matrix[T], U_output: Matrix[T],
    /* New memory gate */
    W_new_mem: Matrix[T], U_new_mem: Matrix[T],
    /* Old memory gate */
    W_c_t_1: Matrix[T],
    /* Inputs */
    x: Matrix[T], h: Matrix[T],
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

    // Old memory gate
    val Wc_t_1 = DRAM[T](D_h, N)

    // Result matrix
    // val result = DRAM[T](D_h, N)
    // Result 1: Next memory
    val next_mem = DRAM[T](D_h, N)
    // Result 2: Next hidden state
    val next_hidden_state = DRAM[T](D_h, N)

    val b_N = 16                          // Stepsize for going through N classes
    val b_Dh = 16                         // Stepsize for going through hidden size
    val b_Wi_d = 16                       // First matmult, col step size
    val b_Ui_Dh = 16                      // Second matmult, col step size

    // For referencing the range of LUTs
    val lo = 32.to[T]
    val revPrec = 2.to[T]
    val totalSize = 128

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

    // Old memory gate
    setMem(Wc_t_1, W_c_t_1)

    Accel {
      /*
       * LUT table for sigmoid function:
       * Input lower bound: -32.0
       * Input upper bound: 32.0
       * Output lower bound: 0
       * Output upper bound: 1
       * Number of samples: 128
       * Precision: 64 / 128 = 0.5
       */
      val sigmoidLUT = LUT[T](totalSize)(
        0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T],
        0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T],
        0.0000000000.to[T], 0.0000000001.to[T], 0.0000000001.to[T], 0.0000000002.to[T], 0.0000000003.to[T], 0.0000000005.to[T], 0.0000000008.to[T], 0.0000000013.to[T],
        0.0000000021.to[T], 0.0000000034.to[T], 0.0000000056.to[T], 0.0000000092.to[T], 0.0000000152.to[T], 0.0000000251.to[T], 0.0000000414.to[T], 0.0000000683.to[T],
        0.0000001125.to[T], 0.0000001855.to[T], 0.0000003059.to[T], 0.0000005043.to[T], 0.0000008315.to[T], 0.0000013710.to[T], 0.0000022603.to[T], 0.0000037266.to[T],
        0.0000061442.to[T], 0.0000101300.to[T], 0.0000167014.to[T], 0.0000275357.to[T], 0.0000453979.to[T], 0.0000748462.to[T], 0.0001233946.to[T], 0.0002034270.to[T],
        0.0003353501.to[T], 0.0005527786.to[T], 0.0009110512.to[T], 0.0015011823.to[T], 0.0024726232.to[T], 0.0040701377.to[T], 0.0066928509.to[T], 0.0109869426.to[T],
        0.0179862100.to[T], 0.0293122308.to[T], 0.0474258732.to[T], 0.0758581800.to[T], 0.1192029220.to[T], 0.1824255238.to[T], 0.2689414214.to[T], 0.3775406688.to[T],
        0.5000000000.to[T], 0.6224593312.to[T], 0.7310585786.to[T], 0.8175744762.to[T], 0.8807970780.to[T], 0.9241418200.to[T], 0.9525741268.to[T], 0.9706877692.to[T],
        0.9820137900.to[T], 0.9890130574.to[T], 0.9933071491.to[T], 0.9959298623.to[T], 0.9975273768.to[T], 0.9984988177.to[T], 0.9990889488.to[T], 0.9994472214.to[T],
        0.9996646499.to[T], 0.9997965730.to[T], 0.9998766054.to[T], 0.9999251538.to[T], 0.9999546021.to[T], 0.9999724643.to[T], 0.9999832986.to[T], 0.9999898700.to[T],
        0.9999938558.to[T], 0.9999962734.to[T], 0.9999977397.to[T], 0.9999986290.to[T], 0.9999991685.to[T], 0.9999994957.to[T], 0.9999996941.to[T], 0.9999998145.to[T],
        0.9999998875.to[T], 0.9999999317.to[T], 0.9999999586.to[T], 0.9999999749.to[T], 0.9999999848.to[T], 0.9999999908.to[T], 0.9999999944.to[T], 0.9999999966.to[T],
        0.9999999979.to[T], 0.9999999987.to[T], 0.9999999992.to[T], 0.9999999995.to[T], 0.9999999997.to[T], 0.9999999998.to[T], 0.9999999999.to[T], 0.9999999999.to[T],
        1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T],
        1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T])

      def sigmoid(p: T) = {
        sigmoidLUT(((p + lo) * revPrec).to[Int])
      }

      /*
       * LUT table for tanh function:
       * Input lower bound: -32.0
       * Input upper bound: 32.0
       * Output lower bound: -1
       * Output upper bound: 1
       * Number of samples: 128
       * Precision: 64 / 128 = 0.5
       */
      val tanhLUT = LUT[T](totalSize) (
        -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T],
        -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T],
        -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T],
        -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T],
        -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T], -1.0000000000.to[T],
        -0.9999999999.to[T], -0.9999999998.to[T], -0.9999999994.to[T], -0.9999999985.to[T], -0.9999999959.to[T], -0.9999999888.to[T], -0.9999999695.to[T], -0.9999999172.to[T],
        -0.9999997749.to[T], -0.9999993882.to[T], -0.9999983369.to[T], -0.9999954794.to[T], -0.9999877117.to[T], -0.9999665972.to[T], -0.9999092043.to[T], -0.9997532108.to[T],
        -0.9993292997.to[T], -0.9981778976.to[T], -0.9950547537.to[T], -0.9866142982.to[T], -0.9640275801.to[T], -0.9051482536.to[T], -0.7615941560.to[T], -0.4621171573.to[T],
         0.0000000000.to[T],  0.4621171573.to[T],  0.7615941560.to[T],  0.9051482536.to[T],  0.9640275801.to[T],  0.9866142982.to[T],  0.9950547537.to[T],  0.9981778976.to[T],
         0.9993292997.to[T],  0.9997532108.to[T],  0.9999092043.to[T],  0.9999665972.to[T],  0.9999877117.to[T],  0.9999954794.to[T],  0.9999983369.to[T],  0.9999993882.to[T],
         0.9999997749.to[T],  0.9999999172.to[T],  0.9999999695.to[T],  0.9999999888.to[T],  0.9999999959.to[T],  0.9999999985.to[T],  0.9999999994.to[T],  0.9999999998.to[T],
         0.9999999999.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],
         1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],
         1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],
         1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],
         1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T],  1.0000000000.to[T])

      def tanh(p: T) = {
        tanhLUT(((p + lo) * revPrec).to[Int])
      }

      /*
       * A function that preforms tile-level batch multiplication of a^Tx, and pass the result
       * through a sigmoid LUT
       * @param tile_re: result tile
       * @param aT: inner product left
       * @param x: inner product right
       * @param i: row index from caller
       * @param j: col index from caller
       * @param tp: row of aT / col of x
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
            tile_re(ii, jj) = prev_aTx + prod_aTx.value
          }
        }
      }

      /*
       * The major function that describes the design
       * where Wi, x, Ui, h are DRAM locations
       * Assuming that the input matrices are transposed already
       * @param Wi: weights of input gate
       * @param x: DRAM matrix
       * @param Ui: DRAM matrix
       * @param h: DRAM matrix
       * @param m: row num of Wi, Ui
       * @param n: col num of x, h
       * @param p: col num of Wi  / row num of x
       * @param q: col num of Ui  / row num of h
       * @param mm: tile step size of Wi, Ui row
       * @param nn: tile step size of x, h col
       * @param pp: tile step size of Wi col / x row
       * @param qq: tile step size of Ui col / h row
       */
      def forward(
        /* Gate weights */
        Wi: DRAM2[T], Ui: DRAM2[T],
        /* Forget gate */
        Wf: DRAM2[T], Uf: DRAM2[T],
        /* Output gate */
        Wo: DRAM2[T], Uo: DRAM2[T],
        /* New memory gate */
        Wc: DRAM2[T], Uc: DRAM2[T],
        /* Old memory gate */
        Wc_t_1: DRAM2[T],
        /* Inputs */
        x: DRAM2[T], h: DRAM2[T],
        /* Sizes */
        m: Int, n: Int, p: Int, q: Int, mm: Int, nn: Int, pp: Int, qq: Int
      ) = {
        Foreach(m by mm, n by nn) { (i,j) =>
          // Test result
          val reg_ct = Reg[T](0.to[T])

          // Meaningful inputs
          val tile_WiTx = SRAM[T](mm, nn)
          val tile_UiTh = SRAM[T](mm, nn)
          val tile_WfTx = SRAM[T](mm, nn)
          val tile_UfTh = SRAM[T](mm, nn)
          val tile_WoTx = SRAM[T](mm, nn)
          val tile_UoTh = SRAM[T](mm, nn)
          val tile_WcTx = SRAM[T](mm, nn)
          val tile_UcTh = SRAM[T](mm, nn)
          val tile_Wc_t_1 = SRAM[T](mm, nn)

          // Output result 1: new memory weights
          val tile_new_mem = SRAM[T](mm, nn)
          // Output result 2: new hidden state weights
          val tile_new_hidden = SRAM[T](mm, nn)

          Parallel {
            List((tile_WiTx, Wi, x, p, pp), (tile_UiTh, Ui, h, q, qq),
                 (tile_WfTx, Wf, x, p, pp), (tile_UfTh, Uf, h, q, qq),
                 (tile_WoTx, Wo, x, p, pp), (tile_UoTh, Uo, h, q, qq),
                 (tile_WcTx, Wc, x, p, pp), (tile_UcTh, Uc, h, q, qq)
                 ).foreach { case (tile, w1, x1, len, step) => tileBatchMult(tile, w1, x1, i, j, p, pp, mm, nn)}
            tile_Wc_t_1 load Wc_t_1(i::i+mm, j::j+nn)
          }

          Foreach(mm by 1, nn by 1) { (ii, jj) =>
            //  i_t    = sigmoid(tile_WiTx(ii, jj) + tile_UiTh(ii, jj))
            //  f_t    = sigmoid(tile_WfTx(ii, jj) + tile_UfTh(ii, jj))
            //  o_t    = sigmoid(tile_WoTx(ii, jj) + tile_UoTh(ii, jj))
            //  c_tl_t = tanh(tile_WcTx(ii, jj) + tile_UcTh(ii, jj))

            // TODO: can we parallize these three lines?
            // c_t = f_t \times c_{t-1} + i_t \times c_tl_t
//            reg_ct := sigmoid(tile_WfTx(ii, jj) + tile_UfTh(ii, jj)) * tile_Wc_t_1(ii, jj) +
//                             sigmoid(tile_WiTx(ii, jj) + tile_UiTh(ii, jj)) * tanh(tile_WcTx(ii, jj) + tile_UcTh(ii, jj))
//            tile_new_mem(ii, jj) = reg_ct.value
//
//            // h_t = o_t \times tanh(c_t)
//            tile_new_hidden(ii, jj) = sigmoid(tile_WoTx(ii, jj) + tile_UoTh(ii, jj)) * tanh(reg_ct.value)
//

//              tile_new_mem(ii, jj) = tile_WfTx(ii, jj) + tile_UfTh(ii, jj)
//              tile_new_hidden(ii, jj) = tile_WiTx(ii, jj) + tile_UiTh(ii, jj)
//
            reg_ct := (tile_WfTx(ii, jj) + tile_UfTh(ii, jj)) * tile_Wc_t_1(ii, jj) +
                             (tile_WiTx(ii, jj) + tile_UiTh(ii, jj)) * (tile_WcTx(ii, jj) + tile_UcTh(ii, jj))
            tile_new_mem(ii, jj) = reg_ct.value
            tile_new_hidden(ii, jj) = (tile_WoTx(ii, jj) + tile_UoTh(ii, jj)) * (reg_ct.value)
          }

          next_mem(i::i+mm, j::j+nn) store tile_new_mem
          next_hidden_state(i::i+mm, j::j+nn) store tile_new_hidden
        }
      }

      /* Main function */
      forward (
        /* Input gate */
        Wi, Ui,
        /* Forget gate */
        Wf, Uf,
        /* Output gate */
        Wo, Uo,
        /* New memory gate */
        Wc, Uc,
        /* Old memory gate */
        Wc_t_1,
        /* Inputs */
        xt, h_t_1,
        /* Sizes */
        D_h, N, d, D_h, b_Dh, b_N, b_Wi_d, b_Ui_Dh
      )
    }

    (getMatrix(next_mem), getMatrix(next_hidden_state))
  }

  @virtualize
  def main() = {
    val D_h = 64
    val d = 64
    val N = 32

    val W_i =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val U_i =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val W_f =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val U_f =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val W_o =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val U_o =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val W_c =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val U_c =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val x_t =     loadCSV2D[X]("/home/tianzhao/data/64_by_32_basic_eles.csv", ",", "\n")
    val h_t_1 =   loadCSV2D[X]("/home/tianzhao/data/64_by_32_basic_eles.csv", ",", "\n")
    val W_c_t_1 = loadCSV2D[X]("/home/tianzhao/data/64_by_32_basic_eles.csv", ",", "\n")

    val (nextMem, nextHidden) = GateForward (
      /* Weights */
      W_i, U_i, W_f, U_f, W_o, U_o, W_c, U_c, W_c_t_1,
      /* Inputs */
      x_t, h_t_1,
      /* Sizes */
      D_h, d, N
    )

    printMatrix(nextMem, "LSTM nextMem = ")
    writeCSV2D[X](nextMem, "/home/tianzhao/spatial-LSTM/spatial-lang/apps/results/LSTM_Forward_Single/nextMem.csv", ",", "\n")
    printMatrix(nextHidden, "LSTM nextHidden = ")
    writeCSV2D[X](nextHidden, "/home/tianzhao/spatial-LSTM/spatial-lang/apps/results/LSTM_Forward_Single/nextHidden.csv", ",", "\n")
  }
}
