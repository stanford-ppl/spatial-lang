package spatial.models.characterization

import argon.core.State
import spatial._
import spatial.dsl._

trait Benchmarks {
  self: SpatialCompiler =>

  type JString = java.lang.String
  type SpatialProg = () => Unit
  type NamedSpatialProg = (JString, SpatialProg)


  trait Benchmark {
    def prefix: JString
    def N: scala.Int
    def name: JString = s"${prefix}_$N"
    def eval(): Unit
  }

  case class MetaProgGen(name: JString, Ns: Seq[scala.Int], benchmark: scala.Int => Benchmark) {
    def expand: List[NamedSpatialProg] = Ns.toList.map{n => benchmark(n) }
      .map{x => (name + "_" + x.name, () => x.eval()) }
  }

  var gens: List[MetaProgGen] = Nil

}
