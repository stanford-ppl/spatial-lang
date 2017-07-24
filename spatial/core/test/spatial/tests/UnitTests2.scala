package spatial.tests

import org.scalatest.{FlatSpec, Matchers}
import org.virtualized._

// Args: 192 384
object BlockReduce2D extends SpatialTest {
  import spatial.dsl._
  testArgs = List("192", "384")

  val N = 1920
  val tileSize = 96

  def blockreduce_2d[T:Type:Num](src: Array[T], rows: Int, cols: Int) = {
    val rowsIn = ArgIn[Int]; setArg(rowsIn, rows)
    val colsIn = ArgIn[Int]; setArg(colsIn, cols)

    val srcFPGA = DRAM[T](rowsIn, colsIn)
    val dstFPGA = DRAM[T](tileSize, tileSize)

    setMem(srcFPGA, src)

    Accel {
      val accum = SRAM[T](tileSize,tileSize)
      MemReduce(accum)(rowsIn by tileSize, colsIn by tileSize){ (i,j)  =>
        val tile = SRAM[T](tileSize,tileSize)
        tile load srcFPGA(i::i+tileSize, j::j+tileSize)
        tile
      }{_+_}
      dstFPGA (0::tileSize, 0::tileSize) store accum
    }
    getMem(dstFPGA)
  }

  @virtualize
  def main() = {
    val numRows = args(0).to[Int]
    val numCols = args(1).to[Int]
    val src = Array.tabulate(numRows) { i => Array.tabulate(numCols) { j => 1.to[Int] } } //i*numCols + j} }
    val flatsrc = src.flatten

    val result = blockreduce_2d(flatsrc, numRows, numCols)

    val numHorizontal = numRows/tileSize
    val numVertical = numCols/tileSize
    val numBlocks = numHorizontal*numVertical

    val a1 = Array.tabulate(tileSize) { i => i }
    val a2 = Array.tabulate(tileSize) { i => i }
    val a3 = Array.tabulate(numHorizontal) { i => i }
    val a4 = Array.tabulate(numVertical) { i => i }
    val gold = a1.map{i=> a2.map{j => a3.map{ k=> a4.map {l=>
      src(i + k*tileSize).apply(j + l*tileSize) }}.flatten.reduce{_+_}
    }}.flatten

    printArray(gold, "gold:")
    printArray(result, "result:")
    // result.zip(gold){_==_} foreach {println(_)}
    val cksum = result.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (BlockReduce2D)")
    assert(cksum)
    //    (0 until tileSize) foreach { i => assert(dst(i) == gold(i)) }
  }
}


// Args: none
object ScatterGather extends SpatialTest {
  import spatial.dsl._

  val N = 1920

  val tileSize = 384
  val maxNumAddrs = 1536
  val offchip_dataSize = maxNumAddrs*6
  val P = param(1)

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
    assert(cksum)
  }
}


// Args: 7
object InOutArg extends SpatialTest {
  import spatial.dsl._
  testArgs = List("7")

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
    assert(cksum)
  }
}

// Args: None
object MultiplexedWriteTest extends SpatialTest { // Regression (Unit) // Args: none
  import spatial.dsl._

  val tileSize = 64
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
        wt load weights(i::i+T)
        in load inputs(i::i+T)

        // Some math nonsense (definitely not a correct implementation of anything)
        Foreach(I by 1){x =>
          MemFold(wt)(1 by 1){ i =>  // s0 write
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
        val in = Array.tabulate(tileSize) { i => (j+1)*(k*tileSize + i) }
        val wt = Array.tabulate(tileSize) { i => k*tileSize + i }
        in.zip(wt){_+_}
      }.flatten
    }.flatten
    printArray(gold, "gold: ");
    printArray(result, "result: ");

    val cksum = gold.zip(result){_==_}.reduce{_&&_}
    println("PASS: " + cksum  + " (MultiplexedWriteTest)")
    assert(cksum)
  }
}

// TODO: Make this actually check a bubbled NBuf (i.e.- s0 = wr, s2 = wr, s4 =rd, s1s2 = n/a)
// because I think this will break the NBuf SM since it won't detect drain completion properly
// Args: None
object BubbledWriteTest extends SpatialTest { // Regression (Unit) // Args: none
  import spatial.dsl._

  val tileSize = 64
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
        wt load weights(i::i+T)
        in load inputs(i::i+T)

        Foreach(I by 1){x =>
          val niter = Reg[Int]
          niter := x+1
          MemFold(wt)(niter by 1){ k =>  // s0 write
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

    val gold = Array.tabulate(N/tileSize) { k =>
      Array.tabulate(I){ j =>
        Array.tabulate(tileSize) { i =>
          ( 1 + (j+1)*(j+2)/2 ) * (i + k*tileSize)
        }
      }.flatten
    }.flatten
    printArray(gold, "gold: ")
    printArray(result, "result: ")

    val cksum = gold.zip(result){_==_}.reduce{_&&_}
    println("PASS: " + cksum  + " (MultiplexedWriteTest)")
    assert(cksum)
  }
}

object SequentialWrites extends SpatialTest {
  import spatial.dsl._
  testArgs = List("13")

  val tileSize = 96
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
      in load src(0::T)

      MemFold(in)(N by 1){ i =>
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
    val srcData = Array.tabulate(tileSize){ i => i }

    val result = sequentialwrites(srcData, x)

    val first = x*N
    val diff = N+1
    val gold = Array.tabulate(tileSize) { i => first + i*diff}

    printArray(gold, "gold: ")
    printArray(result, "result: ")
    val cksum = result.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum  + " (SequentialWrites)")
    assert(cksum)
  }
}

// Args: None
object ChangingCtrMax extends SpatialTest {
  import spatial.dsl._

  val tileSize = 96
  val N = 5

  def changingctrmax[T:Type:Num](): Array[T] = {
    val result = DRAM[T](96)
    Accel {
      val rMem = SRAM[T](96)
      Sequential.Foreach(96 by 1) { i =>
        val accum = Reduce(0)(i by 1){ j => j }{_+_}
        rMem(i) = accum.value.to[T]
      }
      result(0::96) store rMem
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
    assert(cksum)
  }
}


// Args: 384
object FifoPushPop extends SpatialTest {
  import spatial.dsl._
  testArgs = List("384")

  def fifopushpop(N: Int) = {
    val tileSize = 96 (96 -> 96)

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
    assert(cksum)
  }
}


class UnitTests2 extends FlatSpec with Matchers {
  "BlockReduce2D" should "compile" in { BlockReduce2D.main(Array.empty) }
  "ScatterGather" should "compile" in { ScatterGather.main(Array.empty) }
  "InOutArg" should "compile" in { InOutArg.main(Array.empty) }
  "MultiplexedWriteTest" should "compile" in { MultiplexedWriteTest.main(Array.empty) }
  "BubbledWriteTest" should "compile" in { BubbledWriteTest.main(Array.empty) }
  "SequentialWrites" should "compile" in { SequentialWrites.main(Array.empty) }
  "ChangingCtrMax" should "compile" in { ChangingCtrMax.main(Array.empty) }
  "FifoPushPop" should "compile" in { FifoPushPop.main(Array.empty) }
}
