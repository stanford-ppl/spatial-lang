package spatial.tests

import org.scalatest.{FlatSpec, Matchers,Tag}
import org.virtualized._

object SimpleSequential extends SpatialTest {
  import spatial.dsl._
  testArgs = List("16", "16")

  def simpleSeq(xIn: Int, yIn: Int): Int = {
    val innerPar = 1 (1 -> 1)
    val tileSize = 96 (96 -> 96)

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

    val a1 = Array.tabulate(96){i => x * i}
    val gold = a1(y)

    println("expected: " + gold)
    println("result:   " + result)
    val chkSum = result == gold
    println("PASS: " + chkSum + " (SimpleSeq)")
    assert(chkSum)
  }
}


// Args: 16
object DeviceMemcpy extends SpatialTest {
  import spatial.dsl._
  testArgs = List("16")

  val N = 192
  type T = Int
  def memcpyViaFPGA(srcHost: Array[T]): Array[T] = {
    val fpgaMem = DRAM[T](N)
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
    assert(chkSum)
  }
}

// Args: 16
object SimpleTileLoadStore extends SpatialTest {
  import spatial.dsl._
  testArgs = List("16")

  val N = 192

  def simpleLoadStore[T:Type:Num](srcHost: Array[T], value: T) = {
    val loadPar  = 1 (1 -> 1)
    val storePar = 1 (1 -> 1)
    val tileSize = 96 (96 -> 96)

    val srcFPGA = DRAM[T](N)
    val dstFPGA = DRAM[T](N)
    setMem(srcFPGA, srcHost)

    val size = ArgIn[Int]
    val x = ArgIn[T]
    setArg(x, value)
    setArg(size, N)
    Accel {
      val b1 = SRAM[T](tileSize)
      Sequential.Foreach(size by tileSize) { i =>

        b1 load srcFPGA(i::i+tileSize)

        val b2 = SRAM[T](tileSize)
        Foreach(tileSize by 1) { ii =>
          b2(ii) = b1(ii) * x
        }

        dstFPGA(i::i+tileSize) store b2
      }
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() {
    val arraySize = N
    val value = args(0).to[Int]

    val src = Array.tabulate[Int](arraySize) { i => i }
    val dst = simpleLoadStore(src, value)

    val gold = src.map { _ * value }

    println("Sent in: ")
    (0 until arraySize) foreach { i => print(gold(i) + " ") }
    println("Got out: ")
    (0 until arraySize) foreach { i => print(dst(i) + " ") }
    println("")

    val cksum = dst.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (SimpleTileLoadStore)")
    assert(cksum)
  }
}


// Args: 960
object FifoLoad extends SpatialTest {
  import spatial.dsl._
  testArgs = List("960")

  def fifoLoad[T:Type:Num](srcHost: Array[T], N: Int) = {
    val tileSize = 96 (96 -> 96)

    val size = ArgIn[Int]
    setArg(size, N)

    val srcFPGA = DRAM[T](size)
    val dstFPGA = DRAM[T](size)
    setMem(srcFPGA, srcHost)

    Accel {
      val f1 = FIFO[T](tileSize)
      Sequential.Foreach(size by tileSize) { i =>
        f1 load srcFPGA(i::i + tileSize)
        val b1 = SRAM[T](tileSize)
        Foreach(tileSize by 1) { i =>
          b1(i) = f1.deq()
        }
        dstFPGA(i::i + tileSize) store b1
      }
      ()
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() {
    val arraySize = args(0).to[Int]

    val src = Array.tabulate(arraySize){i => i }
    val dst = fifoLoad(src, arraySize)

    val gold = src

    println("Sent in: ")
    (0 until arraySize) foreach { i => print(gold(i) + " ") }
    println("Got out:")
    (0 until arraySize) foreach { i => print(dst(i) + " ") }
    println("")

    val cksum = dst.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (FifoLoadTest)")
    assert(cksum)
  }
}


// 6
object ParFifoLoad extends SpatialTest {
  import spatial.dsl._
  testArgs = List("192")

  def parFifoLoad[T:Type:Num](src1: Array[T], src2: Array[T], in: Int) = {

    val tileSize = 96 (96 -> 96)

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
          f1 load src1FPGA(i::i+tileSize)
          f2 load src2FPGA(i::i+tileSize)
        }
        val accum = Reduce(Reg[T](0.to[T]))(tileSize by 1){i =>
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

    val src1 = Array.tabulate(arraySize) { i => i }
    val src2 = Array.tabulate(arraySize) { i => i*2 }
    val out = parFifoLoad(src1, src2, arraySize)

    val sub1_for_check = Array.tabulate(arraySize-96) {i => i}
    val sub2_for_check = Array.tabulate(arraySize-96) {i => i*2}
    val sub = if (arraySize <= 96) lift(0) else sub1_for_check.zip(sub2_for_check){_*_}.reduce(_+_)

    // val gold = src1.zip(src2){_*_}.zipWithIndex.filter( (a:Int, i:Int) => i > arraySize-96).reduce{_+_}
    val gold = src1.zip(src2){_*_}.reduce{_+_} - sub
    println("gold = " + gold)
    println("out = " + out)

    val cksum = out == gold
    println("PASS: " + cksum + " (ParFifoLoad)")
    assert(cksum)
  }
}


object FifoLoadStore extends SpatialTest {
  import spatial.dsl._

  val N = 192

  def fifoLoadStore[T:Type:Bits](srcHost: Array[T]) = {
    val tileSize = N

    val srcFPGA = DRAM[T](N)
    val dstFPGA = DRAM[T](N)
    setMem(srcFPGA, srcHost)

    Accel {
      val f1 = FIFO[T](tileSize)
      // Parallel {
      Sequential {
        f1 load srcFPGA(0::tileSize)
        dstFPGA(0::tileSize) store f1
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

    val src = Array.tabulate(arraySize) { i => i }
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
    assert(cksum)
  }
}


object SimpleReduce extends SpatialTest { // Args: 72
  import spatial.dsl._
  testArgs = List("72")

  val N = 96.to[Int]

  def simpleReduce[T:Type:Num](xin: T) = {
    val P = param(8)

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
    assert(cksum)
  }
}


// Args: 192
object Niter extends SpatialTest {
  import spatial.dsl._
  testArgs = List("192")

  val constTileSize = 96

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
          val accum = Reduce(Reg[T](0.to[T]))(tileSize par innerPar){ ii =>
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

    val b1 = Array.tabulate(len){i => i}

    val gold = b1.reduce{_+_} - ((len-constTileSize) * (len-constTileSize-1))/2
    println("expected: " + gold)
    println("result:   " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (Niter)")
    assert(cksum)
  }
}

// Args: 1920
object SimpleFold extends SpatialTest {
  import spatial.dsl._
  testArgs = List("1920")

  val constTileSize = 96

  def simple_fold[T:Type:Num](src: Array[T]) = {
    val outerPar = 16 (16 -> 16)
    val innerPar = 16 (16 -> 16)
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

    val src = Array.tabulate(len){i => i % 25}
    val result = simple_fold(src)

    val gold = src.reduce{_+_}
    println("expected: " + gold)
    println("result:   " + result)

    val cksum = result == gold
    println("PASS: " + cksum + " (SimpleFold)")
    assert(cksum)
  }
}

// Args: None
object Memcpy2D extends SpatialTest {
  import spatial.dsl._

  val R = 96
  val C = 96

  def memcpy_2d[T:Type:Num](src: Array[T], rows: Int, cols: Int): Array[T] = {
    val tileDim1 = param(96)
    val tileDim2 = 96 (96 -> 96)

    val rowsIn = rows
    val colsIn = cols

    val srcFPGA = DRAM[T](rows, cols)
    val dstFPGA = DRAM[T](rows, cols)

    // Transfer data and start accelerator
    setMem(srcFPGA, src)

    Accel {
      Sequential.Foreach(rowsIn by tileDim1, colsIn by tileDim2) { (i,j) =>
        val tile = SRAM[T](tileDim1, tileDim2)
        tile load srcFPGA(i::i+tileDim1, j::j+tileDim2)
        dstFPGA (i::i+tileDim1, j::j+tileDim2) store tile
      }
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() = {
    val rows = R
    val cols = C
    val src = Array.tabulate(rows*cols) { i => i }

    val dst = memcpy_2d(src, rows, cols)

    printArray(src, "src:")
    printArray(dst, "dst:")

    val cksum = dst.zip(src){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (MemCpy2D)")
    assert(cksum)
  }
}


// Args: 1920
object BlockReduce1D extends SpatialTest {
  import spatial.dsl._
  testArgs = List("1920")

  val tileSize = 96
  val p = 2

  def blockreduce_1d[T:Type:Num](src: Array[T], size: Int) = {
    val sizeIn = ArgIn[Int]
    setArg(sizeIn, size)

    val srcFPGA = DRAM[T](sizeIn)
    val dstFPGA = DRAM[T](tileSize)

    setMem(srcFPGA, src)

    Accel {
      val accum = SRAM[T](tileSize)
      MemReduce(accum)(sizeIn by tileSize){ i  =>
        val tile = SRAM[T](tileSize)
        tile load srcFPGA(i::i+tileSize)
        tile
      }{_+_}
      dstFPGA (0::tileSize) store accum
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() = {
    val size = args(0).to[Int]
    val src = Array.tabulate(size){i => i }

    val dst = blockreduce_1d(src, size)

    val iters = size/tileSize
    val first = ((iters*(iters-1))/2)*tileSize

    val gold = Array.tabulate(tileSize) { i => first + i*iters }

    printArray(gold, "src:")
    printArray(dst, "dst:")
    val cksum = dst.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (BlockReduce1D)")
    assert(cksum)
    //    (0 until tileSize) foreach { i => assert(dst(i) == gold(i)) }
  }
}

// Args: 100
object UnalignedLd extends SpatialTest {
  import spatial.dsl._
  testArgs = List("100")

  val N = 19200

  val numCols = 8
  val paddedCols = 1920

  def unaligned_1d[T:Type:Num](src: Array[T], ii: Int) = {
    val iters = ArgIn[Int]
    val srcFPGA = DRAM[T](paddedCols)
    val acc = ArgOut[T]

    setArg(iters, ii)
    setMem(srcFPGA, src)

    Accel {
      val mem = SRAM[T](96)
      val accum = Reg[T](0.to[T])
      Reduce(accum)(iters by 1) { k =>
        mem load srcFPGA(k*numCols::k*numCols+numCols)
        Reduce(Reg[T](0.to[T]))(numCols by 1){i => mem(i) }{_+_}
      }{_+_}
      acc := accum
    }
    getArg(acc)
  }

  @virtualize
  def main() = {
    // val size = args(0).to[Int]
    val ii = args(0).to[Int]
    val size = paddedCols
    val src = Array.tabulate(size) {i => i }

    val dst = unaligned_1d(src, ii)

    val gold = Array.tabulate(ii*numCols){ i => i }.reduce{_+_}

    println("src:" + gold)
    println("dst:" + dst)
    val cksum = gold == dst
    println("PASS: " + cksum + " (UnalignedLd)")
    assert(cksum)
    //    (0 until tileSize) foreach { i => assert(dst(i) == gold(i)) }
  }
}

object PipeRetimer extends Tag("PipeRetimer") with SpatialTest {
  import spatial.dsl._
  @virtualize
  def main() {
    // add one to avoid dividing by zero
    val a = random[Int](10) + 1
    val b = random[Int](10) + 1

    val aIn = ArgIn[Int]
    val bIn = ArgIn[Int]
    setArg(aIn, a)
    setArg(bIn, b)

    val out1 = ArgOut[Int]
    val out2 = ArgOut[Int]
    Accel {
      out1 := (aIn * bIn) + aIn
      out2 := (aIn / bIn) + aIn
    }
    val gold1 = (a * b) + a
    val gold2 = (a / b) + a
    val cksum = gold1 == getArg(out1) && gold2 == getArg(out2)
    println("PASS: " + cksum + " (PipeRetimer)")
  }
}

class UnitTests extends FlatSpec with Matchers {
  "SimpleSequential" should "compile" in { SimpleSequential.main(Array.empty) }
  "DeviceMemcpy" should "compile" in { DeviceMemcpy.main(Array.empty) }
  "SimpleTileLoadStore" should "compile" in { SimpleTileLoadStore.main(Array.empty) }
  "FifoLoad" should "compile" in { FifoLoad.main(Array.empty) }
  "ParFifoLoad" should "compile" in { ParFifoLoad.main(Array.empty) }
  "FifoLoadStore" should "compile" in { FifoLoadStore.main(Array.empty) }
  "SimpleReduce" should "compile" in { SimpleReduce.main(Array.empty) }
  "Niter" should "compile" in { Niter.main(Array.empty) }
  "SimpleFold" should "compile" in { SimpleFold.main(Array.empty) }
  "Memcpy2D" should "compile" in { Memcpy2D.main(Array.empty) }
  "BlockReduce1D" should "compile" in { BlockReduce1D.main(Array.empty) }
  "UnalignedLd" should "compile" in { UnalignedLd.main(Array.empty) }
  "PipeRetimer" should "compile" taggedAs(PipeRetimer) in { PipeRetimer.main(Array.empty) }
}
