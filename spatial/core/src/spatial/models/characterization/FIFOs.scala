package spatial.models.characterization

import spatial.dsl._
import spatial.SpatialCompiler
import org.virtualized._

trait FIFOs extends Benchmarks {
  self: SpatialCompiler =>

  case class FIFOBench[T:Type:Num](size: scala.Int, p: scala.Int)(val N: scala.Int) extends Benchmark {
    val prefix = s"${size}_${p}"
    override def eval(): SUnit = {
      val outs = List.fill(N){ ArgOut[T] }

      Accel {
        val fifos = List.fill(N){ FIFO[T](size) }
        Foreach(0 until size par p){i =>
          fifos.foreach{fifo => fifo.enq(i.to[T]) }
        }
        Pipe {
          fifos.zip(outs).foreach{case (fifo,out) => out := fifo.deq() }
        }
      }

    }
  }

  val sizes = List(2, 4, 8, 16, 32, 64, 256, 512, 1024, 2048, 4096, 8192)
  val pars  = List(1, 2, 4, 8)

  gens :::= sizes.flatMap{size =>
    pars.flatMap{par =>
      List(
        MetaProgGen("FIFO8", Seq(10,50), FIFOBench[Int8](size, par)),
        MetaProgGen("FIFO16", Seq(10,50), FIFOBench[Int16](size, par)),
        MetaProgGen("FIFO32", Seq(10,50), FIFOBench[Int32](size, par)),
        MetaProgGen("FIFO64", Seq(10,50), FIFOBench[Int64](size, par))
      )
    }
  }

}
