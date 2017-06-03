package spatial.lang

import spatial.SpatialExp
import forge._
import spatial.nodes.EnabledOp

trait LUTsApi { this: SpatialExp =>
  private def checkDims(dims: Seq[Int], elems: Seq[_])(implicit ctx: SrcCtx) = {
    if (dims.product != elems.length) {
      error(ctx, c"Specified dimensions of the LUT do not match the number of supplied elements (${dims.product} != ${elems.length})")
      error(ctx)
    }
  }

  @api def LUT[T:Meta:Bits](dim: Int)(elems: T*): LUT1[T] = {
    checkDims(Seq(dim),elems)
    LUT1(lut_new[T,LUT1](Seq(dim), unwrap(elems)))
  }
  @api def LUT[T:Meta:Bits](dim1: Int, dim2: Int)(elems: T*): LUT2[T] = {
    checkDims(Seq(dim1,dim2),elems)
    LUT2(lut_new[T,LUT2](Seq(dim1,dim2), unwrap(elems)))
  }
  @api def LUT[T:Meta:Bits](dim1: Int, dim2: Int, dim3: Int)(elems: T*): LUT3[T] = {
    checkDims(Seq(dim1,dim2,dim3),elems)
    LUT3(lut_new[T,LUT3](Seq(dim1,dim2,dim3), unwrap(elems)))
  }
  @api def LUT[T:Meta:Bits](dim1: Int, dim2: Int, dim3: Int, dim4: Int)(elems: T*): LUT4[T] = {
    checkDims(Seq(dim1,dim2,dim3,dim4),elems)
    LUT4(lut_new[T,LUT4](Seq(dim1,dim2,dim3,dim4), unwrap(elems)))
  }
  @api def LUT[T:Meta:Bits](dim1: Int, dim2: Int, dim3: Int, dim4: Int, dim5: Int)(elems: T*): LUT5[T] = {
    checkDims(Seq(dim1,dim2,dim3,dim4,dim5),elems)
    LUT5(lut_new[T,LUT5](Seq(dim1,dim2,dim3,dim4,dim5), unwrap(elems)))
  }
}

trait LUTsExp { this: SpatialExp =>

  trait LUT[T] { this: Template[_] =>
    def s: Exp[LUT[T]]
  }

  case class LUT1[T:Meta:Bits](s: Exp[LUT1[T]]) extends Template[LUT1[T]] with LUT[T] {
    @api def apply(i: Index): T = wrap(lut_load(s, Seq(i.s), bool(true)))
  }
  case class LUT2[T:Meta:Bits](s: Exp[LUT2[T]]) extends Template[LUT2[T]] with LUT[T] {
    @api def apply(r: Index, c: Index): T = wrap(lut_load(s, Seq(r.s, c.s), bool(true)))
  }
  case class LUT3[T:Meta:Bits](s: Exp[LUT3[T]]) extends Template[LUT3[T]] with LUT[T] {
    @api def apply(r: Index, c: Index, p: Index): T = wrap(lut_load(s, Seq(r.s, c.s, p.s), bool(true)))
  }
  case class LUT4[T:Meta:Bits](s: Exp[LUT4[T]]) extends Template[LUT4[T]] with LUT[T] {
    @api def apply(r: Index, c: Index, p: Index, q: Index): T = wrap(lut_load(s, Seq(r.s, c.s, p.s, q.s), bool(true)))
  }
  case class LUT5[T:Meta:Bits](s: Exp[LUT5[T]]) extends Template[LUT5[T]] with LUT[T] {
    @api def apply(r: Index, c: Index, p: Index, q: Index, m: Index): T = wrap(lut_load(s, Seq(r.s, c.s, p.s, q.s, m.s), bool(true)))
  }

  trait LUTType[T] {
    def child: Meta[T]
    def isPrimitive = false
  }
  case class LUT1Type[T:Bits](child: Meta[T]) extends Meta[LUT1[T]] with LUTType[T] {
    override def wrapped(x: Exp[LUT1[T]]) = LUT1(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[LUT1[T]]
  }
  case class LUT2Type[T:Bits](child: Meta[T]) extends Meta[LUT2[T]] with LUTType[T] {
    override def wrapped(x: Exp[LUT2[T]]) = LUT2(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[LUT2[T]]
  }
  case class LUT3Type[T:Bits](child: Meta[T]) extends Meta[LUT3[T]] with LUTType[T] {
    override def wrapped(x: Exp[LUT3[T]]) = LUT3(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[LUT3[T]]
  }
  case class LUT4Type[T:Bits](child: Meta[T]) extends Meta[LUT4[T]] with LUTType[T] {
    override def wrapped(x: Exp[LUT4[T]]) = LUT4(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[LUT4[T]]
  }
  case class LUT5Type[T:Bits](child: Meta[T]) extends Meta[LUT5[T]] with LUTType[T] {
    override def wrapped(x: Exp[LUT5[T]]) = LUT5(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[LUT5[T]]
  }

  implicit def lut1Type[T:Meta:Bits]: Meta[LUT1[T]] = LUT1Type(typ[T])
  implicit def lut2Type[T:Meta:Bits]: Meta[LUT2[T]] = LUT2Type(typ[T])
  implicit def lut3Type[T:Meta:Bits]: Meta[LUT3[T]] = LUT3Type(typ[T])
  implicit def lut4Type[T:Meta:Bits]: Meta[LUT4[T]] = LUT4Type(typ[T])
  implicit def lut5Type[T:Meta:Bits]: Meta[LUT5[T]] = LUT5Type(typ[T])

  /** IR Nodes **/
  case class LUTNew[T:Type:Bits,C[_]<:LUT[_]](dims: Seq[Int], elems: Seq[Exp[T]])(implicit cT: Type[C[T]]) extends Op[C[T]] {
    def mirror(f:Tx) = lut_new[T,C](dims, f(elems))
    val mT = typ[T]
  }

  case class LUTLoad[T:Type:Bits](
    lut:  Exp[LUT[T]],
    inds: Seq[Exp[Index]],
    en:   Exp[Bool]
  ) extends EnabledOp[T](en) {
    def mirror(f:Tx) = lut_load(f(lut),f(inds),f(en))
    override def aliases = Nil
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  private[spatial] def flatIndexConst(indices: Seq[Int], dims: Seq[Int])(implicit ctx: SrcCtx): Int = {
    val strides = List.tabulate(dims.length){d => dims.drop(d+1).product }
    indices.zip(strides).map{case (a,b) => a*b }.sum
  }

  @internal def lut_new[T:Type:Bits,C[_]<:LUT[_]](dims: Seq[Int], elems: Seq[Exp[T]])(implicit cT: Type[C[T]]) = {
    stageMutable(LUTNew[T,C](dims, elems))(ctx)
  }

  @internal def lut_load[T:Type:Bits](lut: Exp[LUT[T]], inds: Seq[Exp[Index]], en: Exp[Bool]) = {
    def node = stage(LUTLoad(lut, inds, en))(ctx)

    if (inds.forall{case Const(c: BigDecimal) => true; case _ => false}) lut match {
      case Op(LUTNew(dims, elems)) =>
        val is = inds.map{case Const(c: BigDecimal) => c.toInt }
        val index = flatIndexConst(is, dims)
        if (index < 0 || index >= elems.length) {
          if (argon.State.staging) {
            warn(ctx, s"Load from LUT at index " + is.mkString(", ") + " is out of bounds.")
            warn(ctx)
          }
          node
        }
        else elems(index)

      case _ => node
    }
    else node
  }
}
