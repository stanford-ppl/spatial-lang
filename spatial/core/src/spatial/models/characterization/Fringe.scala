package spatial.models.characterization

import spatial.dsl._
import spatial._
import org.virtualized._

trait Fringe extends Benchmarks {
  self: SpatialCompiler =>

  case class Transfer1D[T:Type:Bits](width: scala.Int, p: scala.Int, store: scala.Boolean, aligned: scala.Boolean)(val N: scala.Int) extends Benchmark {
    val prefix = s"1_${width}_${p}"
    override def eval(): SUnit = {
      val drams = List.fill(N){ DRAM[T](12288) }
      val input = ArgIn[Int]
      Accel {
        val width: Int = if (aligned) 96.to[Int] else input.value
        val srams = List.fill(N){ SRAM[T](96) }
        Foreach(12288 by 96){ i =>
          Parallel {
            srams.zip(drams).foreach { case (sram, dram) =>
              if (store) { dram(i::i+width par p) store sram } else { sram load dram(i::i+width par p) }
            }
          }
        }
      }
    }
  }

  case class Transfer2D[T:Type:Bits](width: scala.Int, p: scala.Int, store: scala.Boolean, aligned: scala.Boolean)(val N: scala.Int) extends Benchmark {
    val prefix = s"2_${width}_${p}"
    override def eval(): SUnit = {
      val drams = List.fill(N){ DRAM[T](64,12288) }
      val input = ArgIn[Int]
      Accel {
        val width: Int = if (aligned) 96.to[Int] else input.value
        val srams = List.fill(N){ SRAM[T](4,96) }
        Foreach(64 by 4, 12288 by 96){(i,j)=>
          Parallel {
            srams.zip(drams).foreach { case (sram, dram) =>
              if (store) { dram(i::i+4, j::j+width par p) store sram } else { sram load dram(i::i+4, j::j+width par p) }
            }
          }
        }
      }
    }
  }


  val ldPars = List(1, 4, 8, 16)

  gens :::= ldPars.map{p => MetaProgGen("AlignedLoad", Seq(2,4,8), Transfer1D[Int8](8, p, store = false, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedLoad", Seq(2,4,8), Transfer1D[Int16](16, p, store = false, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedLoad", Seq(2,4,8), Transfer1D[Int32](32, p, store = false, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedLoad", Seq(2,4,8), Transfer1D[Int64](64, p, store = false, aligned=true)) }

  gens :::= ldPars.map{p => MetaProgGen("AlignedLoad", Seq(2,4,8), Transfer2D[Int8](8, p, store = false, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedLoad", Seq(2,4,8), Transfer2D[Int16](16, p, store = false, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedLoad", Seq(2,4,8), Transfer2D[Int32](32, p, store = false, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedLoad", Seq(2,4,8), Transfer2D[Int64](64, p, store = false, aligned=true)) }

  gens :::= ldPars.map{p => MetaProgGen("AlignedStore", Seq(2,4,8), Transfer1D[Int8](8, p, store=true, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedStore", Seq(2,4,8), Transfer1D[Int16](16, p, store=true, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedStore", Seq(2,4,8), Transfer1D[Int32](32, p, store=true, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedStore", Seq(2,4,8), Transfer1D[Int64](64, p, store=true, aligned=true)) }

  gens :::= ldPars.map{p => MetaProgGen("AlignedStore", Seq(2,4,8), Transfer2D[Int8](8, p, store=true, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedStore", Seq(2,4,8), Transfer2D[Int16](16, p, store=true, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedStore", Seq(2,4,8), Transfer2D[Int32](32, p, store=true, aligned=true)) }
  gens :::= ldPars.map{p => MetaProgGen("AlignedStore", Seq(2,4,8), Transfer2D[Int64](64, p, store=true, aligned=true)) }

  gens :::= ldPars.map{p => MetaProgGen("UnalignedLoad", Seq(2,4,8), Transfer1D[Int8](8, p, store = false, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedLoad", Seq(2,4,8), Transfer1D[Int16](16, p, store = false, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedLoad", Seq(2,4,8), Transfer1D[Int32](32, p, store = false, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedLoad", Seq(2,4,8), Transfer1D[Int64](64, p, store = false, aligned=false)) }

  gens :::= ldPars.map{p => MetaProgGen("UnalignedLoad", Seq(2,4,8), Transfer2D[Int8](8, p, store = false, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedLoad", Seq(2,4,8), Transfer2D[Int16](16, p, store = false, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedLoad", Seq(2,4,8), Transfer2D[Int32](32, p, store = false, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedLoad", Seq(2,4,8), Transfer2D[Int64](64, p, store = false, aligned=false)) }

  gens :::= ldPars.map{p => MetaProgGen("UnalignedStore", Seq(2,4,8), Transfer1D[Int8](8, p, store=true, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedStore", Seq(2,4,8), Transfer1D[Int16](16, p, store=true, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedStore", Seq(2,4,8), Transfer1D[Int32](32, p, store=true, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedStore", Seq(2,4,8), Transfer1D[Int64](64, p, store=true, aligned=false)) }

  gens :::= ldPars.map{p => MetaProgGen("UnalignedStore", Seq(2,4,8), Transfer2D[Int8](8, p, store=true, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedStore", Seq(2,4,8), Transfer2D[Int16](16, p, store=true, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedStore", Seq(2,4,8), Transfer2D[Int32](32, p, store=true, aligned=false)) }
  gens :::= ldPars.map{p => MetaProgGen("UnalignedStore", Seq(2,4,8), Transfer2D[Int64](64, p, store=true, aligned=false)) }
}
