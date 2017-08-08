package spatial.models.characterization

import spatial.metadata._
import spatial.dsl._
import spatial._
import org.virtualized._

trait RegFiles extends Benchmarks {
  self: SpatialCompiler =>

  case class RegFile1DOp[T:Num:Type](depth: scala.Int, len: scala.Int, p: scala.Int)(val N: scala.Int) extends Benchmark {
    val prefix: JString = s"${depth}_${len}_${p}"
    def eval(): SUnit = {
      val outs = List.fill(N){ ArgOut[T] }

      Accel {
        val rfs = List.fill(N){ RegFile.buffer[T](len) }

        if (depth == 1) {
          Foreach(0 until 100 par p) { i =>
            rfs.foreach{ rf => rf.update(i, i.to[T]) }
          }
        }

        Foreach(0 until 1000) { _ =>
          List.tabulate(depth) { d =>
            Foreach(0 until 100 par p) { i =>
              rfs.zip(outs).foreach{case (rf,out) => if (d > 0) rf.update(i, i.to[T]) else out := rf(i) }
            }
          }
          ()
        }
      }
    }
  }

  case class RegFile2DOp[T:Num:Type](depth: scala.Int, rows: scala.Int, cols: scala.Int, p0: scala.Int, p1: scala.Int)(val N: scala.Int) extends Benchmark {
    val prefix: JString = s"${depth}_${rows}_${cols}_${p0}_${p1}"
    def eval(): SUnit = {
      val outs = List.fill(N)(ArgOut[T])

      Accel {
        val rfs = List.fill(N){ RegFile.buffer[T](rows, cols) }

        if (depth == 1) {
          Foreach(0 until 100 par p0) { i =>
            Foreach(0 until 100 par p1) { j =>
              rfs.foreach{rf => rf.update(i, j, i.to[T]) }
            }
          }
        }

        Foreach(0 until 1000) { _ =>
          List.tabulate(depth) { d =>
            Foreach(0 until 100 par p0) { i =>
              Foreach(0 until 100 par p1) { j =>
                rfs.zip(outs).foreach{case (rf,out) => if (d > 0) rf.update(i, j, i.to[T]) else out := rf(i, j) }
              }
            }
          }
          ()
        }
      }
    }
  }

  private val dims1d = List(2, 4, 8, 16, 32, 64)

  val dims2d = List(
    (2,2),
    (4,2),
    (4,4),
    (8,2),
    (8,4),
    (8,8),
    (16,2),
    (16,4),
    (32,2),
    (64,1),
    (64,2)
  )

  //gens ::= dims2d.flatMap{case (rows,cols) => List.tabulate(3){depth => MetaProgGen("Reg16", Seq(100,200), RegFile2DOp[Int16](depth, rows, cols)) } }
  gens :::= dims1d.flatMap{len => List.tabulate(3){depth => MetaProgGen("RegFile1D", Seq(100,200), RegFile1DOp[Int32](depth+1, len, 1)) } }
  gens :::= dims2d.flatMap{case (rows,cols) => List.tabulate(3){depth => MetaProgGen("RegFile2D", Seq(100,200), RegFile2DOp[Int32](depth+1, rows, cols, 1, 1)) } }

}
