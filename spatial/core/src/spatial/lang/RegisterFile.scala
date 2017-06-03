package spatial.lang

import spatial._
import forge._
import spatial.nodes.EnabledOp

trait RegisterFileApi extends RegisterFileExp { this: SpatialApi =>

  @api def RegFile[T:Meta:Bits](cols: Index): RegFile1[T] = wrap(regfile_new[T,RegFile1](cols.s))
  @api def RegFile[T:Meta:Bits](rows: Index, cols: Index): RegFile2[T] = wrap(regfile_new[T,RegFile2](rows.s,cols.s))
  @api def RegFile[T:Meta:Bits](dim0: Index, dim1: Index, dim2: Index): RegFile3[T] = wrap(regfile_new[T,RegFile3](dim0.s, dim1.s, dim2.s))
}

trait RegisterFileExp { this: SpatialExp =>

  trait RegFile[T] { this: Template[_] =>
    def s: Exp[RegFile[T]]

    @util def ranges: Seq[Range] = stagedDimsOf(s).map{d => range_alloc(None, wrap(d),None,None)}
  }

  case class RegFile1[T:Meta:Bits](s: Exp[RegFile1[T]]) extends Template[RegFile1[T]] with RegFile[T] {
    @api def apply(i: Index): T = wrap(regfile_load(s, Seq(i.s), bool(true)))
    @api def update(i: Index, data: T): Void = Void(regfile_store(s, Seq(i.s), data.s, bool(true)))

    @api def <<=(data: T): Void = wrap(regfile_shiftin(s, Seq(int32(0)), 0, data.s, bool(true)))
    @api def <<=(data: Vector[T]): Void = wrap(par_regfile_shiftin(s, Seq(int32(0)), 0, data.s, bool(true)))

    @api def load(dram: DRAM1[T]): Void = dense_transfer(dram.toTile(ranges), this, isLoad = true)
    @api def load(dram: DRAMDenseTile1[T]): Void = dense_transfer(dram, this, isLoad = true)
  }

  case class RegFile2[T:Meta:Bits](s: Exp[RegFile2[T]]) extends Template[RegFile2[T]] with RegFile[T] {
    @api def apply(r: Index, c: Index): T = wrap(regfile_load(s, Seq(r.s, c.s), bool(true)))
    @api def update(r: Index, c: Index, data: T): Void = Void(regfile_store(s, Seq(r.s, c.s), data.s, bool(true)))

    @api def apply(i: Index, y: Wildcard) = RegFileView(s, Seq(i,lift[Int,Index](0)), 1)
    @api def apply(y: Wildcard, i: Index) = RegFileView(s, Seq(lift[Int,Index](0),i), 0)

    @api def load(dram: DRAM2[T]): Void = dense_transfer(dram.toTile(ranges), this, isLoad = true)
    @api def load(dram: DRAMDenseTile2[T]): Void = dense_transfer(dram, this, isLoad = true)
  }

  case class RegFile3[T:Meta:Bits](s: Exp[RegFile3[T]]) extends Template[RegFile3[T]] with RegFile[T] {
    @api def apply(dim0: Index, dim1: Index, dim2: Index): T = wrap(regfile_load(s, Seq(dim0.s, dim1.s, dim2.s), bool(true)))
    @api def update(dim0: Index, dim1: Index, dim2: Index, data: T): Void = Void(regfile_store(s, Seq(dim0.s, dim1.s, dim2.s), data.s, bool(true)))

    @api def apply(i: Index, j: Index, y: Wildcard) = RegFileView(s, Seq(i,j,lift[Int,Index](0)), 2)
    @api def apply(i: Index, y: Wildcard, j: Index) = RegFileView(s, Seq(i,lift[Int,Index](0),j), 1)
    @api def apply(y: Wildcard, i: Index, j: Index) = RegFileView(s, Seq(lift[Int,Index](0),i,j), 0)

    @api def load(dram: DRAM3[T]): Void = dense_transfer(dram.toTile(ranges), this, isLoad = true)
    @api def load(dram: DRAMDenseTile3[T]): Void = dense_transfer(dram, this, isLoad = true)
  }

  case class RegFileView[T:Meta:Bits](s: Exp[RegFile[T]], i: Seq[Index], dim: Int) {
    @api def <<=(data: T): Void = wrap(regfile_shiftin(s, unwrap(i), dim, data.s, bool(true)))
    @api def <<=(data: Vector[T]): Void = wrap(par_regfile_shiftin(s, unwrap(i), dim, data.s, bool(true)))
  }

  /** Type classes **/
  // Meta
  trait RegFileType[T] {
    def child: Meta[T]
    def isPrimitive = false
  }
  case class RegFile1Type[T:Bits](child: Meta[T]) extends Meta[RegFile1[T]] with RegFileType[T] {
    override def wrapped(x: Exp[RegFile1[T]]) = RegFile1(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[RegFile1[T]]
  }
  case class RegFile2Type[T:Bits](child: Meta[T]) extends Meta[RegFile2[T]] with RegFileType[T] {
    override def wrapped(x: Exp[RegFile2[T]]) = RegFile2(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[RegFile2[T]]
  }
  case class RegFile3Type[T:Bits](child: Meta[T]) extends Meta[RegFile3[T]] with RegFileType[T] {
    override def wrapped(x: Exp[RegFile3[T]]) = RegFile3(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[RegFile3[T]]
  }

  implicit def regFile1Type[T:Meta:Bits]: Meta[RegFile1[T]] = RegFile1Type(typ[T])
  implicit def regFile2Type[T:Meta:Bits]: Meta[RegFile2[T]] = RegFile2Type(typ[T])
  implicit def regFile3Type[T:Meta:Bits]: Meta[RegFile3[T]] = RegFile3Type(typ[T])


  // Mem
  class RegFileIsMemory[T:Meta:Bits,C[T]](implicit mC: Meta[C[T]], ev: C[T] <:< RegFile[T]) extends Mem[T, C] {
    def load(mem: C[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = {
      wrap(regfile_load(mem.s, unwrap(is), en.s))
    }

    def store(mem: C[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = {
      wrap(regfile_store(mem.s, unwrap(is), data.s, en.s))
    }
    def iterators(mem: C[T])(implicit ctx: SrcCtx): Seq[Counter] = {
      stagedDimsOf(mem.s).map{d => Counter(0, wrap(d), 1, 1) }
    }
  }
  implicit def regfile1IsMemory[T:Meta:Bits]: Mem[T,RegFile1] = new RegFileIsMemory[T,RegFile1]
  implicit def regfile2IsMemory[T:Meta:Bits]: Mem[T,RegFile2] = new RegFileIsMemory[T,RegFile2]
  implicit def regfile3IsMemory[T:Meta:Bits]: Mem[T,RegFile3] = new RegFileIsMemory[T,RegFile3]


  /** IR Nodes **/
  case class RegFileNew[T:Type:Bits,C[_]<:RegFile[_]](dims: Seq[Exp[Index]])(implicit cT: Type[C[T]]) extends Op[C[T]] {
    def mirror(f:Tx) = regfile_new[T,C](f(dims):_*)
    val mT = typ[T]
  }

  case class RegFileLoad[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    en:   Exp[Bool]
  ) extends EnabledOp[T](en) {
    def mirror(f:Tx) = regfile_load(f(reg),f(inds),f(en))
    override def aliases = Nil
    val mT = typ[T]
    val bT = bits[T]
  }

  case class RegFileStore[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    data: Exp[T],
    en:   Exp[Bool]
  ) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = regfile_store(f(reg),f(inds),f(data),f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  case class RegFileShiftIn[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    dim:  Int,
    data: Exp[T],
    en:   Exp[Bool]
  ) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = regfile_shiftin(f(reg),f(inds),dim,f(data),f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  case class ParRegFileShiftIn[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    dim:  Int,
    data: Exp[Vector[T]],
    en:   Exp[Bool]
  ) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = par_regfile_shiftin(f(reg),f(inds),dim,f(data),f(en))
  }


  /** Constructors **/
  @internal def regfile_new[T:Type:Bits,C[_]<:RegFile[_]](dims: Exp[Index]*)(implicit cT: Type[C[T]]) = {
    stageMutable(RegFileNew[T,C](dims))(ctx)
  }

  @internal def regfile_load[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    en:   Exp[Bool]
  ) = {
    stage(RegFileLoad(reg, inds, en))(ctx)
  }

  @internal def regfile_store[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    data: Exp[T],
    en:   Exp[Bool]
  ) = {
    stageWrite(reg)(RegFileStore(reg, inds, data, en))(ctx)
  }

  @internal def regfile_shiftin[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    dim:  Int,
    data: Exp[T],
    en:   Exp[Bool]
  ) = {
    stageWrite(reg)(RegFileShiftIn(reg, inds, dim, data, en))(ctx)
  }

  @internal def par_regfile_shiftin[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    dim:  Int,
    data: Exp[Vector[T]],
    en:   Exp[Bool]
  ) = {
    stageWrite(reg)(ParRegFileShiftIn(reg, inds, dim, data, en))(ctx)
  }
}
