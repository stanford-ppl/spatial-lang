package spatial.models.characterization

import argon.core.State
import spatial._

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
      println("Expanding " + name + " into " + Ns.length + " benchmarks")

      Ns.toList.map{n => benchmark(n) }.map{x => (name + "_" + x.name, () => x.eval()) }
    }
  }

  var gens: List[MetaProgGen] = Nil

  var bases: List[MetaProgGen] = Nil
}
