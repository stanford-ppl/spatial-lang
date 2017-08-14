package spatial.models
package characterization

import java.io.PrintWriter
import scala.util._
import argon.util.Report._

object Modeling {
  import LabeledPairs._
  import Regression._

  lazy val SPATIAL_HOME: String = sys.env.getOrElse("SPATIAL_HOME", {
    error("SPATIAL_HOME was not set!")
    error("Set top directory of spatial using: ")
    error("export SPATIAL_HOME=/path/to/spatial")
    sys.exit()
  })

  val target = spatial.targets.Zynq
  val FIELDS = target.FIELDS
  implicit def AREA_CONFIG: AreaConfig[Double] = target.AREA_CONFIG
  implicit def LINEAR_CONFIG: AreaConfig[LinearModel] = target.LINEAR_CONFIG
  type Area = AreaMap[Double]
  type Model = AreaMap[LinearModel]
  def header(nParams: Int): String = "Benchmark," + List.tabulate(nParams){i => s"Param #$i"}.mkString(",") + "," + FIELDS.mkString(",")

  val baselines  = s"$SPATIAL_HOME/results/baselines.csv"
  val benchmarks = s"$SPATIAL_HOME/results/characterization.csv"
  val benchmarks2 = s"$SPATIAL_HOME/results/characterization3.csv"
  val saved = s"$SPATIAL_HOME/results/benchmarks.csv"
  val MODELS_FILE = s"$SPATIAL_HOME/spatial/core/resources/models/${target.name}_raw.csv"

  def writeFile(file: String, areass: Seq[Seq[_<:AreaMap[_]]]): Unit = {
    val output = new PrintWriter(file)

    val maxParams = areass.map(areas => (0 +: areas.map(_.params.length)).max).max
    //output.println(XilinxArea.superHeader(maxParams).mkString(", "))
    output.println(header(maxParams))

    areass.foreach{areas =>
      areas.sortBy(_.fullName).foreach{area =>
        output.println(area.toPrintableString(maxParams))
      }
    }
    output.close()
  }

  def keyRemap(x: String): String = x match {
    case "RAMB18E1" => "RAM18"
    case "RAMB36E1" => "RAM36"
    case "F7 Muxes" => "MUX7"
    case "F8 Muxes" => "MUX8"
    case "Register as Flip Flop" => "Regs"
    case _ => x
  }

  def main(args: Array[String]): Unit = {
    /*val (bases, areas) = try {
      println("Attempting to load saved...")
      val (bases, areas) = Benchmarks.fromSaved(saved)
      (bases, areas)
    }
    catch {case _:Throwable =>*/
      //println("Failed. Creating from file instead")
      val files = Seq(baselines, benchmarks, benchmarks2)

      val benchs = files.flatMap{file => Benchmarks.fromFile(file) }
                        .map{area => area.renameEntries(keyRemap) }

      val allBenchs = benchs.groupBy(p => (p.name, p.params.map(_.trim).filterNot(_ == "").toSet)).mapValues(_.last).values.toSeq

      val (bases, areas) = allBenchs.partition{p => p.name.startsWith("Unary") || p.name.startsWith("Static") }

      writeFile(saved, Seq(bases, areas))

      //(bases, areas)
    //}

    val (fringe, fringe2, argIn, argOut) = {
      val b = (0,"b")
      val n = (1,"n")

      val (fringeModel, argOutModel) = createLm("Static", Nil, bases, n | b*n | n + b*n) // | n + n*n | b*n + b*n*n) //((0,"b")*(1,"n") + (1,"n")) | ((0,"b")*(1,"n") + (1,"n") + (1,"n")*(1,"n")) )
      val fringe: Area = fringeModel.copy(name = "Fringe")
      val argOut = argOutModel.copy(name = "ArgOut")

      val (fringe2, argsModel) = createLm("Unary", Nil, bases, n | b*n | n + b*n, baseline=Some(_ => fringe)) //  n + b | n + n*n | b*n + b*n*n, baseline = Some(_ => fringe)) //((0,"b")*(1,"n") + (1,"n")) | ((0,"b")*(1,"n") + (1,"n") + (1,"n")*(1,"n")), baseline = Some(_ => fringe))


      val argIn  = (argsModel - argOut).copy(name = "ArgIn")
      //val argOut = (argsModel / 2).copy(name = "ArgOut")

      (fringe, fringe2, argIn, argOut)
    }

    def model(name: String, params: Seq[(Int,String)], labels: Seq[PatternList]): Model = {
      createLm(name, params, areas, labels, None)._2
    }

    def removeArgs(area: Model, b: Int, in: Int, out: Int, dbg: Boolean = false) = {
      val args = argIn.partial("b" -> b)*in + argOut.partial("b" -> b)*out

      if (dbg) println(args)

      area <-> args
    }

    def makeModel(name: String, params: Seq[(Int,String)], labels: Seq[PatternList], b: Int, in: Int, out: Int, dbg: Boolean = false) = {
      val model1 = model(name, params, labels)

      if (dbg) println(model1)

      removeArgs(model1, b, in, out, dbg)
    }

    // TODO: Would be nice to automate, but need to associate with # of inputs
    val IntOps = Array("Inv"->1, "Neg"->1, "Abs"->1, "Min"->2, "Add"->2, "Sub"->2, "Div"->2, "Mod"->2, "Or"->2,
      "And"->2, "XOr"->2, "Lt"->2, "Leq"->2, "Neq"->2, "Eql"->2)

    val FloatOps = Array("Neg"->1, "Abs"->1, "Min"->2, "Add"->2, "Sub"->2, "Div"->2, "Mod"->2,
      "Lt"->2, "Leq"->2, "Neq"->2, "Eql"->2)

    val BitOps = Array("Not"->1, "And"->2, "Or"->2, "XOr"->2, "Eql"->2)

    val intModels = IntOps.map{case (op,in) =>
      val b = (0,"b")
      val n = (2,"n")
      val int8  = makeModel("Int", Seq(0->"8", 1->op), n, 8, in, 1).eval("n" -> 1).copy(name="Int"+op, params=Array("8"))
      val int16 = makeModel("Int", Seq(0->"16", 1->op), n, 16, in, 1).eval("n" -> 1).copy(name="Int"+op, params=Array("16"))
      val int32 = makeModel("Int", Seq(0->"32", 1->op), n, 32, in, 1).eval("n" -> 1).copy(name="Int"+op, params=Array("32"))
      // TODO: Put this back when fixed
      //val int64 = makeModel("Int", Seq(0->"64", 1->op), n, 64, in, 1).eval("n" -> 1).copy(name="Int"+op, params=Array("64"))

      val model = createLm("Int"+op,Nil,Seq(int8,int16,int32), b | b*b, addIntercept = false)._2

      model.copy(name="Fix"+op).cleanup
    }

    // Special case multipliers because of DSPs
    val intMuls = {
      val n = (2,"n")
      val intMul8 = makeModel("Int", Seq(0->"8", 1->"Mul"), n, 8, 2, 1).eval("n" -> 1).copy(name="IntMul8").cleanup
      val intMul16 = makeModel("Int", Seq(0->"16", 1->"Mul"), n, 16, 2, 1).eval("n" -> 1).copy(name="IntMul16").cleanup
      val intMul32 = makeModel("Int", Seq(0->"32", 1->"Mul"), n, 32, 2, 1).eval("n" -> 1).copy(name="IntMul32").cleanup
      // TODO: Add 64 bit back
      //val intMul32 = makeModel("Int", Seq(0->"64", 1->"Mul"), n, 64, 2, 1).eval("n" -> 1).copy(name="IntMul64").cleanup

      Array(intMul8, intMul16, intMul32)
    }

    def simpleModel(opPos: Int, nPos: Int, bits: Int, in: Int, out: Int)(name: String, op: String, eqs: Seq[PatternList]): Option[Area] = {
      val benchs = areas.getAll(name, opPos->op)
      try {
        if (benchs.length > 1) {
          Some(makeModel(name, Seq(opPos -> op), eqs, bits, in, out).eval("n" -> 1).copy(name = name + op, params = Seq.empty).cleanup)
        }
        else if (benchs.length == 1) {
          println(s"Only one benchmark exists for $name - $op")
          val bench = benchs.head
          val n = bench.params(nPos).toInt
          val args = argIn.eval("b" -> bits, "n" -> n) * in + argOut.eval("b" -> bits, "n" -> n)
          Some(((bench - args) / n).copy(name = name + op, params = Seq.empty).cleanup)
        }
        else {
          println(s"Not enough information to make model for $name-$op")
          None
        }
      }
      catch {case _: Throwable =>
        println(s"Not enough information to make model for $name-$op")
        None
      }
    }

    val floatModels = FloatOps.flatMap { case (op, in) =>
      val n = (1,"n")
      simpleModel(opPos = 0, nPos = 1, bits = 32, in = in, out = 1)("Float", op, n)
    }

    val bitModels = BitOps.flatMap{case (op,in) =>
      val n = (1,"n")
      simpleModel(opPos = 0, nPos = 1, bits = 1, in=in, out=1)("Bit", op, n)
    }

    val muxModel = {
      val n = (1,"n")
      def makeMuxModel(bits: Int) = {
        val mod = model("Mux", Seq(0->bits.toString), n)
        val rem = removeArgs(removeArgs(mod, bits, 2, 1), 1, 1, 0)
        rem.eval("n" -> 1).copy(name = "Mux", params=Array(bits.toString))
      }

      val mux8 = makeMuxModel(8)
      val mux16 = makeMuxModel(16)
      val mux32 = makeMuxModel(32)
      val mux64 = makeMuxModel(64)

      val b = (0,"b")
      val (i,m) = createLm("Mux",Nil,Seq(mux8,mux16,mux32,mux64), b | b*b)

      val mc = m.zip(i){(a,b) => a + b}.copy(name="Mux").cleanup

      Array(mux8, mux16, mux32, mux64, mc)
    }

    val fracModels = Array("Ceil", "Floor").flatMap{op =>
      val n = (3,"n")
      val q8 = makeModel("Q", Seq(2->op), n, 16, 1, 1).eval("n"->1).copy(name="Q"+op, params=Array("8","8"))
      val q16 = makeModel("Q", Seq(2->op), n, 32, 1, 1).eval("n"->1).copy(name="Q"+op, params=Array("16","16"))
      val q32 = makeModel("Q", Seq(2->op), n, 64, 1, 1).eval("n"->1).copy(name="Q"+op, params=Array("32","32"))
      val i = (0,"i")
      val f = (1,"f")
      val (inter,m) = createLm("Q"+op,Nil,Seq(q8,q16,q32), i + f)

      val mc = m.zip(inter){(x,y) => x + y}.copy(name="Fix"+op).cleanup

      Array(q8, q16, q32, mc)
    }

    val regModel = {
      val b = (0,"b")
      val d = (1,"d")
      val n = (2,"n")

      def regModels(bits: Int): Model = {
        val models = List.tabulate(7){d =>
          val depth = d + 1
          makeModel("Reg", Seq(0->bits.toString,1->depth.toString), n, bits, 1, 1).eval("n"->1).copy(name="Reg", params=Array(depth.toString))
        }
        val d = (0,"d")
        val (inter,m) = createLm("Reg", Nil, models, d)
        m.copy(name = "Reg", params=Array(bits.toString))
      }

      val reg8  = regModels(8)  //makeModel("Reg", Seq(0->"8",1->"2"), n, 8, 1, 1).eval("n"->1).copy(name="Reg", params=Array("8"))
      val reg16 = regModels(16) //makeModel("Reg", Seq(0->"16",1->"2"), n, 16, 1, 1).eval("n"->1).copy(name="Reg", params=Array("16"))
      val reg32 = regModels(32) //makeModel("Reg", Seq(0->"32",1->"2"), n, 32, 1, 1).eval("n"->1).copy(name="Reg", params=Array("32"))
      val reg64 = regModels(64) //makeModel("Reg", Seq(0->"64",1->"2"), n, 64, 1, 1).eval("n"->1).copy(name="Reg", params=Array("64"))

      Array(reg8.copy(name="Reg8"), reg16.copy(name="Reg16"), reg32.copy(name="Reg32"), reg64.copy(name="Reg64"))
    }

    val fifoModel = {
      val b = (0,"b")
      val d = (1,"d")
      val p = (2,"p")
      val n = (3,"n")

      val fifo8 = makeModel("FIFO", Seq(0->"8",2->"1",3->"50"), d, 8, 0, 1).copy(name="FIFO", params=Array("8","d"))
      val fifo16 = makeModel("FIFO", Seq(0->"16",2->"1",3->"50"), d, 16, 0, 1).copy(name="FIFO", params=Array("16","d"))
      val fifo32 = makeModel("FIFO", Seq(0->"32",2->"1",3->"50"), d, 32, 0, 1).copy(name="FIFO", params=Array("32","d"))
      //val fifo64 = makeModel("FIFO", Seq(0->"64",2->"1",3->"50"), d, 16, 0, 1).copy(name="FIFO", params=Array("64","d"))

      val fifo8d = fifo8.eval("d" -> 1).copy(name="FIFO", params=Array("8"))
      val fifo16d = fifo16.eval("d" -> 1).copy(name="FIFO", params=Array("16"))
      val fifo32d = fifo32.eval("d" -> 1).copy(name="FIFO", params=Array("32"))

      val fifoModel = createLm("FIFO", Nil, Seq(fifo8d, fifo16d, fifo32d), b)._2.copy(name="FIFO", params=Array("b","d")) * "d"
      Array(fifo8.copy(name="FIFO8"), fifo16.copy(name="FIFO16"), fifo32.copy(name="FIFO32"), fifoModel.fractional)
    }

    val regFileModel = {
      def depths1D(name: String): Seq[Area] = (1 to 3).flatMap{d =>
        val c = (1,"c")

        Try(makeModel(name, Seq(0->d.toString, 2->"1"), c, 32, 0, 1).eval("c" -> 1).copy(name = name, params=Array(d.toString, "c"))) match {
          case Success(model) => Some(model)
          case Failure(except) =>
            println(s"Not enough information to make model $name, d = $d")
            None
        }
      }

      def depths2D(name: String): Seq[Area] = (1 to 3).flatMap{d =>
        val r = (1,"r")
        val c = (2,"c")

        Try(makeModel(name, Seq(0->d.toString, 3->"1",4->"1"), r*c, 32, 0, 1).eval("r" -> 1, "c" -> 1).copy(name = name, params=Array(d.toString+"XXX", "r", "c"))) match {
          case Success(model) => Some(model)
          case Failure(except) =>
            println(s"Not enough information to make model $name, d = $d")
            None
        }
      }

      val rf1 = depths1D("RegFile1D")
      val rf2 = depths2D("RegFile2D")
      (rf1 ++ rf2).toArray
    }

    val transferModels = {
      val d = (0,"d") // Number of dimensions
      val w = (1,"w")
      val p = (2,"p")
      val n = (3,"n")

      val pars = Seq(1, 4, 8, 16)

      def modelTx(name: String, params: Seq[(Int,String)], eq: Seq[PatternList], vv: Boolean = false): (Model, Area) = {
        val parModels = pars.flatMap { par =>
          val benchs = areas.getAll(name, params:_*).getAll(name, 2 -> par.toString)
          println(s"CREATING MODEL FOR $name, $params, par = $par")
          //benchs.foreach{bench => println(bench.name + ", " + bench.params.mkString(", ") + ": " + bench) }

          val varyingNs = benchs.map(_.params.apply(3)).distinct.length

          //benchs.foreach{bench => println(bench.params.last + ", " + bench("LUT5")) }

          if (benchs.length > 1 && varyingNs > 1) {
            //makeModel(name, params, eq, 32, 1, 0).partial("n" -> 1)
            val (offset, model) = createLm(name, params, benchs, n, baseline = None, useMaxOnly = true, baselineIfNegative = Some(fringe))

            val offsetParModel = offset.copy(name = name, params = Seq(par.toString))
            val parModel = model.copy(name = name, params = Seq(par.toString))

            if (vv) {
              println(par + ": ")
              println("SLOPE:  " + model)
              println("OFFSET: " + offset)
            }

            Some((parModel, offsetParModel))
          }
          else None
        }

        val models  = parModels.map(_._1).map(_.eval("n"->1))
        val offsets = parModels.map(_._2)

        /*models.zip(offsets).foreach{case (model,offset) =>
          println("MODEL " + model.name + " " + model.params.mkString(", ") + ":")
          println("  " + model.cleanup)
          println("OFFSET " + offset.name + " " + offset.params.mkString(", ") + ":")
          println("  " + offset.cleanup)
        }*/

        val p = (0,"p")
        val (offset,offsetModel) = createLm(name, Nil, offsets, p, baselineIfNegative = Some(fringe))
        val (modelOffset, model) = createLm(name, Nil, models, p | p*p)

        if (vv) {
          println("OFFSET: " + offset)
          println("OFFSET MODEL: " + offsetModel)
          println("MODEL OFFSET: " + modelOffset)
          println("MODEL: " + model)
        }

        (modelOffset + model, offset - fringe)

        /*else if (benchs.length == 1) {
          println("Only one benchmark exist for " + name)
          val bench = benchs.head
          val n = bench.params(3).toInt
          val args = argIn.eval("b" -> 32, "n" -> n) + fringe
          val area = (bench - args) / n
          val model = area.map{x => LinearModel(Seq(Prod(x,Nil)),Set.empty) }
          (model, fringe)
        }
        else {
          throw new Exception(s"Not enough information to make model for $name $params")
        }*/
      }

      val (alignLd1,fringeAL1) = modelTx("AlignedLoad", Seq(0->"1"), n | n + n*p)
      val (alignLd2,fringeAL2) = modelTx("AlignedLoad", Seq(0->"2"), n | n + n*p)

      val (unalignLd1,fringeUL1) = modelTx("UnalignedLoad", Seq(0->"1"), n | n + n*p)
      val (unalignLd2,fringeUL2) = modelTx("UnalignedLoad", Seq(0->"2"), n | n + n*p, vv = true)


      val (alignSt1,fringeAS1) = modelTx("AlignedStore", Seq(0->"1"), n | n + n*p)
      val (alignSt2,fringeAS2) = modelTx("AlignedStore", Seq(0->"2"), n | n + n*p)

      val (unalignSt1,fringeUS1) = modelTx("UnalignedStore", Seq(0->"1"), n | n + n*p)
      val (unalignSt2,fringeUS2) = modelTx("UnalignedStore", Seq(0->"2"), n | n + n*p)


      Seq(
        alignLd1.copy(name = "AlignedLoad1", params=Seq.empty),
        alignLd2.copy(name = "AlignedLoad2", params=Seq.empty),
        unalignLd1.copy(name = "UnalignedLoad1", params=Seq.empty),
        unalignLd2.copy(name = "UnalignedLoad2", params=Seq.empty),
        alignSt1.copy(name = "AlignedStore1", params=Seq.empty),
        alignSt2.copy(name = "AlignedStore2", params=Seq.empty),
        unalignSt1.copy(name = "UnalignedStore1", params=Seq.empty),
        unalignSt2.copy(name = "UnalignedStore2", params=Seq.empty),
        fringeAL1.copy(name = "FringeAL1"),
        fringeAL2.copy(name = "FringeAL2"),
        fringeUL1.copy(name = "FringeUL1"),
        fringeUL2.copy(name = "FringeUL2"),
        fringeAS1.copy(name = "FringeAS1"),
        fringeAS2.copy(name = "FringeAS2"),
        fringeUS1.copy(name = "FringeUS1"),
        fringeUS2.copy(name = "FringeUS2")
      )
    }

    val argInModel = argIn.partial("n" -> 1).cleanup.copy(name="ArgIn", params=Array("b"))
    val argOutModel = argOut.partial("n" -> 1).cleanup.copy(name="ArgOut", params=Array("b"))
    val argModels = Array(argInModel, argOutModel)

    writeFile(MODELS_FILE, Seq(
      Array(fringe),
      argModels,
      intModels,
      intMuls,
      floatModels,
      bitModels,
      muxModel,
      fracModels,
      regModel,
      fifoModel,
      regFileModel,
      transferModels
    ))

    //println(modelX("Int", Seq(1->"Add"), (0,"b")*(2,"n") + (2,"n") + (2,"n")*(2,"n") + (0,"b")*(2,"n")*(2,"n") ))

    //println( (areas.getFirst("Int32", 0->"Add",1->"200") - bases.getFirst("Unary", 0->"32", 1->"200")) / 200)
  }
}
