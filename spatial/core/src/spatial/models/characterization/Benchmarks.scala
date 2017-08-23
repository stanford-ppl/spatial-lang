package spatial.models
package characterization

import argon.core.State
import spatial._

import scala.util._
import scala.io.Source
import scala.collection.mutable.ArrayBuffer

trait Benchmarks {
  self: SpatialCompiler =>

  type SUnit = scala.Unit
  type JString = java.lang.String
  type SpatialProg = () => SUnit
  type NamedSpatialProg = (JString, SpatialProg)

  trait Benchmark {
    def prefix: JString
    def N: scala.Int
    def name: JString = s"${prefix}_$N"
    def eval(): SUnit
  }

  case class MetaProgGen(name: JString, Ns: Seq[scala.Int], benchmark: scala.Int => Benchmark) {
    def expand: List[NamedSpatialProg] = {
      //println("Expanding " + name + " into " + Ns.length + " benchmarks")
      Ns.toList.map{n => benchmark(n) }.map{x => (name + "_" + x.name, () => x.eval()) }
    }
  }

  var gens: List[MetaProgGen] = Nil
}


object Benchmarks {
  private def fromLines(first: String, lines: Iterator[String])(implicit config: AreaConfig[Double]): (Area, String) = {
    val splitFirstLine = first.split(",") // Ignore BRAM
    val fullName = splitFirstLine.head
    val splitFullName = fullName.split("_")
    val name = splitFullName.head
    val params = splitFullName.drop(1)
    var entries = Map[String,Double]()

    var line = first
    do {
      val parts = line.split(",")
      val key = parts(1)
      val entry = parts(2).toDouble
      entries += key -> entry
      if (lines.hasNext) line = lines.next() else line = null
    } while (line != null && line.startsWith(fullName))

    println(s"Found benchmark " + fullName)
    val area = new AreaMap[Double](name, params, entries)
    (area, line)
  }

  def fromFile(file: String)(implicit config: AreaConfig[Double]): Seq[Area] = {
    val lines = Source.fromFile(file).getLines()
    var first = if (lines.hasNext) lines.next() else null
    val areas = ArrayBuffer[Area]()
    while (first != null) {
      val (area, last) = fromLines(first, lines)
      areas += area
      first = last
    }
    areas
  }

  def fromSaved(file: String)(implicit config: AreaConfig[Double]): (Seq[Area], Seq[Area]) = {
    val lines = Source.fromFile(file).getLines()
    val header = lines.next().split(",").map(_.trim)
    val indices = header.zipWithIndex.filter{case (head,i) => config.fields.contains(head) }.map(_._2)
    val fields = indices.map{i => header(i) }
    val nParams = header.lastIndexWhere(_.startsWith("Param")) + 1

    val areas = lines.map{line =>
      val elems = line.split(",").map(_.trim)
      val name = elems.head
      val params = elems.slice(1,nParams).filterNot(_ == "")
      val entries = indices.map{i => Try(elems(i).toDouble).getOrElse(0.0) }
      new AreaMap(name,params,fields.zip(entries).toMap)
    }
    val (base,bench) = areas.partition{b => b.name == "Static" || b.name == "Unary" }
    (base.toSeq, bench.toSeq)
  }

}