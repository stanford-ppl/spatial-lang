import spatial._
import org.virtualized._

/* Radix-4 FFT program in Spatial. */
/* Signal size should be powers of four (standard). */
object FFT4 extends SpatialApp {
  import IR._

  /* Use fixed point. */
  type T = FixPt[TRUE,_16,_16]

  val threshold = 0.5.to[T] /* Checksum threshold. */

  /* FFT function run by the accelerator.
     Parameters: FFT real and imaginary arrays (flattened),
     signal real and imaginary arrays, number of stages, and
     N (signal length).
     Returns: Fourier Transform of inputted signal as a tuple
     (real and imaginary arrays). */
  @virtualize
  def FFT4(AR: Array[T], AI: Array[T], BR: Array[T], BI: Array[T],
    nStages: Int, nn: Int) = {
    /* Set arguments and place arrays in off-chip memory. */
    val N = ArgIn[Int]
    val numStages = ArgIn[Int]
    setArg(N, nn)
    setArg(numStages, nStages)

    val sigReal = DRAM[T](N)
    val sigImag = DRAM[T](N)
    val twiddleR = DRAM[T](N)
    val twiddleI = DRAM[T](N)
    val finReal = DRAM[T](N)
    val finImag = DRAM[T](N)

    setMem(sigReal, AR)
    setMem(sigImag, AI)
    setMem(twiddleR, BR)
    setMem(twiddleI, BI)

    Accel {
      /* Fixed size, but could possibly be broken down. */
      val tempReal = SRAM[T](128)
      val tempImag = SRAM[T](128)

      val finTempReal = SRAM[T](128)
      val finTempImag = SRAM[T](128)

      /* Initialize to signal values. */
      tempReal load sigReal(0::N)
      tempImag load sigImag(0::N)

      val rInd = Reg[Int](0.to[Int])

      val currTwiddleR = SRAM[T](128)
      val currTwiddleI = SRAM[T](128)

      /* Load in all twiddle values. */
      currTwiddleR load twiddleR(0::N)
      currTwiddleI load twiddleI(0::N)
      /*
      /* Calculate for first numStages-1 stages. */
      Foreach (0 until numStages/*-1*/) {i =>
        Foreach (0 until N/4) {r =>
          /* Could be made simpler using an Array declaration,
             but this works for now. */
          rInd := r.to[Int]
          val nextR0 = tempReal(rInd.value)
          val nextR1 = tempReal(rInd.value + N/4)
          val nextR2 = tempReal(rInd.value + 2.to[Int]*N/4)
          val nextR3 = tempReal(rInd.value + 3.to[Int]*N/4)

          val nextI0 = tempImag(rInd.value)
          val nextI1 = tempImag(rInd.value + N/4)
          val nextI2 = tempImag(rInd.value + 2.to[Int]*N/4)
          val nextI3 = tempImag(rInd.value + 3.to[Int]*N/4)

          /* Obtain next pow. */
          val segPow = Reg[Int](1)
          segPow := 1.to[Int]
          Reduce(segPow)(0 until i){x =>
            4.to[Int]}{_*_}

          val seg = (rInd.value.to[Int]/segPow) * segPow

          val twid2R = currTwiddleR(2*seg)
          val twid2I = currTwiddleI(2*seg)
          val twid1R = currTwiddleR(seg)
          val twid1I = currTwiddleI(seg)
          val twid3R = currTwiddleR(3*seg)
          val twid3I = currTwiddleI(3*seg)

          val nextBFly = compBFly(nextR0, nextI0, nextR1, nextI1,
            nextR2, nextI2, nextR3, nextI3,
            twid2R, twid2I, twid1R, twid1I,
            twid3R, twid3I)

          /* Update temporary final values. */
          Pipe { finTempReal(4*rInd.value) = nextBFly._1 }
          Pipe { finTempImag(4*rInd.value) = nextBFly._2 }
          Pipe { finTempReal(4*rInd.value + 1) = nextBFly._3 }
          Pipe { finTempImag(4*rInd.value + 1) = nextBFly._4 }
          Pipe { finTempReal(4*rInd.value + 2) = nextBFly._5 }
          Pipe { finTempImag(4*rInd.value + 2) = nextBFly._6 }
          Pipe { finTempReal(4*rInd.value + 3) = nextBFly._7 }
          Pipe { finTempImag(4*rInd.value + 3) = nextBFly._8 }
        }

        /* Update the "in-between" scratch memory. */
        val pInd = Reg[Int](0.to[Int])
        Foreach (0 until N) {p =>
          pInd := p.to[Int]
          tempReal(pInd.value) = finTempReal(pInd.value)
          tempImag(pInd.value) = finTempImag(pInd.value)
        }
      }*/

      /* Calculate for the last stage. */
      Sequential.Foreach (0 until N/4) {r =>
        rInd := r.to[Int]
        val nextR0 = tempReal(rInd.value)
        val nextR1 = tempReal(rInd.value + N/4) // Used in multiple places
        val nextR2 = tempReal(rInd.value + 2.to[Int]*N/4)
        val nextR3 = tempReal(rInd.value + 3.to[Int]*N/4) // Used in multiple pipes

        val nextI0 = tempImag(rInd.value)
        val nextI1 = tempImag(rInd.value + N/4)
        val nextI2 = tempImag(rInd.value + 2.to[Int]*N/4)
        val nextI3 = tempImag(rInd.value + 3.to[Int]*N/4)

        val twid2R = currTwiddleR(0)
        val twid2I = currTwiddleI(0)
        val twid1R = currTwiddleR(0)
        val twid1I = currTwiddleI(0)
        val twid3R = currTwiddleR(0)
        val twid3I = currTwiddleI(0)

        val nextBFly = compBFly(nextR0, nextI0, nextR1, nextI1,
          nextR2, nextI2, nextR3, nextI3,
          twid2R, twid2I, twid1R, twid1I,
          twid3R, twid3I)

        /* Update temporary final values. */
        Pipe { finTempReal(4*rInd.value) = nextBFly._1 }
        Pipe { finTempImag(4*rInd.value) = nextBFly._2 }
        Pipe { finTempReal(4*rInd.value + 1) = nextBFly._3 }
        Pipe { finTempImag(4*rInd.value + 1) = nextBFly._4 }
        Pipe { finTempReal(4*rInd.value + 2) = nextBFly._5 }
        Pipe { finTempImag(4*rInd.value + 2) = nextBFly._6 }
        Pipe { finTempReal(4*rInd.value + 3) = nextBFly._7 }
        Pipe { finTempImag(4*rInd.value + 3) = nextBFly._8 }
      }
      /* Store block in DRAM. */
      finReal(0::N) store finTempReal
      finImag(0::N) store finTempImag
    }
    /* Return as a tuple. */
    (getMem(finReal), getMem(finImag))
  }

  /* Function to compute a matrix multiplication. */
  @virtualize
  def compMult(aR: T, aI: T,
    bR: T, bI: T): (T, T) = {
    val real = aR * bR - aI * bI
    val imag = aR * bI + aI * bR
    return (real, imag)
  }

  /* Function to compute butterfly. */
  @virtualize
  def compBFly(r0: T, i0: T, r1: T, i1: T, r2: T, i2: T, r3: T, i3: T,
    t2r: T, t2i: T, t1r: T, t1i: T, t3r: T, t3i: T):
  (T, T, T, T, T, T, T, T) = {
    /* Initialize variables and scale by 1/4. */
    val aR = r0*(0.25).to[T]
    val aI = i0*(0.25).to[T]
    val bR = r1*(0.25).to[T]
    val bI = i1*(0.25).to[T]
    val cR = r2*(0.25).to[T]
    val cI = i2*(0.25).to[T]
    val dR = r3*(0.25).to[T]
    val dI = i3*(0.25).to[T]

    val twid2R = t2r
    val twid2I = t2i
    val twid1R = t1r
    val twid1I = t1i
    val twid3R = t3r
    val twid3I = t3i

    /* Perform Radix-4 algorithm. */
    /* First computation. */
    val resR0 = (aR+bR+cR+dR).to[T]
    val resI0 = (aI+bI+cI+dI).to[T]

    /* Second computation. */
    val a2Comp = compMult(aR, aI, twid2R, twid2I)
    val b2Comp = compMult(bR, bI, twid2R, twid2I)
    val c2Comp = compMult(cR, cI, twid2R, twid2I)
    val d2Comp = compMult(dR, dI, twid2R, twid2I)
    val resR1 = (a2Comp._1 - b2Comp._1 + c2Comp._1 - d2Comp._1).to[T]
    val resI1 = (a2Comp._2 - b2Comp._2 + c2Comp._2 - d2Comp._2).to[T]

    /* Third computation. */
    val a3Comp = compMult(aR, aI, twid1R, twid1I)
    val b3Comp = compMult(bI, -bR, twid1R, twid1I)
    val c3Comp = compMult(cR, cI, twid1R, twid1I)
    val d3Comp = compMult(dI, -dR, twid1R, twid1I)
    val resR2 = (a3Comp._1 - b3Comp._1 - c3Comp._1 + d3Comp._1).to[T]
    val resI2 = (a3Comp._2 - b3Comp._2 - c3Comp._2 + d3Comp._2).to[T]

    /* Fourth computation. */
    val a4Comp = compMult(aR, aI, twid3R, twid3I)
    val b4Comp = compMult(bI, -bR, twid3R, twid3I)
    val c4Comp = compMult(cR, cI, twid3R, twid3I)
    val d4Comp = compMult(dI, -dR, twid3R, twid3I)
    val resR3 = (a4Comp._1 + b4Comp._1 - c4Comp._1 - d4Comp._1).to[T]
    val resI3 = (a4Comp._2 + b4Comp._2 - c4Comp._2 - d4Comp._2).to[T]

    return (resR0, resI0, resR1, resI1, resR2, resI2, resR3, resI3)
  }

  /* Main (CPU/Host) function. */
  @virtualize
  def main() = {
    /* Read in file, load in signal (both real and imaginary) and determine
       signal size. */
    val inpFile = args(0)

    val inpData = loadCSV2D[T](inpFile, " ", "\n")

    val N = inpData.cols

    printMatrix(inpData)

    /* Set up input signals. */
    val sigReal = Array.tabulate(N){ i => inpData(0, i) }
    val sigImag = Array.tabulate(N){ i => inpData(1, i) }

    /* Compute number of stages. */
    val numStages = (log(N.to[Float]) / log(4.to[Float])).to[Int]

    val finReal = Array.empty[T](N)
    val finImag = Array.empty[T](N)
    val tempReal = Array.empty[T](N)
    val tempImag = Array.empty[T](N)

    for (i <- 0 until N) {
      tempReal(i) = sigReal(i)
      tempImag(i) = sigImag(i)
    }

    val twiddleR = Array.empty[T](N)
    val twiddleI = Array.empty[T](N)

    /* Compute all twiddle factors, which can then be index into.
       Note that we don't use all of them, but it is easier to
       compute them all across a range of N-values rather than
       try to figure out at what index a particular twiddle
       factor is located. */
    for (k <- 0 until N) {
      val nextEntr = -k.to[T]*(2.0 * PI / N.to[Float]).to[T]
      twiddleR(k) = cos(nextEntr.to[Float]).to[T]
      twiddleI(k) = sin(nextEntr.to[Float]).to[T]
    }

    /* Calculate for first numStages-1 stages. */
    for (i <- 0 until numStages-1) {
      for (r <- 0 until N/4) {
        val nextR0 = tempReal(r)
        val nextR1 = tempReal(r + N/4)
        val nextR2 = tempReal(r + 2*N/4)
        val nextR3 = tempReal(r + 3*N/4)

        val nextI0 = tempImag(r)
        val nextI1 = tempImag(r + N/4)
        val nextI2 = tempImag(r + 2*N/4)
        val nextI3 = tempImag(r + 3*N/4)

        val seg = ((r.to[Float]/
          pow(4.to[Float],i.to[Float])) *
          pow(4.to[Float],i.to[Float])).to[Int]

        val twid2R = twiddleR(2*seg)
        val twid2I = twiddleI(2*seg)
        val twid1R = twiddleR(seg)
        val twid1I = twiddleI(seg)
        val twid3R = twiddleR(3*seg)
        val twid3I = twiddleI(3*seg)

        val nextBFly = compBFly(nextR0, nextI0, nextR1, nextI1,
          nextR2, nextI2, nextR3, nextI3,
          twid2R, twid2I, twid1R, twid1I,
          twid3R, twid3I)

        finReal(4*r) = nextBFly._1
        finImag(4*r) = nextBFly._2
        finReal(4*r + 1) = nextBFly._3
        finImag(4*r + 1) = nextBFly._4
        finReal(4*r + 2) = nextBFly._5
        finImag(4*r + 2) = nextBFly._6
        finReal(4*r + 3) = nextBFly._7
        finImag(4*r + 3) = nextBFly._8
      }
      /* Update the temporary arrays. */
      for (p <- 0 until N) {
        tempReal(p) = finReal(p)
        tempImag(p) = finImag(p)
      }
    }

    /* Calculate for the last stage. */
    for (r <- 0 until N/4) {
      val stage = numStages - 1
      /* Could be made simpler using an Array declaration,
         but this works for now. */
      val nextR0 = tempReal(r)
      val nextR1 = tempReal(r + N/4)
      val nextR2 = tempReal(r + 2*N/4)
      val nextR3 = tempReal(r + 3*N/4)

      val nextI0 = tempImag(r)
      val nextI1 = tempImag(r + N/4)
      val nextI2 = tempImag(r + 2*N/4)
      val nextI3 = tempImag(r + 3*N/4)

      val twid2R = twiddleR(0)
      val twid2I = twiddleI(0)
      val twid1R = twiddleR(0)
      val twid1I = twiddleI(0)
      val twid3R = twiddleR(0)
      val twid3I = twiddleI(0)

      val nextBFly = compBFly(nextR0, nextI0, nextR1, nextI1,
        nextR2, nextI2, nextR3, nextI3,
        twid2R, twid2I, twid1R, twid1I,
        twid3R, twid3I)

      finReal(4*r) = nextBFly._1
      finImag(4*r) = nextBFly._2
      finReal(4*r + 1) = nextBFly._3
      finImag(4*r + 1) = nextBFly._4
      finReal(4*r + 2) = nextBFly._5
      finImag(4*r + 2) = nextBFly._6
      finReal(4*r + 3) = nextBFly._7
      finImag(4*r + 3) = nextBFly._8
    }

    /* Run on accelerator. */
    val result = FFT4(sigReal, sigImag, twiddleR, twiddleI, numStages, N)

    /* Checksum results. Note that we need to rescale the outputs by N
       since in the butterfly method we scale them by 1/4. */
    val goldReCksum = finReal.map(a => a.to[T]*N.to[T]).reduce{_+_}
    val goldImCksum = finImag.map(a => a.to[T]*N.to[T]).reduce{_+_}
    val resReCksum  = result._1.map(a => a.to[T]*N.to[T]).reduce{_+_}
    val resImCksum  = result._2.map(a => a.to[T]*N.to[T]).reduce{_+_}

    println("expected real cksum: " + goldReCksum)
    println("result real cksum: " + resReCksum)
    println("expected imag cksum: " + goldImCksum)
    println("result imag cksum:   " + resImCksum)

    /* Check to see if checksums match to a given threshold. */
    val cksum1 = (goldReCksum - resReCksum) < threshold
    val cksum2 = (goldImCksum - resImCksum) < threshold
    val cksum = cksum1 && cksum2

    println("CKSUM1: " + (goldReCksum - resReCksum))
    println("CKSUM2: " + (goldImCksum - resImCksum))
    println("PASS: " + cksum + " (FFT4)")
  }
}

