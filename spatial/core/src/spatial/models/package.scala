package spatial

package object models {
  type Area = AreaMap[Double]
  type NodeModel = Either[LinearModel,Double]
  type Model = AreaMap[NodeModel]

  implicit class NodeModelOps(x: NodeModel) {
    def eval(args: (String,Double)*): Double = x match {
      case Left(model) => Math.ceil(model.eval(args:_*))
      case Right(num) => Math.ceil(num)
    }
  }
  implicit class ModelOps(x: Model) {
    def eval(args: (String,Double)*): Area = x.map{model => model.eval(args:_*) }
  }
}
