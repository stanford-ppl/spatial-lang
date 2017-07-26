package spatial.models.characterization

import spatial.dsl._
import spatial._
import org.virtualized._

trait SRAMs extends Benchmarks {
  self: SpatialCompiler =>

  case class SRAM1DOp[T:Num:Type](depth: scala.Int, len: scala.Int, p: scala.Int)(val N: scala.Int) extends Benchmark {
    val prefix: JString = s"${depth}_${len}_${p}"
    def eval(): Unit = {
      val outs = List.fill(N){ ArgOut[T] }

      Accel {
        val rfs = List.fill(N){ SRAM.buffer[T](len) }

        Foreach(0 until 1000) { _ =>
          List.tabulate(depth) { _ =>
            Foreach(0 until 100 par p) { i =>
              rfs.foreach{ rf => rf.update(i, i.to[T]) }
            }
          }
          ()
        }
        Pipe {
          rfs.zip(outs).foreach { case (rf, out) => out := rf(0) }
        }
      }
    }
  }

  case class SRAM2DOp[T:Num:Type](depth: scala.Int, rows: scala.Int, cols: scala.Int, p0: scala.Int, p1: scala.Int)(val N: scala.Int) extends Benchmark {
    val prefix: JString = s"${depth}_${rows}_${cols}_${p0}_${p1}"
    def eval(): Unit = {
      val outs = List.fill(N)(ArgOut[T])

      Accel {
        val rfs = List.fill(N){ SRAM.buffer[T](rows, cols) }

        Foreach(0 until 1000) { _ =>
          List.tabulate(depth) { _ =>
            Foreach(0 until 100 par p0) { i =>
              Foreach(0 until 100 par p1) { j =>
                rfs.foreach { rf => rf.update(i, j, i.to[T]) }
              }
            }
          }
          ()
        }
        Pipe {
          rfs.zip(outs).foreach{case (rf, out) => out := rf(0,0) }
        }
      }
    }
  }

  private val dims1d = List(256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536)
  private val pars1d = List(1, 2, 4)

  gens :::= dims1d.flatMap{len =>
    pars1d.flatMap{par =>
      List.tabulate(3) { depth => MetaProgGen("SRAM1D", Seq(10, 50, 100), SRAM1DOp[Int32](depth, len, par)) }
    }
  }

  private val dims2d = List(
    (512, 8),
    (512, 16),
    (512, 32),
    (512, 64),
    (512, 128),
    (1024, 8),
    (1024, 16),
    (1024, 32),
    (1024, 64),
    (2048, 8),
    (2048, 16),
    (2048, 32),
    (4096, 8),
    (4096, 16),
    (8192, 8)
  )
  private val pars2d = List((1,1), (1,2), (1,4), (2,1), (2,2), (2,4), (4,1), (4,2), (4,4))

  gens :::= dims2d.flatMap{case (rows,cols) =>
    pars2d.flatMap{case (p0,p1) =>
      List.tabulate(3){ depth => MetaProgGen("SRAM2D", Seq(10, 50, 100), SRAM2DOp[Int32](depth, rows, cols, p0, p1)) }
    }
  }
}
