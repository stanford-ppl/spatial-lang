package spatial.models.characterization

import spatial.dsl._
import spatial._
import org.virtualized._

trait Regs extends Benchmarks {
  self: SpatialCompiler =>  

  type Int48 = FixPt[TRUE,_48,_0]
  type Int96 = FixPt[TRUE,_96,_0]
  type Int128 = FixPt[TRUE,_128,_0]

  case class RegOp[T:Type:Bits](depth: scala.Int)(val N: scala.Int) extends Benchmark {
    val prefix: JString = depth.toString
    private val nbits: scala.Int = bits[T].length
    def eval(): SUnit = {
      val outs = List.fill(N)(ArgOut[T])
      val ins = List.fill(N)(ArgIn[T])

      ins.foreach(setArg(_, zero[T]))

      Accel {
        val regs = List.fill(N){ Reg.buffer[T] }

        Foreach(0 until 1000){_ =>
          List.tabulate(N-1){_ => Pipe {
            regs.foreach{reg =>
              // TODO: Should have syntax support for this kind of bit twiddling
              val value = reg.value
              val allbits = List.tabulate(nbits){i => if (i == 0) value(nbits-1) else value(i - 1) }
              implicit val vT = VectorN.typeFromLen[Bit](nbits)
              val vec: VectorN[Bit] = wrap(Vector.fromseq[Bit,VectorN](unwrap(allbits)))
              reg := vec.as[T]
            }
          }}
          Pipe{ regs.zip(outs).foreach{case (reg,out) => out := reg.value } }
          ()
        }
      }
    }
  }

  //gens ::= List.tabulate(7){depth => MetaProgGen("Reg1", Seq(50,100,200), RegOp[Bit](depth)) }
  gens :::= List.tabulate(7){depth => MetaProgGen("Reg8", Seq(50,100,200), RegOp[Int8](depth)) }
  gens :::= List.tabulate(7){depth => MetaProgGen("Reg16", Seq(50,100,200), RegOp[Int16](depth)) }
  gens :::= List.tabulate(7){depth => MetaProgGen("Reg32", Seq(50,100,200), RegOp[Int32](depth)) }
  //gens ::= List.tabulate(7){depth => MetaProgGen("Reg48", Seq(50,100,200), RegOp[Int48](depth)) }
  gens :::= List.tabulate(7){depth => MetaProgGen("Reg64", Seq(50,100,200), RegOp[Int64](depth)) }
  //gens ::= List.tabulate(7){depth => MetaProgGen("Reg96", Seq(50,100,200), RegOp[Int96](depth)) }
  //gens ::= List.tabulate(7){depth => MetaProgGen("Reg128", Seq(50,100,200), RegOp[Int128](depth)) }
}
