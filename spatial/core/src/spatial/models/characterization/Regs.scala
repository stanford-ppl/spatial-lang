package spatial.models.characterization

import spatial.dsl._
import spatial._
import org.virtualized._

trait Regs extends Benchmarks {
  self: SpatialCompiler =>  

  type Int48 = FixPt[TRUE,_48,_0]
  type Int96 = FixPt[TRUE,_96,_0]
  type Int128 = FixPt[TRUE,_128,_0]

  case class RegOp[I:INT](width: scala.Int, depth: scala.Int)(val N: scala.Int) extends Benchmark {
    type T = FixPt[TRUE,I,_0]
    val prefix: JString = s"${width}_${depth}"
    def eval(): SUnit = {
      val outs = List.fill(N)(ArgOut[T])
      val ins = List.fill(N)(ArgIn[T])

      ins.foreach(setArg(_, zero[T]))

      Accel {
        val regs = List.fill(N){ Reg.buffer[T] }

        Foreach(0 until 1000){i =>
          List.tabulate(depth-1){d =>
            Pipe { regs.foreach{reg => reg := i.as[T] << d } }
          }
          Pipe{ regs.zip(outs).foreach{case (reg,out) => out := reg.value } }
          ()
        }
      }
    }
  }

  gens :::= List.tabulate(7){depth => MetaProgGen("Reg", Seq(400,800,1000), RegOp[_8](8,depth+1)) }
  gens :::= List.tabulate(7){depth => MetaProgGen("Reg", Seq(400,800,1000), RegOp[_16](16,depth+1)) }
  gens :::= List.tabulate(7){depth => MetaProgGen("Reg", Seq(400,800,1000), RegOp[_32](32,depth+1)) }
  gens :::= List.tabulate(7){depth => MetaProgGen("Reg", Seq(400,800,1000), RegOp[_64](64,depth+1)) }

  //gens ::= List.tabulate(7){depth => MetaProgGen("Reg1", Seq(50,100,200), RegOp[Bit](depth)) }
  //gens ::= List.tabulate(7){depth => MetaProgGen("Reg48", Seq(50,100,200), RegOp[Int48](depth)) }
  //gens ::= List.tabulate(7){depth => MetaProgGen("Reg96", Seq(50,100,200), RegOp[Int96](depth)) }
  //gens ::= List.tabulate(7){depth => MetaProgGen("Reg128", Seq(50,100,200), RegOp[Int128](depth)) }
}
