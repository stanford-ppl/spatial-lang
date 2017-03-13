import spatial._
import org.virtualized._

object InOutArg extends SpatialApp {  // Regression (Unit) // Args: 5
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

object FileSplitter extends SpatialApp {  // Regression (Unit) // Args: 5
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
          Pipe {y := x + 4 + j + i}
        }
      }
    }


    // Extract results from accelerator
    val result = getArg(y)

    // Create validation checks and debug code
    val gold = N + 4 + 4 + 9
    println("expected: " + gold)
    println("result: " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (FileSplitter)")
  }
}

object Niter extends SpatialApp {  // Regression (Unit) // Args: 100
  import IR._
  
  val constTileSize = 16

  def nIterTest[T:Staged:Num](len: Int): T = {
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
          val accum = Reduce(Reg[T](0.as[T]))(redMax par innerPar){ ii =>
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

  def fifoLoad[T:Staged:Num](srcHost: Array[T], N: Int) = {
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

  def simpleLoadStore[T:Staged:Num](srcHost: Array[T], value: T) = {
    val loadPar  = 1 (1 -> 1)
    val storePar = 1 (1 -> 1)
    val tileSize = 16 (16 -> 16)

    val srcFPGA = DRAM[T](N)
    val dstFPGA = DRAM[T](N)
    setMem(srcFPGA, srcHost)

    val x = ArgIn[T]
    setArg(x, value)
    Accel {
      val b1 = SRAM[T](tileSize)
      Sequential.Foreach(N by tileSize) { i =>


        b1 load srcFPGA(i::i+tileSize par 16)

        val b2 = SRAM[T](tileSize)
        Foreach(tileSize by 1 par 16) { ii =>
          b2(ii) = b1(ii) * x
        }

        dstFPGA(i::i+tileSize par 16) store b2
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

  def singleFifoLoad[T:Staged:Num](src1: Array[T], in: Int) = {

    val P1 = 16 (16 -> 16)

    val N = ArgIn[Int]
    setArg(N, in)

    val src1FPGA = DRAM[T](N)
    val out = ArgOut[T]
    setMem(src1FPGA, src1)

    Accel {
      val f1 = FIFO[T](tileSize)
      Foreach(N by tileSize) { i =>
        f1 load src1FPGA(i::i+tileSize par P1)
        val accum = Reduce(Reg[T](0.as[T]))(tileSize by 1){i =>
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
  def parFifoLoad[T:Staged:Num](src1: Array[T], src2: Array[T], in: Int) = {

    val P1 = 16 (16 -> 16)

    val N = ArgIn[Int]
    setArg(N, in)

    val src1FPGA = DRAM[T](N)
    val src2FPGA = DRAM[T](N)
    val out = ArgOut[T]
    setMem(src1FPGA, src1)
    setMem(src2FPGA, src2)

    Accel {
      val f1 = FIFO[T](tileSize)
      val f2 = FIFO[T](tileSize)
      Foreach(N by tileSize) { i =>
        Parallel {
          f1 load src1FPGA(i::i+tileSize par P1)
          f2 load src2FPGA(i::i+tileSize par P1)
        }
        val accum = Reduce(Reg[T](0.as[T]))(tileSize by 1){i =>
          f1.deq() * f2.deq()
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

    val src1 = Array.tabulate(arraySize) { i => (2*i) % 256}
    val src2 = Array.tabulate(arraySize) { i => i % 256 }
    val out = parFifoLoad(src1, src2, arraySize)

    val sub1_for_check = Array.tabulate(arraySize-tileSize) {i => 2*i % 256}
    val sub2_for_check = Array.tabulate(arraySize-tileSize) {i => i % 256}

    // val gold = src1.zip(src2){_*_}.zipWithIndex.filter( (a:Int, i:Int) => i > arraySize-64).reduce{_+_}
    val gold = src1.zip(src2){_*_}.reduce{_+_} - sub1_for_check.zip(sub2_for_check){_*_}.reduce(_+_)
    println("gold: " + gold)
    println("out: " + out)

    val cksum = out == gold
    println("PASS: " + cksum + " (ParFifoLoad)")
  }
}



object FifoLoadStore extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  val N = 32

  def fifoLoadStore[T:Staged:Bits](srcHost: Array[T]) = {
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

  val N = 16.as[Int]

  def simpleReduce[T:Staged:Num](xin: T) = {
    val P = param(8)

    val x = ArgIn[T]
    val out = ArgOut[T]
    setArg(x, xin)

    Accel {
      out := Reduce(Reg[T](0.as[T]))(N by 1){ ii =>
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

  def simple_fold[T:Staged:Num](src: Array[T]) = {
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
      val accum = Reg[T](0.as[T])
      Reduce(accum)(N by tileSize par outerPar){ i =>
        val b1 = SRAM[T](tileSize)
        b1 load v1(i::i+tileSize par 16)
        Reduce(Reg[T](0.as[T]))(tileSize par innerPar){ ii =>
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

  def memcpy_2d[T:Staged:Num](src: Array[T], rows: Int, cols: Int): Array[T] = {
    val tileDim1 = param(16)
    val tileDim2 = 16 (16 -> 16)

    val rowsIn = rows
    val colsIn = cols

    val srcFPGA = DRAM[T](rows, cols)
    val dstFPGA = DRAM[T](rows, cols)

    // Transfer data and start accelerator
    setMem(srcFPGA, src)

    Accel {
      Sequential.Foreach(rowsIn by tileDim1, colsIn by tileDim2) { (i,j) =>
        val tile = SRAM[T](tileDim1, tileDim2)
        tile load srcFPGA(i::i+tileDim1 par 16, j::j+tileDim2)
        dstFPGA (i::i+tileDim1 par 16, j::j+tileDim2) store tile
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


object BlockReduce1D extends SpatialApp { // Regression (Unit) // Args: 1920
  import IR._

  val tileSize = 16
  val p = 2

  def blockreduce_1d[T:Staged:Num](src: Array[T], size: Int) = {
    val sizeIn = ArgIn[Int]
    setArg(sizeIn, size)

    val srcFPGA = DRAM[T](sizeIn)
    val dstFPGA = DRAM[T](tileSize)

    setMem(srcFPGA, src)

    Accel {
      val accum = SRAM[T](tileSize)
      MemReduce(accum)(sizeIn by tileSize){ i  =>
        val tile = SRAM[T](tileSize)
        tile load srcFPGA(i::i+tileSize par 16)
        tile
      }{_+_}
      dstFPGA store accum
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() = {
    val size = args(0).to[Int]
    val src = Array.tabulate(size){i => i % 256}

    val dst = blockreduce_1d(src, size)

    val tsArr = Array.tabulate(tileSize){i => i}
    val perArr = Array.tabulate(size/tileSize){i => i}
    val gold = tsArr.map{ i => perArr.map{j => src(i+j*tileSize)}.reduce{_+_}}

    printArray(gold, "src:")
    printArray(dst, "dst:")
    val cksum = dst.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (BlockReduce1D)")

    //    (0 until tileSize) foreach { i => assert(dst(i) == gold(i)) }
  }
}

object UnalignedLd extends SpatialApp { // Regression (Unit) // Args: 100 16
  import IR._

  val N = 19200

  val paddedCols = 1920

  def unaligned_1d[T:Staged:Num](src: Array[T], ii: Int, numCols: Int) = {
    val iters = ArgIn[Int]
    val srcFPGA = DRAM[T](paddedCols)
    val ldSize = ArgIn[Int]
    val acc = ArgOut[T]

    setArg(iters, ii)
    setArg(ldSize, numCols)
    setMem(srcFPGA, src)

    Accel {
      val mem = SRAM[T](16)
      val accum = Reg[T](0.as[T])
      Reduce(accum)(iters by 1) { k =>
        mem load srcFPGA(k*ldSize::k*ldSize+ldSize par 16)
        Reduce(Reg[T](0.as[T]))(ldSize by 1){i => mem(i) }{_+_}
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

    val gold = Array.tabulate(ii*cols){ i => i % 256 }.reduce{_+_}

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

  def blockreduce_2d[T:Staged:Num](src: Array[T], rows: Int, cols: Int) = {
    val rowsIn = ArgIn[Int]; setArg(rowsIn, rows)
    val colsIn = ArgIn[Int]; setArg(colsIn, cols)

    val srcFPGA = DRAM[T](rowsIn, colsIn)
    val dstFPGA = DRAM[T](tileSize, tileSize)

    setMem(srcFPGA, src)

    Accel {
      val accum = SRAM[T](tileSize,tileSize)
      MemReduce(accum)(rowsIn by tileSize, colsIn by tileSize){ (i,j)  =>
        val tile = SRAM[T](tileSize,tileSize)
        tile load srcFPGA(i::i+tileSize par 16, j::j+tileSize)
        tile
      }{_+_}
      dstFPGA store accum
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
object ScatterGather extends SpatialApp { // Regression (Sparse) // Args: none
  import IR._

  val N = 1920

  val tileSize = 384
  val maxNumAddrs = 1536
  val offchip_dataSize = maxNumAddrs*6
  val P = param(1)

  def scattergather[T:Staged:Num](addrs: Array[Int], offchip_data: Array[T], size: Int, dataSize: Int) = {

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



// Args: None
object MultiplexedWriteTest extends SpatialApp { // Regression (Unit) // Args: none
  import IR._

  val tileSize = 16
  val I = 5
  val N = 192

  def multiplexedwrtest[W:Staged:Num](w: Array[W], i: Array[W]): Array[W] = {
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
          weightsResult(i*I+x*T::i*I+x*T+T) store wt //s1 read
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
    val dummyWeightsResult = DRAM[Int](T)
    val dummyOut = DRAM[Int](T)
    val dummyOut2 = DRAM[Int](T)
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
          weightsResult(i*I+x*T::i*I+x*T+T) store wt //s4 read
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
    println("PASS: " + cksum  + " (MultiplexedWriteTest)")


  }
}

object SequentialWrites extends SpatialApp { // Regression (Unit) // Args: 7
  import IR._

  val tileSize = 16
  val N = 5

  def sequentialwrites[A:Staged:Num](srcData: Array[A], x: A) = {
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

      MemReduce(in)(N by 1){ i =>
        val d = SRAM[A](T)
        Foreach(T by 1){ i => d(i) = xx.value + i.to[A] }
        d
      }{_+_}

      dst(0::T) store in
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

  def changingctrmax[T:Staged:Num](): Array[T] = {
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


// Args: 384
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
            val pop = conduit.deq()
            fifo1.enq(pop)
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
