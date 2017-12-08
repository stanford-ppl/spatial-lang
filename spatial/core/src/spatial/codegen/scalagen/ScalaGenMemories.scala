package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaFileGen
import argon.core._
import spatial.aliases._
import spatial.banking._
import spatial.utils._

trait ScalaGenMemories extends ScalaGenBits with ScalaFileGen {
  var globalMems: Boolean = false
  dependencies ::= FileDep("scalagen", "OOB.scala")
  dependencies ::= FileDep("scalagen", "BankedMemory.scala")
  dependencies ::= FileDep("scalagen", "Warn.scala")

  override def emitPostMain(): Unit = {
    super.emitPostMain()
    emit("Warn.close()")
  }

  def emitMem(lhs: Exp[_], x: String): Unit = {
    if (globalMems) emit(s"if ($lhs == null) $x")
    else emit(src"var $lhs: ${lhs.tp} = null")
  }

  def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Option[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s" }.mkString(" + ") + ofs.map{o => src" + $o"}.getOrElse("")
  }

  def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s"}.mkString(" + ")
  }

  def flattenConstDimsAddress(dims: Seq[Int], indices: Seq[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => dims.drop(i+1).product }
    indices.zip(strides).map{case (i,s) => src"$i*$s"}.mkString(" + ")
  }

  def flattenConstAddress(dims: Seq[Int], indices: Seq[Int]): Int = {
    val strides = List.tabulate(dims.length){i => dims.drop(i+1).product }
    indices.zip(strides).map{case (i,s) => i*s}.sum
  }

  private def oob(tp: Type[_], mem: Exp[_], lhs: Exp[_], inds: Seq[Exp[_]], pre: String, post: String, isRead: Boolean)(lines: => Unit) = {
    val name = u"$mem"
    val addr = if (inds.isEmpty && pre == "" && post == "") "err.getMessage"
    else "\"" + pre + "\" + " + "s\"\"\"${" + inds.map(quote).map(_ + ".toString").mkString(" + \", \" + ") + "}\"\"\" + \"" + post + "\""

    val op = if (isRead) "readOrElse" else "writeOrElse"
    open(s"OOB.$op({")
      lines
    closeopen("}, {err => ")
      emit(s"""Warn("[warn] ${lhs.ctx} Memory $name: Out of bounds $op at address " + $addr)""")
      if (isRead) emit(src"${invalid(tp)}")
    close("})")
  }

  def oobApply(tp: Type[_], mem: Exp[_], lhs: Exp[_], inds: Seq[Exp[_]], pre: String = "", post: String = "")(lines: => Unit) = {
    oob(tp, mem, lhs, inds, pre, post, isRead = true)(lines)
  }

  def oobBankedApply(tp: Type[_], mem: Exp[_], lhs: Exp[_], bank: Seq[Exp[_]], ofs: Exp[_], pre: String = "", post: String = "")(lines: => Unit) = {
    oob(tp, mem, lhs, bank :+ ofs, pre, post, isRead = true)(lines)
  }

  def oobUpdate(tp: Type[_], mem: Exp[_], lhs: Exp[_], inds: Seq[Exp[_]], pre: String = "", post: String = "")(lines: => Unit) = {
    oob(tp, mem, lhs, inds, pre, post, isRead = false)(lines)
  }

  def oobBankedUpdate(tp: Type[_], mem: Exp[_], lhs: Exp[_], bank: Seq[Exp[_]], ofs: Exp[_], pre: String = "", post: String = "")(lines: => Unit) = {
    oob(tp, mem, lhs, bank :+ ofs, pre, post, isRead = false)(lines)
  }


  def emitBankedInitMem(mem: Exp[_], init: Option[Seq[Exp[_]]])(tp: Type[_]): Unit = {
    val inst = instanceOf(mem)
    val dims = constDimsOf(mem)
    implicit val ctx: SrcCtx = mem.ctx

    val data = init match {
      case Some(elems) =>
        val nBanks = inst.nBanks.product
        val bankDepth = Math.ceil(dims.product.toDouble / nBanks).toInt
        val banks = multiLoopWithIndex(dims).map { case (is, i) =>
          val bankAddr = inst.constBankAddress(is)
          val ofs = inst.constBankOffset(mem, is)
          (elems(i), bankAddr, ofs)
        }.toArray.groupBy(_._2).map { case (_, vals) =>
          val elems = (0 until bankDepth).map { i => vals.find(_._3 == i).map(_._1).getOrElse(invalid(tp)) }
          src"Array[$tp]($elems)"
        }
        src"""Array[Array[$tp]](${banks.mkString("\n")})"""
      case None =>
        val banks = inst.totalBanks
        val bankDepth = Math.ceil(dims.product.toDouble / banks).toInt
        src"""Array.fill($banks){ Array.fill($bankDepth)(${invalid(tp)}) }"""
    }
    val dimensions = dims.map(_.toString).mkString("Seq(", ",", ")")
    val numBanks = inst.nBanks.map(_.toString).mkString("Seq(", ",", ")")

    def emitMemDef(rhs: => Unit) = {
      if (globalMems) {
        open(src"if ($mem eq null) { $mem = {")
        rhs
        close("}}")
      }
      else {
        open(src"val $mem = {")
        rhs
        close("}")
      }
    }

    if (isRegFile(mem)) {
      // HACK: Stage, then generate, the banking and offset addresses for the regfile on the fly
      val addr = Seq.fill(rankOf(mem)){ fresh[Index] }
      val bankAddrFunc = fakeStageScopeHack{
        val bank = inst.bankAddress(addr)
        implicit val vT: Type[VectorN[Index]] = VectorN.typeFromLen[Index](bank.length)
        Vector.fromseq[Index,VectorN](bank)
      }
      val offsetFunc = fakeStageScopeHack{ inst.bankOffset(mem,addr) }

      emitMemDef{
        val name = u""""$mem""""
        open(src"new ShiftableMemory($name, $dimensions, $numBanks, $data, ${invalid(tp)}, saveInit = ${init.isDefined}, {")
          emit(src"""case Seq(${addr.mkString(",")}) => """)
          bankAddrFunc.foreach(visitStm)
          emit(src"${bankAddrFunc.last.lhs.head}")
        close("},")
        open("{")
          emit(src"""case Seq(${addr.mkString(",")}) => """)
          offsetFunc.foreach(visitStm)
          emit(src"${offsetFunc.last.lhs.head}")
        close("})")
      }
    }
    else {
      emitMemDef {
        val name = u""""$mem""""
        emit(src"new BankedMemory($name, $dimensions, $numBanks, $data, ${invalid(tp)}, saveInit = ${init.isDefined})")
      }
    }
  }

  def emitBankedLoad[T:Type](lhs: Exp[_], mem: Exp[_], bank: Seq[Seq[Exp[Index]]], ofs: Seq[Exp[Index]], ens: Seq[Exp[Bit]]): Unit = {
    val bankAddr = bank.map(_.map(quote).mkString("Seq(", ",", ")")).mkString("Seq(", ",", ")")
    val ofsAddr  = ofs.map(quote).mkString("Seq(", ",", ")")
    val enables  = ens.map(quote).mkString("Seq(", ",", ")")
    val ctx = s""""${lhs.ctx}""""
    emit(src"val $lhs = $mem.apply($ctx, $bankAddr, $ofsAddr, $enables)")
  }

  def emitBankedStore[T:Type](lhs: Exp[_], mem: Exp[_], data: Seq[Exp[T]], bank: Seq[Seq[Exp[Index]]], ofs: Seq[Exp[Index]], ens: Seq[Exp[Bit]]): Unit = {
    val bankAddr = bank.map(_.map(quote).mkString("Seq(", ",", ")")).mkString("Seq(", ",", ")")
    val ofsAddr  = ofs.map(quote).mkString("Seq(", ",", ")")
    val enables  = ens.map(quote).mkString("Seq(", ",", ")")
    val datas    = data.map(quote).mkString("Seq(", ",", ")")
    val ctx = s""""${lhs.ctx}""""
    emit(src"val $lhs = $mem.update($ctx, $bankAddr, $ofsAddr, $enables, $datas)")
  }
}
