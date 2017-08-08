package spatial.models
package characterization

object Regression {
  import LabeledPairs._
  type Area = AreaMap[Double]
  type Model = AreaMap[LinearModel]

  def createLm(
    name:         String,
    params:       Seq[(Int,String)],
    benchs:       Seq[Area],
    proposals:    Seq[PatternList],
    baseline:     Option[Int => Area] = None,
    addIntercept: Boolean = true
  )(implicit dbl: AreaConfig[Double], lin: AreaConfig[LinearModel]) = {
    val addInt = addIntercept && baseline.isEmpty

    val benchmarks = benchs.getAll(name, params:_*)

    val dataArrays = benchmarks.map{bench: Area =>
      (baseline, bench.n) match {
        case (Some(base), Some(n: Int)) => bench - (base(n) : AreaMap[Double])
        case _ => bench
      }
    }.map(_.toSeq)

    if (dataArrays.length < 2) {
      throw new Exception(s"Not enough data to make model for $name: " + params.mkString(", "))
    }

    val results = Array.tabulate(dataArrays.head.length){d =>
      val proposalResults = proposals.map{list =>
        val inputs = list.toSeq

        import scalaglm.Lm
        import breeze.linalg._

        val y = DenseVector(dataArrays.map(_.apply(d)):_*)

        val label = inputs.map(_.label)

        val x = DenseMatrix(
          benchmarks.map{bench => inputs.map {
            case p: Product => p.ins.map { i => bench.params(i).toDouble }.product
            case Linear((i, _)) => bench.params(i).toDouble
          }}:_*
        )

        val lm = Lm(y, x, label, addIntercept = addInt)

        val intercept = if (addInt) lm.coefficients.apply(0) else 0.0
        val coeffs = if (addInt) lm.coefficients.toArray.drop(1) else lm.coefficients.toArray

        /*if (d == 29) {
          println(x)
          println(y)
          lm.summary
          println(coeffs.mkString(", "))
          println(label.mkString(", "))
        }*/

        val r2 = lm.rSquared
        if (r2 > 0.5) { // HACK: Magic number
          val model = LinearModel(coeffs.zip(label))
          (intercept, model, r2)
        }
        else {
          val model = LinearModel(label.map{l => (0.0,l)})
          val intercept = y.toArray.sum / y.length
          (intercept, model, 0.0)
        }
      }

      var results = proposalResults.head
      var bestR2: Double = proposalResults.head._3
      proposalResults.foreach{prop =>
        if (prop._3 >= bestR2 + 0.1) { results = prop; bestR2 = prop._3 }
      }

      (results._1, results._2)
    }

    val pars = results.map(_._2.vars).toSet.flatten.toArray

    val intercept: Area = AreaMap.fromArray(name + "_" + params.map(_._2).mkString("_") + "_Intercept", Seq.empty[String], results.map(_._1))
    val model: Model = AreaMap.fromArray(name + "_" + params.map(_._2).mkString("_") + "_Model", pars, results.map(_._2))

    (intercept, model)
  }

}

