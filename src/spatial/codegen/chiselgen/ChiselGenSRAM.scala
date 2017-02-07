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
    case op@SRAMNew(dimensions) => 
      withStream(getStream("GlobalWires")) {
        duplicatesOf(lhs).zipWithIndex.foreach{ case (mem, i) => 
          mem match {
            case BankedMemory(dims, depth) =>
              val strides = s"""List(${dims.map{ d => d match {
                case StridedBanking(_, s) => s
                case _ => 1
              }}.mkString(",")})"""
              val numWriters = writersOf(lhs).map{access => portsOf(access, lhs, i)}.distinct.length // Count writers accessing this port
              val numReaders = readersOf(lhs).map{access => portsOf(access, lhs, i)}.distinct.length // Count writers accessing this port
              if (depth == 1) {
                open(src"""val ${lhs}_$i = Module(new SRAM(List(${dimensions.mkString(",")}), 32, """)
                emit(src"""List(${dims.map(_.banks).mkString(",")}), $strides,""")
                emit(src"""$numWriters, $numReaders, """)
                emit(src"""${dims.map(_.banks).reduce{_*_}}, ${dims.map(_.banks).reduce{_*_}}, "BankedMemory" // TODO: Be more precise with parallelizations """)
                close("))")
              } else {
                open(src"""val ${lhs}_$i = Module(new NBufSRAM(List(${dimensions.mkString(",")}), $depth, 32,""")
                emit(src"""List(${dims.map(_.banks).mkString(",")}), $strides,""")
                emit(src"""$numWriters, $numReaders, """)
                emit(src"""${dims.map(_.banks).reduce{_*_}}, ${dims.map(_.banks).reduce{_*_}}, "BankedMemory" // TODO: Be more precise with parallelizations """)
                close("))")
              }
            case DiagonalMemory(strides, banks, depth) => 
              Console.println(s"NOT SUPPORTED, MAKE EXCEPTION FOR THIS!")
          }
        }
      }
    
    case SRAMLoad(sram, dims, is, ofs) =>
      val dispatch = dispatchOf(lhs, sram)
      val rPar = 1 // Because this is SRAMLoad node    
      emit(s"""// Assemble multidimR vector""")
      dispatch.foreach{ i => 
        val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
        val enable = src"""${parent}_en"""
        emit(src"""val ${lhs}_rVec = Wire(Vec(${rPar}, new multidimR(${dims.length}, 32)))""")
        emit(src"""${lhs}_rVec(0).en := $enable""")
        is.zipWithIndex.foreach{ case(ind,j) => 
          emit(src"""${lhs}_rVec(0).addr($j) := ${ind}""")
        }
        val p = portsOf(lhs, sram, i).head
        Console.println(s"ports of $lhs sram $sram dup $i is ${portsOf(lhs, sram,i)}")
        emit(src"""${sram}_$i.connectRPort(Vec(${lhs}_rVec.toArray), $p)""")
        emit(src"""val $lhs = ${sram}_$i.io.output.data(${rPar}*$p)""")
      }

    case SRAMStore(sram, dims, is, ofs, v, en) =>
      emit(s"""// Assemble multidimW vector""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(1, new multidimW(${dims.length}, 32))) """)
      emit(src"""${lhs}_wVec(0).data := ${v}""")
      emit(src"""${lhs}_wVec(0).en := ${en}""")
      is.zipWithIndex.foreach{ case(ind,j) => 
        emit(src"""${lhs}_wVec(0).addr($j) := ${ind}""")
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) => 
        val p = portsOf(lhs, sram, i).mkString(",")
        val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
        val enable = src"""${parent}_en"""
        emit(src"""${sram}_$i.connectWPort(${lhs}_wVec, ${enable}, List(${p})) """)
      }

//     val writers = writersOf(sram)
//     val writer = writers.find(_.node == write).get
//     //val EatAlias(ww) = write -- This is unnecessary (can't be bound)
//     val distinctParents = writers.map{writer => parentOf(writer.controlNode)}.distinct
//     val allParents = writers.map{writer => parentOf(writer.controlNode)}
//     if (distinctParents.length < allParents.length) {
//       Console.println("[WARNING] Bram $sram has multiple writers controlled by the same controller, which should only happen in CharBramTest!")
//       // throw MultipleWriteControllersException(sram, writersOf(sram))
//     }
//     val writeCtrl = writer.controlNode

//     // Figure out if this Ctrl is an accumulation, since we need to do this now that there can be many writers
//     val isAccumCtrl = writeCtrl match {
//         case Deff(d:OpReduce[_,_]) => true
//         case Deff(d:OpForeach) => false
//         case Deff(d:UnrolledReduce[_,_]) => true
//         case Deff(d:UnrolledForeach) =>
//           if (childrenOf(parentOf(writeCtrl).get).indexOf(writeCtrl) == childrenOf(parentOf(writeCtrl).get).length-1) {
//             styleOf(writeCtrl) match {
//               case InnerPipe => true
//               case _ => false
//             }
//           } else {
//             false
//           }
//         case Deff(d:UnitPipe) => true // Not sure why but this makes matmult work
//         case p => throw UnknownParentControllerException(sram, write, writeCtrl)
//     }
//     val globalEn = if (isAccum(sram) & isAccumCtrl) {
//       writeCtrl match {
//         case Deff(_: UnitPipe) => s"${quote(writeCtrl)}_done /* Not sure if this is right */"
//         case Deff(a) => s"${quote(writeCtrl)}_datapath_en /*& ${quote(writeCtrl)}_redLoop_done *//*wtf pipe is $a*/"
//         case _ => s"${quote(writeCtrl)}_datapath_en & ${quote(writeCtrl)}_redLoop_done /*no def node*/"
//       }
//     } else {
//       s"${quote(writeCtrl)}_datapath_en /*old behavior mask*/"
//     }

//     val dups = allDups.zipWithIndex.filter{dup => instanceIndicesOf(writer,sram).contains(dup._2) }

//     val inds = parIndicesOf(write)
//     val num_dims = dimsOf(sram).length
//     val wPar = inds.length

//     if (inds.isEmpty) throw NoParIndicesException(sram, write)

//     val Deff(value_type) = value


//     emit(s"""// Assemble multidimW vector
// val ${quote(write)}_wVec = Wire(Vec(${wPar}, new multidimW(${num_dims}, 32))) """)
//     value match {
//       case Deff(d:ListVector[_]) => // zip up vector nodes
//         emit(s"""
// ${quote(write)}_wVec.zip(${quote(value)}).foreach {case (w,d) => w.data := d}
// ${quote(write)}_wVec.zip(${quote(ens)}).foreach {case (w,e) => w.en := e}""")
//       case _ => // Otherwise, just connect one thing
//         emit(s"""${quote(write)}_wVec(0).data := ${quote(value)}
// ${quote(write)}_wVec(0).en := ${quote(ens)}""")
//     }
//     inds.zipWithIndex.foreach{ case(ind,i) => 
//       ind.zipWithIndex.foreach { case(wire,j) =>
//         emit(s"""${quote(write)}_wVec($i).addr($j) := ${quote(wire)}""")
//       }
//     }
//     dups.foreach{ case (d,i) =>
//       val p = portsOf(write, sram, i).mkString(",")
//       emit(s"""${quote(sram)}_$i.connectWPort(${quote(write)}_wVec, ${quote(globalEn)}, List(${p})) """)
//     }

    case _ => super.emitNode(lhs, rhs)
  }
}
