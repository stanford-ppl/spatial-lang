package spatial.models.characterization

trait Benchmarks {
  type JString = java.lang.String
  type SpatialProg = () => Unit
  type NamedSpatialProg = (JString, SpatialProg)


  trait Benchmark {
    def prefix: JString
    def N: Int
    def name: JString = s"${prefix}_$N"
    def eval(): Unit
  }

  case class MetaProgGen(name: JString, Ns: Seq[Int], benchmark: Int => Benchmark) {
    def expand: List[NamedSpatialProg] = Ns.toList.map{n => benchmark(n) }
      .map{x => (name + "_" + x.name, () => x.eval()) }
  }

  var gens: List[MetaProgGen] = Nil

}
