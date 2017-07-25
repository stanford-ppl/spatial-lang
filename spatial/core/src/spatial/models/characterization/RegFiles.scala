package spatial.models.characterization

import spatial.metadata._
import spatial.dsl._

trait RegFiles extends Benchmarks {

  case class RegFile1DOp[T:Type:Bits](depth: scala.Int, len: scala.Int)(val N: scala.Int) extends Benchmark {
    val prefix: String = s"${depth}_${len}"
    def eval(): Unit = {
      val outs = List.fill(N)(ArgOut[T])
      val ins = List.fill(N)(ArgIn[T])

      ins.foreach(setArg(_, zero[T]))

      Accel {
        val rfs = List.fill(N){ RegFile.buffer[T](len) }

        Foreach(0 until 1000){_ =>
          List.tabulate(N-1){_ => Foreach(0 until 100, 0 until 100){(i,j) =>
            rfs.foreach{rf => rf.update(i, i.to[T]) }
          }}
          Foreach(0 until 100, 0 until 100){(i,j) =>
            rfs.zip(outs).foreach{case (rf,out) => out := rf(i) }
          }
          ()
        }
      }
    }
  }

  case class RegFile2DOp[T:Type:Bits](depth: scala.Int, rows: scala.Int, cols: scala.Int)(val N: scala.Int) extends Benchmark {
    val prefix: String = s"${depth}_${rows}_${cols}"
    def eval(): Unit = {
      val outs = List.fill(N)(ArgOut[T])
      val ins = List.fill(N)(ArgIn[T])

      ins.foreach(setArg(_, zero[T]))

      Accel {
        val rfs = List.fill(N){ RegFile.buffer[T](rows, cols) }

        Foreach(0 until 1000){_ =>
          List.tabulate(N-1){_ => Foreach(0 until 100, 0 until 100){(i,j) =>
            rfs.foreach{rf => rf.update(i,j, i.to[T]) }
          }}
          Foreach(0 until 100, 0 until 100){(i,j) =>
            rfs.zip(outs).foreach{case (rf,out) => out := rf(i,j) }
          }
          ()
        }
      }
    }
  }

  val dims1d = List(2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)

  val dims2d = List(
    (2,2),
    (4,2),
    (4,4),
    (8,2),
    (8,4),
    (8,8),
    (16,2),
    (16,4),
    (16,8),
    (16,16),
    (32,2),
    (32,4),
    (32,8),
    (32,16),
    (32,32),
    (64,2),
    (64,4),
    (64,8),
    (64,16)
  )

  //gens ::= dims2d.flatMap{case (rows,cols) => List.tabulate(3){depth => MetaProgGen("Reg16", Seq(100,200), RegFile2DOp[Int16](depth, rows, cols)) } }
  gens ::= dims1d.flatMap{len => List.tabulate(3){depth => MetaProgGen("Reg1D", Seq(100,200), RegFile1DOp[Int32](depth, len)) } }
  gens ::= dims2d.flatMap{case (rows,cols) => List.tabulate(3){depth => MetaProgGen("Reg2D", Seq(100,200), RegFile2DOp[Int32](depth, rows, cols)) } }

}
