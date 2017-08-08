package spatial.models.characterization

object LabeledPairs {
  abstract class PatternList {
    def toSeq: Seq[Pattern]
    def |(that: PatternList) = Seq(this, that)
  }
  case class ListProduct(ps: Seq[Pattern], last: Product) extends PatternList {
    def *(y: (Int,String)) = ListProduct(ps, last * y)
    def +(y: (Int,String)) = ListLinear(ps :+ last, Linear(y))
    def *(y: Product) = ListProduct(ps, last * y)
    def +(y: Product) = ListProduct(ps :+ last, y)
    def +(y: Linear)  = ListLinear(ps :+ last, y)
    def toSeq = ps :+ last
  }
  case class ListLinear(ps: Seq[Pattern], last: Linear) extends PatternList {
    def *(y: (Int,String)) = ListProduct(ps, last*y)
    def +(y: (Int,String)) = ListLinear(ps :+ last, Linear(y))
    def +(y: Product) = ListProduct(ps :+ last, y)
    def +(y: Linear) = ListLinear(ps :+ last, y)
    def toSeq = ps :+ last
  }
  implicit def PatternListToSeq(x: PatternList): Seq[PatternList] = Seq(x)
  implicit def LabeledPairToSeq(x: (Int,String)): Seq[PatternList] = Seq(ListLinear(Nil,Linear(x)))
  implicit def LinearToSeq(x: Linear): Seq[PatternList] = Seq(ListLinear(Nil,x))
  implicit def ProductToSeq(x: Product): Seq[PatternList] = Seq(ListProduct(Nil,x))

  abstract class Pattern { def label: String }
  case class Product(xs: (Int,String)*) extends Pattern {
    def *(y: (Int,String)) = Product((y +: xs):_*)
    def +(y: (Int,String)) = ListLinear(Seq(this), Linear(y))
    def *(y: Product) = Product((this.xs ++ y.xs):_*)
    def +(y: Product) = ListProduct(Seq(this), y)
    def +(y: ListProduct) = Seq(ListProduct(Nil,this), y)
    def |(that: (Int,String)) = Seq(ListProduct(Nil,this), ListLinear(Nil,Linear(that)))
    def |(that: Product) = Seq(ListProduct(Nil,this), ListProduct(Nil,that))
    def |(that: Linear) = Seq(ListProduct(Nil,this), ListLinear(Nil,that))
    def |(that: ListProduct) = Seq(ListProduct(Nil,this), that)
    def |(that: ListLinear) = Seq(ListProduct(Nil,this), that)
    def label = xs.map(_._2).mkString("*")
    def ins = xs.map(_._1)
  }
  case class Linear(x: (Int,String)) extends Pattern {
    def *(y: (Int,String)) = Product(x,y)
    def +(y: (Int,String)) = ListLinear(Seq(this), Linear(y))
    def +(y: Product) = ListProduct(Seq(this), y)
    def +(y: ListProduct) = Seq(ListLinear(Nil,this), y)
    def |(that: (Int,String)) = Seq(ListLinear(Nil,this), ListLinear(Nil,Linear(that)))
    def |(that: Product) = Seq(ListLinear(Nil,this), ListProduct(Nil,that))
    def |(that: Linear) = Seq(ListLinear(Nil,this), ListLinear(Nil,that))
    def |(that: ListProduct) = Seq(ListLinear(Nil,this), that)
    def |(that: ListLinear) = Seq(ListLinear(Nil,this), that)
    def label = x._2
  }

  implicit class LabeledPairOps(x: (Int,String)) {
    def *(y: (Int,String)) = Product(x, y)
    def +(y: (Int,String)) = ListLinear(Seq(Linear(x)), Linear(y))
    def +(y: Product) = ListProduct(Seq(Linear(x)),y)
    def +(y: Linear) = ListLinear(Seq(Linear(x)),y)
    def +(y: ListProduct) = ListProduct(Linear(x) +: y.ps, y.last)
    def +(y: ListLinear)  = ListLinear(Linear(x) +: y.ps, y.last)

    def |(that: (Int,String)) = Seq(ListLinear(Nil,Linear(x)), ListLinear(Nil,Linear(that)))
    def |(that: Product) = Seq(ListLinear(Nil,Linear(x)), ListProduct(Nil,that))
    def |(that: Linear) = Seq(ListLinear(Nil,Linear(x)), ListLinear(Nil,that))
    def |(that: ListProduct) = Seq(ListLinear(Nil,Linear(x)), that)
    def |(that: ListLinear) = Seq(ListLinear(Nil,Linear(x)), that)
  }

  implicit class PatternListSeqOps(x: Seq[PatternList]) {
    def |(that: (Int,String)) = x ++ ListLinear(Nil,Linear(that))
    def |(that: Product) = x ++ ListProduct(Nil,that)
    def |(that: Linear) = x ++ ListLinear(Nil,that)
    def |(that: ListProduct) = x ++ that
    def |(that: ListLinear) = x ++ that
  }
}
