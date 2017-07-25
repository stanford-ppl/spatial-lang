package spatial.models

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext

import org.virtualized._
import spatial._
import spatial.dsl._
import argon.core.State
import java.lang.{String => JString}

object Benchmark extends SpatialCompiler {


  //To implement by Richard
  def area(dir: JString): Map[JString, scala.Int] = {
    Thread.sleep(1000)
    Map(("question of the universe", 42))
  }


  val NUM_OP: scala.Int = 10
  val NUM_PAR_SYNTH: scala.Int = 2

  sealed trait Op {
    def name: JString
  }
  case class StaticOp[T](name: JString, f: () => T) extends Op
  case class UnOp[T](name: JString, f: T => T) extends Op
  case class BinOp[T](name: JString, f: (T, T) => T) extends Op
  case class TriOp[T](name: JString, f: (T, T, T) => T) extends Op  

  type SpatialProg = () => Unit
  type NamedSpatialProg = (JString, SpatialProg) 

  case class MetaProgGen[T: Type: Bits](name: JString, default: T, statics: Seq[StaticOp[T]], unaries: Seq[UnOp[T]], binaries: Seq[BinOp[T]], trinaries: Seq[TriOp[T]]) {
    def typ = implicitly[Type[T]]
    def expand: Seq[NamedSpatialProg] =
      statics.map(x => (x.name, (() => templateStatic(x)))) ++
    unaries.map(x => (x.name, () => templateUnary(x, default))) ++
    binaries.map(x => (x.name, () => templateBinary(x, default))) ++
    trinaries.map(x => (x.name, () => templateTrinary(x, default)))     
  }

  case class Baseline(static: SpatialProg, unary: SpatialProg, binary: SpatialProg, trinaries: SpatialProg)

  def templateStatic[T: Type: Bits](x: StaticOp[T]) = {

    val outs = List.fill(NUM_OP)(ArgOut[T])

    Accel {
      outs.foreach(_ := x.f())
    }

  }

  def templateUnary[T: Type: Bits](x: UnOp[T], default: T) = {

    val outs = List.fill(NUM_OP)(ArgOut[T])
    val ins = List.fill(NUM_OP)(ArgIn[T])

    ins.foreach(setArg(_, default))

    Accel {
      ins.zip(outs).foreach { case (in, out) =>
        out := x.f(getArg(in))
      }
    }

  }  

  def templateBinary[T: Type: Bits](x: BinOp[T], default: T) = {

    val outs = List.fill(NUM_OP)(ArgOut[T])
    val insA = List.fill(NUM_OP)(ArgIn[T])
    val insB = List.fill(NUM_OP)(ArgIn[T])

    (insA:::insB).foreach(setArg(_, default))

    Accel {
      (insA.zip(insB)).zip(outs).foreach { case ((inA, inB), out) =>
        out := x.f(getArg(inA), getArg(inB))
      }
    }
  }  

  def templateTrinary[T: Type: Bits](x: TriOp[T], default: T) = {

    val outs = List.fill(NUM_OP)(ArgOut[T])
    val insA = List.fill(NUM_OP)(ArgIn[T])
    val insB = List.fill(NUM_OP)(ArgIn[T])
    val insC = List.fill(NUM_OP)(ArgIn[T])    

    (insA:::insB).foreach(setArg(_, default))

    Accel {
      (insA.zip(insB).zip(insC)).zip(outs).foreach { case (((inA, inB), inC), out) =>
        out := x.f(getArg(inA), getArg(inB), getArg(inC))
      }
    }
  }  

  
  val metaPrograms: List[MetaProgGen[_]] = List(
    MetaProgGen[Int8]("Primitives Int8", 0, Seq(), Seq(), Seq(BinOp("Mult", _*_)), Seq())
  )

  def baseline: Baseline = ???
  
  val programs: List[NamedSpatialProg] =
    metaPrograms
      .flatMap(x =>
        x.expand
      )

  val stagingArgs = scala.Array("--synth")

  def storeArea(name: JString, area: Map[JString, scala.Int]) = {
    Console.println(name, area)
  }

  def main(args: scala.Array[JString]) {
    val chiseled = programs.map(x => {
      //compileProgram(x._2)
      Thread.sleep(1000)
      Console.println(x._1 + " chisel generated ")
      x._1
    })

    val exec = java.util.concurrent.Executors.newFixedThreadPool(NUM_PAR_SYNTH)
    implicit val ec = ExecutionContext.fromExecutor(exec)

    val workers = chiseled.map(x => Future {
      storeArea(x, area(x))
    })

    workers.foreach(Await.ready(_, Duration.Inf))
    Console.println("COMPLETED")
    exec.shutdown()
  }

}
