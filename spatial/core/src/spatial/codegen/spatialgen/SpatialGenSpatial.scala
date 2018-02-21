package spatial.codegen.spatialgen

import scala.language.existentials
import argon.codegen.{Codegen, FileDependencies, FileGen}
import argon.core.{Block, Const, Exp, Op, Param, Sym, Type}
import argon.lang.FixPt
import argon.nodes._
import spatial.nodes._

trait SpatialGenSpatial extends Codegen with FileDependencies with FileGen {
  override val name = "Spatial Codegen"
  override val lang: String = "Spatial"
  override val ext: String = "scala"

  override protected def mainFile: String = IR.config.name

  private val imports = Seq(
    "spatial.dsl._",
    "org.virtualized._",
    "spatial.stdlib._",
    "spatial.targets._"
  )

  private def indent(): Unit =
    streamTab(streamName) = streamTab.getOrElse(streamName, 0) + 1

  private def dedent(): Unit =
    streamTab(streamName) = Math.max(streamTab.getOrElse(streamName, 0) - 1, 0)

  override protected def emitMain[S: Type](b: Block[S]): Unit = {
    imports.foreach {
      s => emit(s"import $s")
    }
    emit("")
    emit(s"object $mainFile extends SpatialApp {")
    indent()
    emit("@virtualize")
    emit("def main() {")
    indent()
    emitBlock(b)
    dedent()
    emit("}")
    dedent()
    emit("}")
  }

  private def ExpToString(v: Exp[_]): String = {
    v match {
      case Const(c) =>
        c match {
          case s: String => "\"" + s + "\""
          case _ => c.toString
        }
      case _ =>
        v match {
          case s: Sym[_] => v.toString
          case p: Param[_] => p.x.toString
          case _ => v.toString
        }
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      case Hwblock(func, isForever) =>
        if (isForever) emit("Accel (*) {")
        else emit("Accel {")
        indent()
        emitBlock(func)
        dedent()
        emit("}")

      case UnitPipe(ens, func) =>
        if (ens.nonEmpty) throw new Exception(s"Enables not empty: $ens")
        emit("Pipe {")
        indent()
        emitBlock(func)
        dedent()
        emit("}")

      case OpForeach(ens, cchain, func, iters) =>
        if (ens.nonEmpty) throw new Exception(s"Enables not empty: $ens")
        emit(s"Foreach(${ExpToString(cchain)}) { (${iters.mkString(", ")}) =>")
        indent()
        emitBlock(func)
        dedent()
        emit("}")

      case SRAMStore(mem, _, is, _, data, _) =>
        emit(s"$mem(${(is map ExpToString).mkString(", ")}) = ${ExpToString(data)}")

      case RegWrite(reg, data, _) =>
        emit(s"$reg := ${ExpToString(data)}")

      case PrintlnIf(enable, x) =>
        val s = ExpToString(x)
        emit(s"println($s)")

      case _ =>
        val rhsString = rhs match {
          case ArgInNew(init) =>
            val t = init.tp
            s"ArgIn[$t]"
          case ArgOutNew(init) =>
            val t = init.tp
            s"ArgOut[$t]"
          case InputArguments() =>
            s"args"
          case ArrayApply(exp, i) =>
            val iString = ExpToString(i)
            s"$exp($iString)"
          case StringToFixPt(x) =>
            val stfp = rhs.asInstanceOf[StringToFixPt[_, _, _]]
            s"${stfp.x}.to[FixPt[${stfp.s}, ${stfp.i}, ${stfp.f}]]"
          case SetArg(reg, value) =>
            val s = ExpToString(value)
            val s2 = ExpToString(reg)
            s"setArg($s2, $s)"
          case GetArg(reg) =>
            val s = ExpToString(reg)
            s"getArg($s)"
          case StringConcat(x, y) =>
            val s1 = ExpToString(x)
            val s2 = ExpToString(y)
            s"$s1 + $s2"
          case ToString(x) =>
            s"$x.toString"
          case SRAMNew(dims) =>
            rhs match {
              case sram: SRAMNew[_, _] =>
                val sizes = sram.dims map ExpToString
                s"SRAM[${sram.mT}](${sizes.mkString(", ")})"
            }
          case CounterNew(start, end, step, par) =>
            val s1 = ExpToString(start)
            val s2 = ExpToString(end)
            val s3 = ExpToString(step)
            val s4 = ExpToString(par)
            s"$s1 until $s2 by $s3 par $s4"
          case CounterChainNew(counters) =>
            val strs = counters map ExpToString
            strs.mkString(", ")
          case FixAdd(x, y) =>
            s"${ExpToString(x)} + ${ExpToString(y)}"
          case SRAMLoad(mem, _, is, _, _) =>
            s"$mem(${(is map ExpToString).mkString(", ")})"
          case RegRead(reg) =>
            s"$reg"
          case _ =>
            println(s"Not defined: $rhs")
            ""
        }
        emit(s"val $lhs = $rhsString")
    }
  }

  override protected def emitBlock(b: Block[_]): Unit = {
    super.emitBlock(b)
  }
}
