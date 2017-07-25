package spatial.models.characterization

import spatial.dsl._
import spatial._
import org.virtualized._

trait SRAMs extends Benchmarks {
  self: SpatialCompiler =>

  case class SRAM1DOp[T:Num:Type:Bits](depth: scala.Int, len: scala.Int, p: scala.Int)(val N: scala.Int) extends Benchmark {
    val prefix: JString = s"${depth}_${len}_${p}"
    def eval(): Unit = {
      val outs = List.fill(N)(ArgOut[T])
      val ins = List.fill(N)(ArgIn[T])

      ins.foreach(setArg(_, zero[T]))

      Accel {
        val rfs = List.fill(N){ SRAM.buffer[T](len) }

        Foreach(0 until 1000){_ =>
          List.tabulate(N-1){_ => Foreach(0 until 100, 0 until 100 par p){(i,j) =>
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

  private val dims1d = List(512, 1024, 2048, 4096, 8192, 16384, 32768, 65536)
  val pars = List(1, 2, 4)

  //gens ::= dims2d.flatMap{case (rows,cols) => List.tabulate(3){depth => MetaProgGen("Reg16", Seq(100,200), RegFile2DOp[Int16](depth, rows, cols)) } }
  gens :::= dims1d.flatMap{len =>
    pars.flatMap{par =>
      List.tabulate(3) { depth => MetaProgGen("SRAM", Seq(10, 50, 100, 200), SRAM1DOp[Int32](depth, len, par)) }
    }
  }


}
