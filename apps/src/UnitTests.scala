import spatial._
import org.virtualized._

object InOutArg extends SpatialApp { // Regression (Unit) // Args: 5
  import IR._

  @virtualize
  def main() {
    // Declare SW-HW interface vals
    val x = ArgIn[Int]
    val y = ArgOut[Int]
    val N = args(0).to[Int]

    // Connect SW vals to HW vals
    setArg(x, N)

    // Create HW accelerator
    Accel {
      Pipe { y := x + 4 }
    }


    // Extract results from accelerator
    val result = getArg(y)

    // Create validation checks and debug code
    val gold = N + 4
    println("expected: " + gold)
    println("result: " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (InOutArg)")
  }
}

object MixedIOTest extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  @virtualize 
  def main(): Unit = { 
    val cst1 = 32
    val cst2 = 23
    val cst3 = 11
    val cst4 = 7
    val io1 = HostIO[Int]
    val io2 = HostIO[Int]
    val x1 = ArgIn[Int]
    val x2 = ArgIn[Int]
    val y1 = ArgOut[Int]
    val y2 = ArgOut[Int]
    val y3 = ArgOut[Int]
//    val m1 = DRAM[Int](16)
//    val m2 = DRAM[Int](16)
    setArg(io1, cst1)
    setArg(io2, cst2)
    setArg(x1, cst3)
    setArg(x2, cst4)
//    val data = Array.tabulate(16){i => i}
//    setMem(m1, data)

    Accel {
      Pipe { io1 := io1.value + 2}
      Pipe { io2 := io2.value + 4}
      Pipe { y2 := 999 }
      Pipe { y1 := x1.value + 6 }
      Pipe { y2 := x2.value + 8 }
 //     val sram1 = SRAM[Int](16)
 //     val sram2 = SRAM[Int](16)
 //     sram1 load m1
 //     sram2 load m1
 //     m2 store sram1
 //     Pipe { y3 := sram2(3) }
    }

    val r1 = getArg(io1)
    val g1 = cst1 + 2
    val r2 = getArg(io2)
    val g2 = cst2 + 4
    val r3 = getArg(y1)
    val g3 = cst3 + 6
    val r4 = getArg(y2)
    val g4 = cst4 + 8
//    val r5 = getMem(m2)
//    val g6 = data(3)
    val r6 = getArg(y3)
    println("expected: " + g1 + ", " + g2 + ", " + g3 + ", " + g4)
    println("received: " + r1 + ", " + r2 + ", " + r3 + ", " + r4)
//    printArray(r5, "Mem: ")
//   val cksum = r1 == g1 && r2 == g2 && r3 == g3 && r4 == g4 && r6 == g6 && data.zip(r5){_==_}.reduce{_&&_}
//    println("PASS: " + cksum + " (MixedIOTest)")
  }
}


// Args: None
object MultiplexedWriteTest extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  val tileSize = 16
  val I = 5
  val N = 192

  def multiplexedwrtest[W:Type:Num](w: Array[W], i: Array[W]): Array[W] = {
    val T = param(tileSize)
    val P = param(4)
    val weights = DRAM[W](N)
    val inputs  = DRAM[W](N)
    val weightsResult = DRAM[W](N*I)
    setMem(weights, w)
    setMem(inputs,i)
    Accel {
      val wt = SRAM[W](T)
      val in = SRAM[W](T)
      Sequential.Foreach(N by T){i =>
        wt load weights(i::i+T par 16)
        in load inputs(i::i+T par 16)

        // Some math nonsense (definitely not a correct implementation of anything)
        Foreach(I by 1){x =>
          val niter = Reg[Int]
          niter := x+1
          MemReduce(wt)(niter by 1){ i =>  // s0 write
            in
          }{_+_}
          weightsResult(i*I+x*T::i*I+x*T+T par 16) store wt //s1 read
        }
      }

    }
    getMem(weightsResult)
  }

  @virtualize
  def main() = {
    val w = Array.tabulate(N){ i => i % 256}
    val i = Array.tabulate(N){ i => i % 256 }

    val result = multiplexedwrtest(w, i)

    val gold = Array.tabulate(N/tileSize) { k =>
      Array.tabulate(I){ j => 
        val in = Array.tabulate(tileSize) { i => (j)*(k*tileSize + i) }
        val wt = Array.tabulate(tileSize) { i => k*tileSize + i }
        in.zip(wt){_+_}
      }.flatten
    }.flatten
    printArray(gold, "gold: ");
    printArray(result, "result: ");

    val cksum = gold.zip(result){_==_}.reduce{_&&_}
    println("PASS: " + cksum  + " (MultiplexedWriteTest)")
  }
}

// TODO: Make this actually check a bubbled NBuf (i.e.- s0 = wr, s2 = wr, s4 =rd, s1s2 = n/a)
// because I think this will break the NBuf SM since it won't detect drain completion properly
// Args: None
object BubbledWriteTest extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  val tileSize = 16
  val I = 5
  val N = 192

  def bubbledwrtest(w: Array[Int], i: Array[Int]): Array[Int] = {
    val T = param(tileSize)
    val P = param(4)
    val weights = DRAM[Int](N)
    val inputs  = DRAM[Int](N)
    val weightsResult = DRAM[Int](N*I)
    // val dummyWeightsResult = DRAM[Int](T)
    // val dummyOut = DRAM[Int](T)
    // val dummyOut2 = DRAM[Int](T)
    setMem(weights, w)
    setMem(inputs,i)
    Accel {

      val wt = SRAM[Int](T)
      val in = SRAM[Int](T)
      Sequential.Foreach(N by T){i =>
        wt load weights(i::i+T par 16)
        in load inputs(i::i+T par 16)

        Foreach(I by 1){x =>
          val niter = Reg[Int]
          niter := x+1
          MemReduce(wt)(niter by 1){ k =>  // s0 write
            in
          }{_+_}
          val dummyReg1 = Reg[Int]
          val dummyReg2 = Reg[Int]
          val dummyReg3 = Reg[Int]
          Foreach(T by 1) { i => dummyReg1 := in(i)} // s1 do not touch
          Foreach(T by 1) { i => dummyReg2 := wt(i)} // s2 read
          Foreach(T by 1) { i => dummyReg3 := in(i)} // s3 do not touch
          weightsResult(i*I+x*T::i*I+x*T+T par 16) store wt //s4 read
        }
      }

    }
    getMem(weightsResult)
  }

  @virtualize
  def main() = {
    val w = Array.tabulate(N){ i => i % 256}
    val i = Array.tabulate(N){ i => i % 256 }

    val result = bubbledwrtest(w, i)

    // // Non-resetting SRAM check
    // val gold = Array.tabulate(N/tileSize) { k =>
    //   Array.tabulate(I){ j => 
    //     Array.tabulate(tileSize) { i => 
    //       ( 1 + (j+1)*(j+2)/2 ) * (i + k*tileSize)
    //     }
    //   }.flatten
    // }.flatten
    // Resetting SRAM check
    val gold = Array.tabulate(N/tileSize) { k =>
      Array.tabulate(I){ j => 
        val in = Array.tabulate(tileSize) { i => (j)*(k*tileSize + i) }
        val wt = Array.tabulate(tileSize) { i => k*tileSize + i }
        in.zip(wt){_+_}
      }.flatten
    }.flatten
    printArray(gold, "gold: ")
    printArray(result, "result: ")

    val cksum = gold.zip(result){_==_}.reduce{_&&_}
    println("PASS: " + cksum  + " (BubbledWriteTest)")


  }
}

object FileSplitter extends SpatialApp { // Regression (Unit) // Args: 6
  import IR._

  @virtualize
  def main() {
    // Declare SW-HW interface vals
    val x = ArgIn[Int]
    val y = ArgOut[Int]
    val N = args(0).to[Int]

    // Connect SW vals to HW vals
    setArg(x, N)

    // Create HW accelerator
    Accel {
      Pipe(5 by 1) { i => 
        Pipe(10 by 1) { j => 
          Pipe {y := 3*(x + 4 + j + i)+x/4}
        }
      }
    }


    // Extract results from accelerator
    val result = getArg(y)

    // Create validation checks and debug code
    val gold = 3*(N + 4 + 4 + 9) + N / 4
    println("expected: " + gold)
    println("result: " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (FileSplitter)")
  }
}

object Niter extends SpatialApp { // Regression (Unit) // Args: 100
  import IR._
  
  val constTileSize = 16

  @virtualize
  def nIterTest[T:Type:Num](len: Int): T = {
    val innerPar = 1 (1 -> 1)
    val tileSize = constTileSize (constTileSize -> constTileSize)
    bound(len) = 9216

    val N = ArgIn[Int]
    val out = ArgOut[T]
    setArg(N, len)

    Accel {
      Sequential {
        Sequential.Foreach(N by tileSize){ i =>
          val redMax = Reg[Int](999)
          Pipe{ redMax := min(tileSize, N.value-i) }
          val accum = Reduce(Reg[T](0.to[T]))(redMax par innerPar){ ii =>
            (i + ii).to[T]
          } {_+_}
          Pipe { out := accum }
        }
      }
    }

    getArg(out)
  }

  @virtualize
  def main() {
    val len = args(0).to[Int]

    val result = nIterTest[Int](len)

    val m = (len-1)%constTileSize + 1
    val b1 = m*(m-1)/2
    val gold = b1 + (len - m)*m
    println("expected: " + gold)
    println("result:   " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (Niter)")
  }
}

object MemTest1D extends SpatialApp { // Regression (Unit) // Args: 7
  import IR._

  @virtualize
  def main() {

    // Declare SW-HW interface vals
    val x = ArgIn[Int]
    val y = ArgOut[Int]
    val N = args(0).to[Int]

    // Connect SW vals to HW vals
    setArg(x, N)

    // Create HW accelerator
    Accel {
      val mem = SRAM[Int](384)
      Sequential.Foreach(384 by 1) { i =>
        mem(i) = x + i.to[Int]
      }
      Pipe { y := mem(383) }
    }


    // Extract results from accelerator
    val result = getArg(y)

    // Create validation checks and debug code
    val gold = N+383
    println("expected: " + gold)
    println("result: " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (MemTest1D)")
  }
}

object MemTest2D extends SpatialApp { // Regression (Unit) // Args: 7
  import IR._

  @virtualize
  def main() {

    // Declare SW-HW interface vals
    val x = ArgIn[Int]
    val y = ArgOut[Int]
    val N = args(0).to[Int]

    // Connect SW vals to HW vals
    setArg(x, N)

    // Create HW accelerator
    Accel {
      val mem = SRAM[Int](64, 128)
      Sequential.Foreach(64 by 1, 128 by 1) { (i,j) =>
        mem(i,j) = x + (i*128+j).to[Int]
      }
      Pipe { y := mem(63,127) }
    }


    // Extract results from accelerator
    val result = getArg(y)

    // Create validation checks and debug code
    val gold = N+63*128+127
    println("expected: " + gold)
    println("result: " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (MemTest2D)")
  }
}

object FifoLoad extends SpatialApp { // Regression (Unit) // Args: 192
  import IR._

  def fifoLoad[T:Type:Num](srcHost: Array[T], N: Int) = {
    val tileSize = 16 (64 -> 64)

    val size = ArgIn[Int]
    setArg(size, N)

    val srcFPGA = DRAM[T](size)
    val dstFPGA = DRAM[T](size)
    setMem(srcFPGA, srcHost)

    Accel {
      val f1 = FIFO[T](tileSize)
      Sequential.Foreach(size by tileSize) { i =>
        f1 load srcFPGA(i::i + tileSize par 16)
        val b1 = SRAM[T](tileSize)
        Foreach(tileSize by 1) { i =>
          b1(i) = f1.deq()
        }
        dstFPGA(i::i + tileSize par 16) store b1
      }
      ()
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() {
    val arraySize = args(0).to[Int]

    val src = Array.tabulate(arraySize){i => i % 256}
    val dst = fifoLoad(src, arraySize)

    val gold = src

    println("Sent in: ")
    (0 until arraySize) foreach { i => print(gold(i) + " ") }
    println("\nGot out:")
    (0 until arraySize) foreach { i => print(dst(i) + " ") }
    println("")

    val cksum = dst.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (FifoLoad)")


  }
}

object SimpleSequential extends SpatialApp { // Regression (Unit) // Args: 5 8
  import IR._

  def simpleSeq(xIn: Int, yIn: Int): Int = {
    val innerPar = 1 (1 -> 1)
    val tileSize = 64 (64 -> 64)

    val x = ArgIn[Int]
    val y = ArgIn[Int]
    val out = ArgOut[Int]
    setArg(x, xIn)
    setArg(y, yIn)
    Accel {
      val bram = SRAM[Int](tileSize)
      Foreach(tileSize by 1 par innerPar){ ii =>
        bram(ii) = x.value * ii
      }
      out := bram(y.value)
    }
    getArg(out)
  }

  @virtualize
  def main() {
    val x = args(0).to[Int]
    val y = args(1).to[Int]
    val result = simpleSeq(x, y)

    val a1 = Array.tabulate(64){i => x * i}
    val gold = a1(y)

    println("expected: " + gold)
    println("result:   " + result)
    val chkSum = result == gold
    // assert(chkSum)
    println("PASS: " + chkSum + " (SimpleSeq)")
  }
}


object DeviceMemcpy extends SpatialApp { // Regression (Unit) // Args: 50
  import IR._

  val N = 192
  type T = Int
  def memcpyViaFPGA(srcHost: Array[T]): Array[T] = {
    val fpgaMem = DRAM[Int](N)
    setMem(fpgaMem, srcHost)

    val y = ArgOut[Int]
    Accel { y := 10 }

    getMem(fpgaMem)
  }

  @virtualize
  def main() {
    val arraySize = N
    val c = args(0).to[Int]

    val src = Array.tabulate(arraySize){i => i*c }
    val dst = memcpyViaFPGA(src)
    println("Sent in: ")
    for (i <- 0 until arraySize){ print(src(i) + " ") }
    println("\nGot out: ")
    for (i <- 0 until arraySize){ print(dst(i) + " ") }
    println("")
    val chkSum = dst.zip(src){_ == _}.reduce{_&&_}
    println("PASS: " + chkSum + " (DeviceMemcpy)")
  }
}

object SimpleTileLoadStore extends SpatialApp { // Regression (Unit) // Args: 100
  import IR._

  val N = 192

  @virtualize
  def simpleLoadStore[T:Type:Num](srcHost: Array[T], value: T) = {
    val loadPar  = 1 (1 -> 1)
    val storePar = 1 (1 -> 1)
    val tileSize = 16 (16 -> 16)

    val srcFPGA = DRAM[T](N)
    val dstFPGA = DRAM[T](N)
    setMem(srcFPGA, srcHost)

    val x = ArgIn[T]
    setArg(x, value)
    Accel {
      Sequential.Foreach(N by tileSize par 2) { i =>
        val b1 = SRAM[T](tileSize)

        b1 load srcFPGA(i::i+tileSize par 1)

        val b2 = SRAM[T](tileSize)
        Foreach(tileSize by 1 par 4) { ii =>
          b2(ii) = b1(ii) * x
        }

        dstFPGA(i::i+tileSize par 1) store b2
      }
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() {
    val arraySize = N
    val value = args(0).to[Int]

    val src = Array.tabulate[Int](arraySize) { i => i % 256 }
    val dst = simpleLoadStore(src, value)

    val gold = src.map { _ * value }

    println("Sent in: ")
    (0 until arraySize) foreach { i => print(gold(i) + " ") }
    println("Got out: ")
    (0 until arraySize) foreach { i => print(dst(i) + " ") }
    println("")

    val cksum = dst.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (SimpleTileLoadStore)")
  }
}


object SingleFifoLoad extends SpatialApp { // Regression (Unit) // Args: 384
  import IR._
  
  val tileSize = 32

  @virtualize
  def singleFifoLoad[T:Type:Num](src1: Array[T], in: Int) = {

    val P1 = 4 (16 -> 16)

    val N = ArgIn[Int]
    setArg(N, in)

    val src1FPGA = DRAM[T](N)
    val out = ArgOut[T]
    setMem(src1FPGA, src1)

    Accel {
      val f1 = FIFO[T](tileSize)
      Foreach(N by tileSize) { i =>
        f1 load src1FPGA(i::i+tileSize par P1)
        val accum = Reduce(Reg[T](0.to[T]))(tileSize by 1 par 1){i =>
          f1.deq()
        }{_+_}
        Pipe { out := accum }
      }
      ()
    }
    getArg(out)
  }

  @virtualize
  def main() {
    val arraySize = args(0).to[Int]

    val src1 = Array.tabulate(arraySize) { i => i % 256}
    val out = singleFifoLoad(src1, arraySize)

    val sub1_for_check = Array.tabulate(arraySize-tileSize) {i => i % 256}

    // val gold = src1.zip(src2){_*_}.zipWithIndex.filter( (a:Int, i:Int) => i > arraySize-64).reduce{_+_}
    val gold = src1.reduce{_+_} - sub1_for_check.reduce(_+_)
    println("gold: " + gold)
    println("out: " + out)

    val cksum = out == gold
    println("PASS: " + cksum + " (SingleFifoLoad)")
  }
}

object ParFifoLoad extends SpatialApp { // Regression (Unit) // Args: 384
  import IR._

  val tileSize = 64
  def parFifoLoad[T:Type:Num](src1: Array[T], src2: Array[T], src3: Array[T], in: Int) = {

    val P1 = 1 (16 -> 16)

    val N = ArgIn[Int]
    setArg(N, in)

    val src1FPGA = DRAM[T](N)
    val src2FPGA = DRAM[T](N)
    val src3FPGA = DRAM[T](N)
    val out = ArgOut[T]
    setMem(src1FPGA, src1)
    setMem(src2FPGA, src2)
    setMem(src3FPGA, src3)

    Accel {
      val f1 = FIFO[T](tileSize)
      val f2 = FIFO[T](tileSize)
      val f3 = FIFO[T](tileSize)
      Foreach(N by tileSize) { i =>
        Parallel {
          f1 load src1FPGA(i::i+tileSize par P1)
          f2 load src2FPGA(i::i+tileSize par P1)
          f3 load src3FPGA(i::i+tileSize par P1)
        }
        val accum = Reduce(Reg[T](0.to[T]))(tileSize by 1){i =>
          f1.deq() * f2.deq() * f3.deq()
        }{_+_}
        Pipe { out := accum }
      }
      ()
    }
    getArg(out)
  }

  @virtualize
  def main() {
    val arraySize = args(0).to[Int]

    val src1 = Array.tabulate(arraySize) { i => i % 4 }
    val src2 = Array.tabulate(arraySize) { i => i % 4 + 16}
    val src3 = Array.tabulate(arraySize) { i => i % 4 + 2*16}
    val out = parFifoLoad(src1, src2, src3, arraySize)

    val sub1_for_check = Array.tabulate(arraySize-tileSize) {i => i % 4}
    val sub2_for_check = Array.tabulate(arraySize-tileSize) {i => i % 4 + 16}
    val sub3_for_check = Array.tabulate(arraySize-tileSize) {i => i % 4 + 2*16}

    // val gold = src1.zip(src2){_*_}.zipWithIndex.filter( (a:Int, i:Int) => i > arraySize-64).reduce{_+_}
    val gold = src1.zip(src2){_*_}.zip(src3){_*_}.reduce{_+_} - sub1_for_check.zip(sub2_for_check){_*_}.zip(sub3_for_check){_*_}.reduce(_+_)
    println("gold: " + gold)
    println("out: " + out)

    val cksum = out == gold
    println("PASS: " + cksum + " (ParFifoLoad)")
  }
}



object FifoLoadStore extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  val N = 32

  def fifoLoadStore[T:Type:Bits](srcHost: Array[T]) = {
    val tileSize = N

    val srcFPGA = DRAM[T](N)
    val dstFPGA = DRAM[T](N)
    setMem(srcFPGA, srcHost)

    Accel {
      val f1 = FIFO[T](tileSize)
      // Parallel {
      Sequential {
        f1 load srcFPGA(0::tileSize par 16)
        dstFPGA(0::tileSize par 16) store f1
      }
      // Pipe(tileSize by 1) { i => // This pipe forces the loadstore to run for enough iters
      //   dummyOut := i
      // }
      // }
      ()
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() {
    val arraySize = N

    val src = Array.tabulate(arraySize) { i => i % 256 }
    val dst = fifoLoadStore(src)

    val gold = src

    println("gold:")
    (0 until arraySize) foreach { i => print(gold(i) + " ") }
    println("")
    println("dst:")
    (0 until arraySize) foreach { i => print(dst(i) + " ") }
    println("")

    val cksum = dst.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (FifoLoadStore)")
  }
}



object SimpleReduce extends SpatialApp { // Regression (Unit) // Args: 7
  import IR._

  val N = 16.to[Int]

  def simpleReduce[T:Type:Num](xin: T) = {

    val x = ArgIn[T]
    val out = ArgOut[T]
    setArg(x, xin)

    Accel {
      out := Reduce(Reg[T](0.to[T]))(N by 1){ ii =>
        x.value * ii.to[T]
      }{_+_}
    }
    getArg(out)
  }

  @virtualize
  def main() {
    val x = args(0).to[Int]

    val result = simpleReduce(x)

    val gold = Array.tabulate(N){i => x * i}.reduce{_+_}
    println("expected: " + gold)
    println("result:   " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (SimpleReduce)")
  }
}




object SimpleFold extends SpatialApp { // Regression (Unit) // Args: 1920
  import IR._

  val constTileSize = 16

  def simple_fold[T:Type:Num](src: Array[T]) = {
    val outerPar = 1 (16 -> 16)
    val innerPar = 1 (16 -> 16)
    val tileSize = constTileSize (constTileSize -> constTileSize)
    val len = src.length; bound(len) = 9216

    val N = ArgIn[Int]
    val out = ArgOut[T]
    setArg(N, len)

    val v1 = DRAM[T](N)
    setMem(v1, src)

    Accel {
      val accum = Reg[T](0.to[T])
      Reduce(accum)(N by tileSize par outerPar){ i =>
        val b1 = SRAM[T](tileSize)
        b1 load v1(i::i+tileSize par 16)
        Reduce(Reg[T](0.to[T]))(tileSize par innerPar){ ii =>
          b1(ii)
        } {_+_}
      } {_+_}
      Pipe { out := accum }
    }

    getArg(out)
  }

  @virtualize
  def main() {
    val len = args(0).to[Int]

    val src = Array.tabulate(len){i => i % 256}
    val result = simple_fold(src)

    val gold = src.reduce{_+_}
    println("expected: " + gold)
    println("result:   " + result)

    val cksum = result == gold
    println("PASS: " + cksum + " (SimpleFold) * Here is an example for how to leave regression comments")
  }
}

object Memcpy2D extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  val R = 16
  val C = 16

  def memcpy_2d[T:Type:Num](src: Array[T], rows: Int, cols: Int): Array[T] = {
    val tileDim1 = R
    val tileDim2 = C

    val rowsIn = rows
    val colsIn = cols

    val srcFPGA = DRAM[T](rows, cols)
    val dstFPGA = DRAM[T](rows, cols)

    // Transfer data and start accelerator
    setMem(srcFPGA, src)

    Accel {
      Sequential.Foreach(rowsIn by tileDim1, colsIn by tileDim2) { (i,j) =>
        val tile = SRAM[T](tileDim1, tileDim2)
        tile load srcFPGA(i::i+tileDim1, j::j+tileDim2 par 1)
        dstFPGA (i::i+tileDim1, j::j+tileDim2 par 1) store tile
      }
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() = {
    val rows = R
    val cols = C
    val src = Array.tabulate(rows*cols) { i => i % 256 }

    val dst = memcpy_2d(src, rows, cols)

    printArray(src, "src:")
    printArray(dst, "dst:")

    val cksum = dst.zip(src){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (Memcpy2D)")

  }
}

object UniqueParallelLoad extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  val dim0 = 144
  val dim1 = 96

  def awkwardload[T:Type:Num](src1: Array[T], src2: Array[T]):T = {


    val mat = DRAM[T](dim0, dim1)
    val other = DRAM[T](dim1, dim1)
    val result = ArgOut[T]
    // Transfer data and start accelerator
    setMem(mat, src1)
    setMem(other, src2)

    Accel {
      val s1 = SRAM[T](dim0, dim1)
      val s2 = SRAM[T](dim1, dim1)
      Parallel{
        s1 load mat(0::dim0, 0::dim1)
        s2 load other(0::dim1, 0::dim1)
      }

      val accum = Reg[T](0.to[T])
      Reduce(accum)(dim0 by 1, dim1 by 1) { (i,j) =>
        s1(i,j)
      }{_+_}
      val accum2 = Reg[T](0.to[T])
      Reduce(accum2)(dim1 by 1, dim1 by 1) { (i,j) =>
        s2(i,j)
      }{_+_}
      result := accum.value + accum2.value
    }
    getArg(result)
  }

  @virtualize
  def main() = {
    val srcA = Array.tabulate(dim0) { i => Array.tabulate(dim1){ j => ((j + i) % 8) }}
    val srcB = Array.tabulate(dim1) { i => Array.tabulate(dim1){ j => ((j + i) % 8) }}

    val dst = awkwardload(srcA.flatten, srcB.flatten)

    val goldA = srcA.map{ row => row.map{el => el}.reduce{_+_}}.reduce{_+_}
    val goldB = srcB.map{ row => row.map{el => el}.reduce{_+_}}.reduce{_+_}
    val gold = goldA + goldB

    println("Gold: " + gold)
    println("result: " + dst)
    val cksum = gold == dst
    println("PASS: " + cksum + " (UniqueParallelLoad)")

  }
}


object BlockReduce1D extends SpatialApp { // Regression (Unit) // Args: 1920
  import IR._

  val tileSize = 64
  val p = 1

  @virtualize
  def blockreduce_1d[T:Type:Num](src: Array[T], size: Int) = {
    val sizeIn = ArgIn[Int]
    setArg(sizeIn, size)

    val srcFPGA = DRAM[T](sizeIn)
    val dstFPGA = DRAM[T](tileSize)

    setMem(srcFPGA, src)

    Accel {
      val accum = SRAM[T](tileSize)
      MemReduce(accum)(sizeIn by tileSize par p){ i  =>
        val tile = SRAM[T](tileSize)
        tile load srcFPGA(i::i+tileSize par 16)
        tile
      }{_+_}
      dstFPGA(0::tileSize par 16) store accum
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() = {
    val size = args(0).to[Int]
    val src = Array.tabulate(size){i => i % 256}

    val dst = blockreduce_1d(src, size)

    val tsArr = Array.tabulate(tileSize){i => i % 256}
    val perArr = Array.tabulate(size/tileSize){i => i}
    val gold = tsArr.map{ i => perArr.map{j => src(i+j*tileSize)}.reduce{_+_}}

    printArray(gold, "src:")
    printArray(dst, "dst:")
    val cksum = dst.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (BlockReduce1D)")

    //    (0 until tileSize) foreach { i => assert(dst(i) == gold(i)) }
  }
}

object UnalignedLd extends SpatialApp { // Regression (Unit) // Args: 100 9
  import IR._

  val N = 19200

  val paddedCols = 1920

  @virtualize
  def unaligned_1d[T:Type:Num](src: Array[T], ii: Int, numCols: Int) = {
    val iters = ArgIn[Int]
    val srcFPGA = DRAM[T](paddedCols)
    val ldSize = ArgIn[Int]
    val acc = ArgOut[T]

    setArg(iters, ii)
    setArg(ldSize, numCols)
    setMem(srcFPGA, src)

    Accel {
      val ldSizeReg = Reg[Int](0.to[Int])
      ldSizeReg := ldSize.value
      val accum = Reduce(Reg[T](0.to[T]))(iters by 1 par 1) { k =>
        val mem = SRAM[T](16)
        mem load srcFPGA(k*ldSizeReg.value::(k+1)*ldSizeReg.value)
        Reduce(Reg[T](0.to[T]))(ldSizeReg.value by 1){i => mem(i) }{_+_}
      }{_+_}
      acc := accum
    }
    getArg(acc)
  } 

  @virtualize
  def main() = {
    // val size = args(0).to[Int]
    val ii = args(0).to[Int]
    val cols = args(1).to[Int]
    val size = paddedCols
    val src = Array.tabulate(size) {i => i % 256 }

    val dst = unaligned_1d(src, ii, cols)

    val goldArray = Array.tabulate(ii*cols){ i => i % 256 }
    val gold = goldArray.reduce{_+_}

    printArray(src, "src")
    printArray(goldArray, "gold")

    println("src:" + gold)
    println("dst:" + dst)
    val cksum = gold == dst
    println("PASS: " + cksum + " (UnalignedLd)")

    //    (0 until tileSize) foreach { i => assert(dst(i) == gold(i)) }
  }
}


// Args: 192 384
object BlockReduce2D extends SpatialApp { // Regression (Unit) // Args: 192 384
  import IR._

  val N = 1920
  val tileSize = 16

  @virtualize
  def blockreduce_2d[T:Type:Num](src: Array[T], rows: Int, cols: Int) = {
    val rowsIn = ArgIn[Int]; setArg(rowsIn, rows)
    val colsIn = ArgIn[Int]; setArg(colsIn, cols)

    val srcFPGA = DRAM[T](rowsIn, colsIn)
    val dstFPGA = DRAM[T](tileSize, tileSize)

    setMem(srcFPGA, src)

    Accel {
      val accum = SRAM[T](tileSize,tileSize)
      MemReduce(accum)(rowsIn by tileSize, colsIn by tileSize par 2){ (i,j)  =>
        val tile = SRAM[T](tileSize,tileSize)
        tile load srcFPGA(i::i+tileSize, j::j+tileSize  par 16)
        tile
      }{_+_}
      dstFPGA(0::tileSize, 0::tileSize par 16) store accum
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() = {
    val numRows = args(0).to[Int]
    val numCols = args(1).to[Int]
    val src = Array.tabulate(numRows) { i => Array.tabulate(numCols) { j => (i*numCols + j)%256 } } // Standard array
    val flatsrc = src.flatten

    val dst = blockreduce_2d(src.flatten, numRows, numCols)

    val numHorizontal = numRows/tileSize
    val numVertical = numCols/tileSize
    val numBlocks = numHorizontal*numVertical
    // val gold = Array.tabulate(tileSize){i =>
    //   Array.tabulate(tileSize){j =>

    //     flatsrc(i*tileSize*tileSize + j*tileSize) }}.flatten
    // }.reduce{(a,b) => a.zip(b){_+_}}
    val a1 = Array.tabulate(tileSize) { i => i }
    val a2 = Array.tabulate(tileSize) { i => i }
    val a3 = Array.tabulate(numHorizontal) { i => i }
    val a4 = Array.tabulate(numVertical) { i => i }
    val gold = a1.map{i=> a2.map{j => a3.map{ k=> a4.map {l=> 
      flatsrc(i*numCols + j + k*tileSize*tileSize + l*tileSize) }}.flatten.reduce{_+_}
    }}.flatten

    // val first_el = (0 until numVertical).map{ case j => (0 until numHorizontal).map {case i => src.flatten(tileSize*j + tileSize*tileSize*i)}}.flatten.reduce{_+_}
    // val first_collapse_cols = ((numVertical*tileSize)/2)*(numVertical-1)
    // val last_collapse_cols = (( numVertical*tileSize*tileSize*(numHorizontal-1) + (first_collapse_cols + numVertical*tileSize*tileSize*(numHorizontal-1)) ) / 2)*(numVertical-1)
    // val first_collapse_rows = if (numHorizontal == 1) {first_collapse_cols} else { ((first_collapse_cols + last_collapse_cols) / 2) * (numHorizontal-1) }
    // // TODO: Why does DEG crash if I add first_collapse_rows rather???
    // val gold = Array.tabulate(tileSize*tileSize) { i => first_collapse_cols + i*numBlocks }

    printArray(gold, "src:")
    printArray(dst, "dst:")
    // dst.zip(gold){_==_} foreach {println(_)}
    val cksum = dst.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (BlockReduce2D)")

    //    (0 until tileSize) foreach { i => assert(dst(i) == gold(i)) }
  }
}

// Args: none
object GatherStore extends SpatialApp { // Regression (Sparse) // Args: none
  import IR._

  val tileSize = 128
  val numAddr = tileSize * 100
  val numData = tileSize * 1000

  val P = param(1)

  @virtualize
  def gatherStore[T:Type:Num](addrs: Array[Int], offchip_data: Array[T]) = {

    val srcAddrs = DRAM[Int](numAddr)
    val gatherData = DRAM[T](numData)
    val denseResult = DRAM[T](numAddr)

    setMem(srcAddrs, addrs)
    setMem(gatherData, offchip_data)

    Accel {
      val addrs = SRAM[Int](tileSize)
      Sequential.Foreach(numAddr by tileSize) { i =>
        val sram = SRAM[T](tileSize)
        addrs load srcAddrs(i::i + tileSize par P)
        sram gather gatherData(addrs par P, tileSize)
        denseResult(i::i+tileSize) store sram
      }
    }

    getMem(denseResult)
  }

  @virtualize
  def main() = {

    val addrs = Array.tabulate(numAddr) { i =>
      // i*2 // for debug
      // TODO: Macro-virtualized winds up being particularly ugly here..
      if      (i == 4)  lift(199)
      else if (i == 6)  lift(numData-2)
      else if (i == 7)  lift(191)
      else if (i == 8)  lift(203)
      else if (i == 9)  lift(381)
      else if (i == 10) lift(numData-97)
      else if (i == 15) lift(97)
      else if (i == 16) lift(11)
      else if (i == 17) lift(99)
      else if (i == 18) lift(245)
      else if (i == 94) lift(3)
      else if (i == 95) lift(1)
      else if (i == 83) lift(101)
      else if (i == 70) lift(203)
      else if (i == 71) lift(numData-1)
      else if (i % 2 == 0) i*2
      else i*2 + numData/2
    }

    val offchip_data = Array.tabulate[Int](numData){ i => i }

    val received = gatherStore(addrs, offchip_data)

    val gold = Array.tabulate(numAddr){ i => offchip_data(addrs(i)) }

    printArray(gold, "gold:")
    printArray(received, "received:")
    val cksum = received.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (GatherStore)")
  }
}

object LoadScatter extends SpatialApp { // Regression (Sparse) // Args: none
  import IR._

  val tileSize = 128
  val numAddr = tileSize * 10
  val numData = tileSize * 100

  val P = param(1)

  @virtualize
  def loadScatter[T:Type:Num](addrs: Array[Int], offchip_data: Array[T]) = {

    val srcAddrs = DRAM[Int](numAddr)
    val inData = DRAM[T](numData)
    val scatterResult = DRAM[T](numData)

    setMem(srcAddrs, addrs)
    setMem(inData, offchip_data)

    Accel {
      val addrs = SRAM[Int](tileSize)
      Sequential.Foreach(numAddr by tileSize) { i =>
        val sram = SRAM[T](tileSize)
        addrs load srcAddrs(i::i + tileSize par P)
        sram gather inData(addrs par P, tileSize)
        scatterResult(addrs par P, tileSize) scatter sram
      }
    }

    getMem(scatterResult)
  }

  @virtualize
  def main() = {

//    val addrs = Array.tabulate(numAddr) { i =>
//      // i*2 // for debug
//      // TODO: Macro-virtualized winds up being particularly ugly here..
//      if      (i == 4)  lift(199)
//      else if (i == 6)  lift(numData-2)
//      else if (i == 7)  lift(191)
//      else if (i == 8)  lift(203)
//      else if (i == 9)  lift(381)
//      else if (i == 10) lift(numData-97)
//      else if (i == 15) lift(97)
//      else if (i == 16) lift(11)
//      else if (i == 17) lift(99)
//      else if (i == 18) lift(245)
//      else if (i == 94) lift(3)
//      else if (i == 95) lift(1)
//      else if (i == 83) lift(101)
//      else if (i == 70) lift(203)
//      else if (i == 71) lift(numData-1)
//      else if (i % 2 == 0) i*2
//      else i*2 + numData/2
//    }

    val nd = numData
    val na = numAddr
    val mul = 2
    val addrs = Array.tabulate(na) { i => i * mul }
    val offchip_data = Array.tabulate[Int](nd){ i => i * 10 }

    val received = loadScatter(addrs, offchip_data)

    def contains(a: Array[Int], elem: Int) = {
      a.map { e => e == elem }.reduce {_||_}
    }

    def indexOf(a: Array[Int], elem: Int) = {
      val indices = Array.tabulate(a.length.to[Int]) { i => i }
      if (contains(a, elem)) {
        a.zip(indices) { case (e, idx) => if (e == elem) idx else lift(0) }.reduce {_+_}
      } else lift(-1)
    }

    val gold = Array.tabulate(nd) { i =>
//      if (contains(addrs, i)) offchip_data(indexOf(addrs, i)) else lift(0)
      if (contains(addrs, i)) offchip_data(i) else lift(0)
    }

    printArray(offchip_data, "data:")
    printArray(addrs, "addrs:")
    printArray(gold, "gold:")
    printArray(received, "received:")
    val cksum = received.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (LoadScatter)")
  }
}

object ScatterGather extends SpatialApp { // DISABLED Regression (Sparse) // Args: none
  import IR._

  val N = 1920

  val tileSize = 384
  val maxNumAddrs = 1536
  val offchip_dataSize = maxNumAddrs*6
  val P = param(1)

  @virtualize
  def scattergather[T:Type:Num](addrs: Array[Int], offchip_data: Array[T], size: Int, dataSize: Int) = {

    val srcAddrs = DRAM[Int](maxNumAddrs)
    val gatherData = DRAM[T](offchip_dataSize)
    val scatterResult = DRAM[T](offchip_dataSize)

    setMem(srcAddrs, addrs)
    setMem(gatherData, offchip_data)

    Accel {
      val addrs = SRAM[Int](maxNumAddrs)
      Sequential.Foreach(maxNumAddrs by tileSize) { i =>
        val sram = SRAM[T](maxNumAddrs)
        addrs load srcAddrs(i::i + tileSize par P)
        sram gather gatherData(addrs par P, tileSize)
        scatterResult(addrs par P, tileSize) scatter sram // TODO: What to do about parallel scatter when sending to same burst simultaneously???
      }
    }

    getMem(scatterResult)
  }

  @virtualize
  def main() = {
    // val size = args(0).to[Int]


    val size = maxNumAddrs
    val dataSize = offchip_dataSize
    val addrs = Array.tabulate(size) { i =>
      // i*2 // for debug
      // TODO: Macro-virtualized winds up being particularly ugly here..
      if      (i == 4)  lift(199)
      else if (i == 6)  lift(offchip_dataSize-2)
      else if (i == 7)  lift(191)
      else if (i == 8)  lift(203)
      else if (i == 9)  lift(381)
      else if (i == 10) lift(offchip_dataSize-97)
      else if (i == 15) lift(97)
      else if (i == 16) lift(11)
      else if (i == 17) lift(99)
      else if (i == 18) lift(245)
      else if (i == 94) lift(3)
      else if (i == 95) lift(1)
      else if (i == 83) lift(101)
      else if (i == 70) lift(203)
      else if (i == 71) lift(offchip_dataSize-1)
      else if (i % 2 == 0) i*2
      else i*2 + offchip_dataSize/2
    }
    val offchip_data = Array.fill(dataSize){ random[Int](dataSize) }
    // val offchip_data = Array.tabulate (dataSize) { i => i}

    val received = scattergather(addrs, offchip_data, size, dataSize)

    // printArr(addrs, "addrs: ")
    // (0 until dataSize) foreach { i => println(i + " match? " + (addrs.map{a => a==i}.reduce{_||_}) ) }
    val gold = Array.tabulate(dataSize){ i => if (addrs.map{a => a == i}.reduce{_||_}) offchip_data(i) else lift(0) }

    printArray(gold, "gold:")
    printArray(received, "received:")
    val cksum = received.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (ScatterGather)")
  }
}



object SequentialWrites extends SpatialApp { // Regression (Unit) // Args: 7
  import IR._

  val tileSize = 16
  val N = 5

  def sequentialwrites[A:Type:Num](srcData: Array[A], x: A) = {
    val T = param(tileSize)
    val P = param(4)
    val src = DRAM[A](T)
    val dst = DRAM[A](T)
    val xx = ArgIn[A]
    setArg(xx, x)
    setMem(src, srcData)
    Accel {
      val in = SRAM[A](T)
      in load src(0::T par 16)

      MemReduce(in)(1 until (N+1) by 1){ ii =>
        val d = SRAM[A](T)
        Foreach(T by 1){ i => d(i) = xx.value + i.to[A] }
        d
      }{_+_}

      dst(0::T par 16) store in
    }
    getMem(dst)
  }

  @virtualize
  def main() = {
    val x = args(0).to[Int]
    val srcData = Array.tabulate(tileSize){ i => i % 256 }

    val result = sequentialwrites(srcData, x)

    val first = x*N
    val gold = Array.tabulate(tileSize) { i => first + i*N}

    printArray(gold, "gold: ")
    printArray(result, "result: ")
    val cksum = result.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum  + " (SequentialWrites)")

  }
}

// Args: None
object ChangingCtrMax extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  val tileSize = 16
  val N = 5

  def changingctrmax[T:Type:Num](): Array[T] = {
    val result = DRAM[T](16)
    Accel {
      val rMem = SRAM[T](16)
      Sequential.Foreach(16 by 1) { i =>
        val accum = Reduce(0)(i by 1){ j => j }{_+_}
        rMem(i) = accum.value.to[T]
      }
      result(0::16 par 16) store rMem
    }
    getMem(result)
  }

  @virtualize
  def main() = {
    //val i = args(0).to[Int] [Unused]

    val result = changingctrmax[Int]()

    // Use strange if (i==0) b/c iter1: 0 by 1 and iter2: 1 by 1 both reduce to 0
    val gold = Array.tabulate(tileSize) { i => if (i==0) lift(0) else (i-1)*i/2}

    printArray(gold, "gold: ")
    printArray(result, "result: ")
    val cksum = result.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum  + " (ChangingCtrMax)")

  }
}


object FifoPushPop extends SpatialApp { // Regression (Unit) // Args: 384
  import IR._

  def fifopushpop(N: Int) = {
    val tileSize = 16 (16 -> 16)

    val size = ArgIn[Int]
    setArg(size, N)
    val acc = ArgOut[Int]

    Accel {
      val f1 = FIFO[Int](tileSize)
      val accum = Reg[Int](0)
      Reduce(accum)(size by tileSize){ iter =>
        Foreach(tileSize by 1){i => f1.enq(iter + i) }
        Reduce(0)(tileSize by 1){ i =>
          f1.deq()
        }{_+_}
      }{_+_}
      acc := accum
    }
    getArg(acc)
  }

  @virtualize
  def main() {
    val arraySize = args(0).to[Int]

    val gold = Array.tabulate(arraySize){ i => i }.reduce{_+_}
    val dst = fifopushpop(arraySize)

    println("gold: " + gold)
    println("dst: " + dst)

    val cksum = dst == gold
    println("PASS: " + cksum + " (FifoPushPop)")
  }
}

// object MultilevelPar extends SpatialApp { 
//   import IR._
//   val dim = 32
//   val M = dim
//   val N = dim

//   def multilevelpar() = {
//     val result = DRAM[Int](dim)

//     Accel {
//       val a = SRAM[Int](M)
//       val b = SRAM[Int](N)
//       Foreach(M by 1 par 4, N by 1 par 8) { (i,j) =>
//         a(i) = i*2
//         b(j) = j*4
//       }
//       val c = SRAM[Int](dim)
//       Foreach(dim by 1 par 4) { i =>
//         c(i) = a(i) + b(i)
//       }
//       result store c
//     }

//     getMem(result)

//   }

//   @virtualize
//   def main() {

//     val gold = Array.tabulate(dim){ i => 6*i }
//     val ans = multilevelpar()

//     printArray(gold, "Gold:")
//     printArray(ans, "Result:")

//     val cksum = gold.zip(ans){_==_}.reduce{_&&_}
//     println("PASS: " + cksum + " (MultilevelPar)")
//   }
// }


object StreamTest extends SpatialApp {
   import IR._

   override val target = targets.DE1

   @virtualize
   def main() {
     type T = Int

     val frameRows = 16
     val frameCols = 16
     val onboardVideo = target.VideoCamera
     val mem = DRAM[T](frameRows, frameCols)
     val conduit = StreamIn[T](onboardVideo)
     // val avalon = StreamOut()

    // Raw Spatial streaming pipes
    Accel {
      Foreach(*, frameCols by 1) { (_,j) =>
        val fifo1 = FIFO[T](frameCols)
        val fifo2 = FIFO[T](frameCols)
        Stream(frameCols by 1) { j =>
          Pipe {
            fifo1.enq(conduit)
          }
          Pipe {
            val pop = fifo1.deq()
            fifo2.enq(pop)
          }
          Pipe {
            val pop = fifo2.deq()
            // avalon.enq(pop)
          }
        }
      }
    }


  }

}

object BasicFSM extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  @virtualize
  def main() {
    val dram = DRAM[Int](32)
    Accel {
      val bram = SRAM[Int](32)

      FSM[Int]{state => state < 32}{state =>
        bram(state) = state
      }{state => state + 1}

      dram(0::32 par 16) store bram
    }
    val gold = Array.tabulate(32){i => i}

    val result = getMem(dram)
    printArray(result, "Result")
    printArray(gold, "Gold")
    val cksum = gold.zip(result){_ == _}.reduce{_&&_}
    // for(i <- 0 until 32) { assert(result(i) == i, "Incorrect at index " + i) }
    println("PASS: " + cksum + " (BasicFSM)")
  }
}

object BasicCondFSM extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  @virtualize
  def main() {
    val dram = DRAM[Int](32)
    Accel {
      val bram = SRAM[Int](32)

      FSM[Int]{state => state < 32} { state =>
        if (state < 16) {
          bram(31 - state) = state // 16:31 [15, 14, ... 0]
        }
        else {
          bram(state - 16) = state // 0:15 [16, 17, ... 31]
        }
      }{state => state + 1}

      dram(0::32 par 16) store bram
    }
    val result = getMem(dram)
    val gold = Array.tabulate(32){i => if (i < 16) 16 + i else 31 - i }
    printArray(result, "Result")
    printArray(gold, "Gold")
    // for (i <- 0 until 32){ assert(result(i) == gold(i)) }
    val cksum = gold.zip(result){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (BasicCondFSM)")
  }
}

object DotProductFSM extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  @virtualize
  def main() {
    val vectorA = Array.fill(128) {
      random[Int](10)
    }
    val vectorB = Array.fill(128) {
      random[Int](10)
    }
    val vecA = DRAM[Int](128)
    val vecB = DRAM[Int](128)
    val out = ArgOut[Int]
    setMem(vecA, vectorA)
    setMem(vecB, vectorB)
    Accel {
      val outer_accum = Reg[Int](0)
      FSM[Int](i => i < 128) { i =>
        val a = SRAM[Int](16)
        val b = SRAM[Int](16)
        Parallel {
          a load vecA(i :: i + 16 par 16)
          b load vecB(i :: i + 16 par 16)
        }
        outer_accum := outer_accum + Reduce(0)(0 until 16) { i => a(i) * b(i) } {
          _ + _
        }
      } { i => i + 16 }
      Pipe{out := outer_accum}
    }
    val result = getArg(out)
    val gold = vectorA.zip(vectorB){_ * _}.reduce {_ + _}
    println("Expected: " + gold + ", got: " + result)
    // assert(result == gold, "Result (" + result + ") did not equal expected (" + gold + ")")
    val cksum = result == gold
    println("PASS: " + cksum + " (DotProductFSM)")
  }
}

object FixPtInOutArg extends SpatialApp {  // Regression (Unit) // Args: -1.5
  import IR._
  type T = FixPt[TRUE,_13,_3]

  @virtualize
  def main() {
    // Declare SW-HW interface vals
    val x = ArgIn[T]
    val y = ArgOut[T]
    val N = args(0).to[T]

    // Connect SW vals to HW vals
    setArg(x, N)

    // Create HW accelerator
    Accel {
      y := (x * 9)/4 + 7
    }


    // Extract results from accelerator
    val result = getArg(y)

    // Create validation checks and debug code
    val gold = (N * 9)/4 + 7
    println("expected: " + gold)
    println("result: " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (FixPtInOutArg)")
  }
}

object FixPtMem extends SpatialApp {  // Regression (Unit) // Args: 1.25 0.75
  import IR._
  type T = Int

  @virtualize
  def main() {
    // Declare SW-HW interface vals
    val N = 128
    val a = args(0).to[T]
    val y = DRAM[T](N)
    val s = ArgIn[T]

    setArg(s, a)

    Accel {
      val yy = SRAM[T](N)
      Foreach(2*N by 1) { i =>
        if (i < s.value) { yy(i) = 1.to[T]}
      }
      Foreach(2*N by 1) { i =>
        if (i >= s.value) { if (i < N) {yy(i) = 2.to[T] }}
      }
      y(0 :: N par 1) store yy
    }


    // Extract results from accelerator
    val result = getMem(y)

    // Create validation checks and debug code
    val gold = Array.tabulate(N){i => if (i < a) {1} else {2}}
    printArray(gold, "expected: ")
    printArray(result, "got: ")

    val cksum = gold.zip(result){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (MaskedWrite)")
  }
}


object SpecialMath extends SpatialApp { // Regression (Unit) // Args: 0.125 5.625 14 1.875 -3.4375 -5
  import IR._
  type USGN = FixPt[FALSE,_4,_4]
  type SGN = FixPt[TRUE,_4,_4]

  @virtualize
  def main() {
    // Declare SW-HW interface vals
    val a_usgn = args(0).to[USGN] //2.625.to[USGN]
    val b_usgn = args(1).to[USGN] //5.625.to[USGN]
    val c_usgn = args(2).to[USGN] //4094.to[USGN]
    val a_sgn = args(3).to[SGN]
    val b_sgn = args(4).to[SGN]
    val c_sgn = args(5).to[SGN]
    // assert(b_usgn.to[FltPt[_24,_8]] + c_usgn.to[FltPt[_24,_8]] > 15.to[FltPt[_24,_8]], "b_usgn + c_usgn must saturate (false,4,4) FP number")
    // assert(b_sgn.to[FltPt[_24,_8]] + c_sgn.to[FltPt[_24,_8]] < -8.to[FltPt[_24,_8]], "b_sgn + c_sgn must saturate (true,4,4) FP number")
    val A_usgn = ArgIn[USGN]
    val B_usgn = ArgIn[USGN]
    val C_usgn = ArgIn[USGN]
    val A_sgn = ArgIn[SGN]
    val B_sgn = ArgIn[SGN]
    val C_sgn = ArgIn[SGN]
    setArg(A_usgn, a_usgn)
    setArg(B_usgn, b_usgn)
    setArg(C_usgn, c_usgn)
    setArg(A_sgn, a_sgn)
    setArg(B_sgn, b_sgn)
    setArg(C_sgn, c_sgn)
    val N = 256

    // Conditions we will check
    val unbiased_mul_unsigned = DRAM[USGN](N) // 1
    val unbiased_mul_signed = DRAM[SGN](N) // 2
    val satur_add_unsigned = ArgOut[USGN] // 3
    val satur_add_signed = ArgOut[SGN] // 4
    val unbiased_sat_mul_unsigned = ArgOut[USGN] // 5
    val unbiased_lower_sat_mul_signed = ArgOut[SGN] // 6
    val unbiased_upper_sat_mul_signed = ArgOut[SGN] // 6


    Accel {
      val usgn = SRAM[USGN](N)
      val sgn = SRAM[SGN](N)
      Foreach(N by 1) { i => 
        usgn(i) = A_usgn *& B_usgn // Unbiased rounding, mean(yy) should be close to a*b
        sgn(i) = A_sgn *& B_sgn
      }
      unbiased_mul_unsigned store usgn
      unbiased_mul_signed store sgn
      Pipe{ satur_add_unsigned := C_usgn <+> B_usgn}
      Pipe{ satur_add_signed := C_sgn <+> B_sgn}
      Pipe{ unbiased_sat_mul_unsigned := B_usgn <*&> C_usgn}
      Pipe{ unbiased_lower_sat_mul_signed := C_sgn <*&> A_sgn}
      Pipe{ unbiased_upper_sat_mul_signed := C_sgn <*&> (-1.to[SGN]*A_sgn)}
    }


    // Extract results from accelerator
    val unbiased_mul_unsigned_res = getMem(unbiased_mul_unsigned)
    val satur_add_unsigned_res = getArg(satur_add_unsigned)
    val unbiased_mul_signed_res = getMem(unbiased_mul_signed)
    val satur_add_signed_res = getArg(satur_add_signed)
    val unbiased_sat_mul_unsigned_res = getArg(unbiased_sat_mul_unsigned)
    val unbiased_lower_sat_mul_signed_res = getArg(unbiased_lower_sat_mul_signed)
    val unbiased_upper_sat_mul_signed_res = getArg(unbiased_upper_sat_mul_signed)

    // Create validation checks and debug code
    val gold_unbiased_mul_unsigned = (a_usgn * b_usgn).to[FltPt[_24,_8]]
    val gold_mean_unsigned = unbiased_mul_unsigned_res.map{_.to[FltPt[_24,_8]]}.reduce{_+_} / N
    val gold_unbiased_mul_signed = (a_sgn * b_sgn).to[FltPt[_24,_8]]
    val gold_mean_signed = unbiased_mul_signed_res.map{_.to[FltPt[_24,_8]]}.reduce{_+_} / N
    val gold_satur_add_signed = (-8).to[Float]
    val gold_satur_add_unsigned = (15.9375).to[Float]
    val gold_unbiased_sat_mul_unsigned = (15.9375).to[Float]
    val gold_unbiased_lower_sat_mul_signed = (-8).to[Float]
    val gold_unbiased_upper_sat_mul_signed = (7.9375).to[Float]

    // Get cksums
    val margin = scala.math.pow(2,-4).to[FltPt[_24,_8]]
    val cksum1 = (abs(gold_unbiased_mul_unsigned - gold_mean_unsigned).to[FltPt[_24,_8]] < margin) 
    val cksum2 = (abs(gold_unbiased_mul_signed - gold_mean_signed).to[FltPt[_24,_8]] < margin) 
    val cksum3 = satur_add_unsigned_res == gold_satur_add_unsigned.to[USGN]
    val cksum4 = satur_add_signed_res == gold_satur_add_signed.to[SGN]
    val cksum5 = unbiased_sat_mul_unsigned_res == gold_unbiased_sat_mul_unsigned.to[USGN]
    val cksum6 = unbiased_lower_sat_mul_signed_res == gold_unbiased_lower_sat_mul_signed.to[SGN]
    val cksum7 = unbiased_upper_sat_mul_signed_res == gold_unbiased_upper_sat_mul_signed.to[SGN]
    val cksum = cksum1 && cksum2 && cksum3 && cksum4 && cksum5 && cksum6 && cksum7

    // Helpful prints
    println(cksum1 + " Unbiased Rounding Multiplication Unsigned: |" + gold_unbiased_mul_unsigned + " - " + gold_mean_unsigned + "| = " + abs(gold_unbiased_mul_unsigned-gold_mean_unsigned) + " <? " + margin)
    println(cksum2 + " Unbiased Rounding Multiplication Signed: |" + gold_unbiased_mul_signed + " - " + gold_mean_signed + "| = " + abs(gold_unbiased_mul_signed-gold_mean_signed) + " <? " + margin)
    println(cksum3 + " Saturating Addition Unsigned: " + satur_add_unsigned_res + " =?= " + gold_satur_add_unsigned.to[USGN])
    println(cksum4 + " Saturating Addition Signed: " + satur_add_signed_res + " =?= " + gold_satur_add_signed.to[SGN])
    println(cksum5 + " Unbiased Saturating Multiplication Unsigned: " + unbiased_sat_mul_unsigned_res + " =?= " + gold_unbiased_sat_mul_unsigned.to[SGN])
    println(cksum6 + " Unbiased (lower) Saturating Multiplication Signed: " + unbiased_lower_sat_mul_signed_res + " =?= " + gold_unbiased_lower_sat_mul_signed.to[SGN])
    println(cksum6 + " Unbiased (upper) Saturating Multiplication Signed: " + unbiased_upper_sat_mul_signed_res + " =?= " + gold_unbiased_upper_sat_mul_signed.to[SGN])


    println("PASS: " + cksum + " (SpecialMath) * Need to check subtraction and division ")
  }
}


object DiagBanking extends SpatialApp {  // Regression (Unit) // Args: none
  import IR._
  type T = Int

  @virtualize
  def main() {
    // Declare SW-HW interface vals
    val colpar = 8
    val rowpar = 3
    val M = 64
    val N = 32
    val x_data = (0::M, 0::N){(i,j) => (i*N + j).to[T]}
    val x = DRAM[T](M,N)
    val s = ArgOut[T]

    setMem(x, x_data)

    Accel {
      val xx = SRAM[T](M,N)
      xx load x(0 :: M, 0 :: N par colpar)
      s := Reduce(Reg[T](0.to[T]))(N by 1, M by 1 par rowpar) { (j,i) => 
        xx(i,j)
      }{_+_}
    }


    // Extract results from accelerator
    val result = getArg(s)

    // Create validation checks and debug code
    val gold = x_data.reduce{_+_}
    println("Result: (gold) " + gold + " =?= " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (DiagBanking)")
  }
}

object MultiArgOut extends SpatialApp { 
  import IR._
  type T = Int

  @virtualize
  def main() {
    // Declare SW-HW interface vals
    val a = ArgIn[T]
    val b = ArgIn[T]
    val x = ArgOut[T]
    val y = ArgOut[T]
    val i = args(0).to[T]
    val j = args(1).to[T]
    setArg(a, i)
    setArg(b, j)


    Accel {
      x := a
      y := b
    }


    // Extract results from accelerator
    val xx = getArg(x)
    val yy = getArg(y)

    println("xx = " + xx + ", yy = " + yy)
    val cksum = (xx == i) && (yy == j)
    println("PASS: " + cksum + " (MultiArgOut)")
  }
}

object MultiWriteBuffer extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  @virtualize
  def main() {
    val R = 16
    val C = 16

    val mem = DRAM[Int](R, C)
    val y = ArgOut[Int]

    Accel {
      val accum = SRAM[Int](R, C)
      MemReduce(accum)(1 until (R+1)) { row =>
        val sram_seq = SRAM[Int](R, C)
         Foreach(0 until R, 0 until C) { (r, c) =>
            sram_seq(r,c) = 0
         }
         Foreach(0 until C) { col =>
            sram_seq(row-1, col) = 32*(row-1 + col)
         }
         sram_seq
      }  { (sr1, sr2) => sr1 + sr2 }

      mem store accum
    }
    
    val result = getMatrix(mem)
    val gold = (0::R, 0::C){(i,j) => 32*(i+j)}
    printMatrix(gold, "Gold:")
    printMatrix(result, "Result:")

    val cksum = gold.zip(result){_==_}.reduce{_&&_}
    println("PASS: " + cksum + " (MultiWriteBuffer)")
  }
}

