package spatial.codegen.pirgen

import argon.codegen.pirgen.PIRCodegen
import spatial.api.SRAMExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait PIRGenSRAM extends PIRCodegen {
  val IR: SRAMExp with SpatialExp
  import IR._

  private var nbufs: List[(Sym[SRAMNew[_]], Int)]  = List()

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Option[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s" }.mkString(" + ") + ofs.map{o => src" + $o"}.getOrElse("")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@SRAMNew(dimensions) => 
      duplicatesOf(lhs).zipWithIndex.foreach{ case (mem, i) => 
        mem match {
          case BankedMemory(dims, depth, isAccum) =>
            //val strides = s"""List(${dims.map(_.banks).mkString(",")})"""
            //val numWriters = writersOf(lhs).filter{ write => dispatchOf(write, lhs) contains i }.distinct.length
            //val numReaders = readersOf(lhs).filter{ read => dispatchOf(read, lhs) contains i }.distinct.length
            if (depth == 1) {
            } else {
            }
          case DiagonalMemory(strides, banks, depth, isAccum) =>
            Console.println(s"NOT SUPPORTED, MAKE EXCEPTION FOR THIS!")
        }
      }
    
    case SRAMLoad(sram, dims, is, ofs) =>
      val dispatch = dispatchOf(lhs, sram)
      val rPar = 1 // Because this is SRAMLoad node    
      //emit(s"""// Assemble multidimR vector""")
      dispatch.foreach{ i => 
        val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
        val enable = src"""${parent}_en"""
      }

    case SRAMStore(sram, dims, is, ofs, v, en) =>
      //emit(s"""// Assemble multidimW vector""")
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) => 
      }

    case _ => super.emitNode(lhs, rhs)
  }

}
