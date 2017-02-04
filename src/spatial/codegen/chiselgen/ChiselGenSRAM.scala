package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.SRAMExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait ChiselGenSRAM extends ChiselCodegen {
  val IR: SRAMExp with SpatialExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.bits}]"
    case _ => super.remap(tp)
  }

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case SRAMNew(dims)=> 
              s"x${lhs.id}_sram"
            case _ =>
              super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Option[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s" }.mkString(" + ") + ofs.map{o => src" + $o"}.getOrElse("")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@SRAMNew(dims) => 
        val sizes = dims.map{dim => boundOf(dim).toInt}
        def quote2D(ind: List[Exp[Any]], i: Int) = if (i >= ind.length) "0" else src"${ind(i)}"
        val read_pars = readersOf(lhs).map{read => 
          Console.println(read)
        }
        val read_head = read_pars.head

        // val write_pars = writersOf(lhs).map{write => parIndicesOf(write.node).map{ind => quote2D(ind, 0)}.length }
        // val write_head = write_pars.head

        val dups = duplicatesOf(lhs)
        dups.zipWithIndex.foreach { case (r, i) =>
          // val banks = Banking(r)//.banking.map(_.banks).mkString("List(", ",", ")")
          val strides = sizes.map{ i => 1 }// TODO: Use getStride(r), but hardcoded to 1 because it works for most apps
          if (r.depth == 1) {
            val numDistinctWriters = writersOf(lhs).length
          //   emitGlobal(src"""${lhs}_${i} = Module(new SRAM(List(${sizes.map{q=>src"$q"}.mkString(",")}), 32,
          // ${banks}, ${strides}, $numDistinctWriters, 1, 
          // ${write_head}, ${read_head})) /* ${nameOf(lhs).getOrElse("")} */""")
          } else if (r.depth >= 2) {
            val numDistinctWriters = 1 // TODO: Figure out what this business was all about writersOf(lhs).map{writer => parentOf(topControllerOf(writer))}.distinct.length

          //   val numReaders_for_duplicate = readersOf(lhs).filter{r => instanceIndicesOf(r, lhs).contains(i) }.map{r => parentOf(r.controlNode)}.distinct.length
          //   emitGlobal(src"""${lhs}_${i} = Module(new NBufSRAM(List(${sizes.map{q=>src"$q"}.mkString(",")}), ${r.depth}, 32,
          // ${banks}, ${strides}, $numDistinctWriters, ${numReaders_for_duplicate}, 
          // ${write_head}, ${read_head})) /* ${nameOf(lhs).getOrElse("")} */""")
          }
        }

    
    case SRAMLoad(sram, dims, is, ofs) =>
      emit(src"val $lhs = $sram.apply(${flattenAddress(dims,is,Some(ofs))})")

    case SRAMStore(sram, dims, is, ofs, v, en) =>
      emit(src"val $lhs = if ($en) $sram.update(${flattenAddress(dims,is,Some(ofs))}, $v)")

    case _ => super.emitNode(lhs, rhs)
  }
}
