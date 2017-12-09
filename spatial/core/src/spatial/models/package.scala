package spatial

package object models {
  type Area = AreaMap[Double]
  type Latency = LatencyMap[Double]
  type NodeModel = Either[LinearModel,Double]
  type Model = AreaMap[NodeModel]
  type LModel = LatencyMap[NodeModel]

  implicit class NodeModelOps(x: NodeModel) {
    def eval(args: (String,Double)*): Double = x match {
      case Left(model) => Math.max(0.0, Math.ceil(model.eval(args:_*)))
      case Right(num) => Math.max(0.0, Math.ceil(num))
    }
    def exactEval(args: (String,Double)*): Double = x match {
      case Left(model) => Math.max(0.0, model.eval(args:_*))
      case Right(num) => Math.max(0.0, num)      
    }
  }
  implicit class ModelOps(x: Model) {
    implicit val dbl: AreaConfig[Double] = x.config.copy(default = 0.0)
    def eval(args: (String,Double)*): Area = x.map{model => model.eval(args:_*) }
  }

  implicit class LModelOps(x: LModel) {
    implicit val dbl: LatencyConfig[Double] = x.config.copy(default = 0.0)
    def eval(args: (String,Double)*): Latency = x.map{model => model.exactEval(args:_*) }
  }

  implicit class SeqAreaOps[T](areas: Seq[AreaMap[T]]) {
    def getFirst(name: String, params: (Int,String)*): AreaMap[T] = {
      areas.find{a: AreaMap[T] => a.name == name && params.forall{case (i,p) => a.params(i) == p} }.get
    }
    def getAll(name: String, params: (Int,String)*): Seq[AreaMap[T]] = {
      areas.filter{a: AreaMap[T] => a.name == name && params.forall{case (i,p) => a.params(i) == p} }
    }
  }

  implicit class DoubleAreaOps(a: AreaMap[Double]) {
    implicit val lin: AreaConfig[LinearModel] = a.config.copy(default = LinearModel(Nil,Set.empty))
    implicit val dbl: AreaConfig[Double] = a.config
    def cleanup: AreaMap[Double] = a.map{x => Math.max(0.0, Math.round(x)) }
    def fractional: AreaMap[Double] = a.map{x => Math.max(0.0, x) }

    def +(b: AreaMap[LinearModel]): AreaMap[LinearModel] = a.zip(b){(y,x) => x + LinearModel(Seq(Prod(y,Nil)),Set.empty) }
  }

  implicit class LinearModelAreaOps(a: AreaMap[LinearModel]) {
    implicit val lin: AreaConfig[LinearModel] = a.config
    implicit val dbl: AreaConfig[Double] = a.config.copy(default = 0.0)

    def *(b: String): AreaMap[LinearModel] = a.map(_*b)
    def *(b: Double): AreaMap[LinearModel] = a.map(_*b)
    def /(b: Double): AreaMap[LinearModel] = a.map(_/b)
    def +(b: Double): AreaMap[LinearModel] = a.map(_+b)
    def -(b: Double): AreaMap[LinearModel] = a.map(_-b)

    def ++(b: AreaMap[LinearModel]): AreaMap[LinearModel] = a.zip(b){(x,y) => x + y }
    def --(b: AreaMap[LinearModel]): AreaMap[LinearModel] = a.zip(b){(x,y) => x - y }
    def <->(b: AreaMap[LinearModel]): AreaMap[LinearModel] = a.zip(b){(x,y) => x <-> y }


    def apply(xs: (String,Double)*): AreaMap[Double] = a.map(_.eval(xs:_*))
    def eval(xs: (String,Double)*): AreaMap[Double] = a.map(_.eval(xs:_*))
    def partial(xs: (String,Double)*): AreaMap[LinearModel] = a.map(_.partial(xs:_*))

    def cleanup: AreaMap[LinearModel] = a.map(_.cleanup)
    def fractional: AreaMap[LinearModel] = a.map(_.fractional)
  }
}
