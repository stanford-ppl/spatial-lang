package spatial.api
import argon.core.Staging
import argon.ops._
import spatial.analysis.{SpatialMetadataExp}
import spatial.{SpatialApi, SpatialExp}

// MemReduce and views
//   If view is staged, requires either direct access to its target via a def or its own load/store defs
//   If view is unstaged, requires unwrapping prior to use in result of Blocks / use as dependencies
//   However, if view is staged, have mutable sharing..

trait ControllerApi extends ControllerExp {
  this: SpatialApi =>

  protected case class MemReduceAccum[T,C[T]](accum: C[T], style: ControlStyle, zero: Option[T], fold: scala.Boolean) {
    /** 1 dimensional memory reduction **/
    def apply(domain1D: Counter)(map: Index => C[T])(reduce: (T,T) => T)(implicit ctx: SrcCtx, mem: Mem[T,C], mT: Type[T], bT: Bits[T], mC: Type[C[T]]): C[T] = {
      mem_reduceND(List(domain1D), accum, {x: List[Index] => map(x.head)}, reduce, style, zero, fold)
      accum
    }

    /** 2 dimensional memory reduction **/
    def apply(domain1: Counter, domain2: Counter)(map: (Index,Index) => C[T])(reduce: (T,T) => T)(implicit ctx: SrcCtx, mem: Mem[T,C], mT: Type[T], bT: Bits[T], mC: Type[C[T]]): C[T] = {
      mem_reduceND(List(domain1,domain2), accum, {x: List[Index] => map(x(0),x(1)) }, reduce, style, zero, fold)
      accum
    }

    /** 3 dimensional memory reduction **/
    def apply(domain1: Counter, domain2: Counter, domain3: Counter)(map: (Index,Index,Index) => C[T])(reduce: (T,T) => T)(implicit ctx: SrcCtx, mem: Mem[T,C], mT: Type[T], bT: Bits[T], mC: Type[C[T]]): C[T] = {
      mem_reduceND(List(domain1,domain2,domain3), accum, {x: List[Index] => map(x(0),x(1),x(2)) }, reduce, style, zero, fold)
      accum
    }

    /** N dimensional memory reduction **/
    def apply(domain1: Counter, domain2: Counter, domain3: Counter, domain4: Counter, domain5plus: Counter*)(map: List[Index] => C[T])(reduce: (T,T) => T)(implicit ctx: SrcCtx, mem: Mem[T,C], mT: Type[T], bT: Bits[T], mC: Type[C[T]]): C[T] = {
      mem_reduceND(List(domain1,domain2,domain3,domain4) ++ domain5plus, accum, map, reduce, style, zero, fold)
      accum
    }
  }

  protected case class MemReduceClass(style: ControlStyle) {
    def apply[T,C[T]](accum: C[T]) = MemReduceAccum[T,C](accum, style, None, fold = false)
    def apply[T,C[T]](accum: C[T], zero: T) = MemReduceAccum[T,C](accum, style, Some(zero), fold = false)
  }

  protected case class MemFoldClass(style: ControlStyle) {
    def apply[T,C[T]](accum: C[T]) = MemReduceAccum[T,C](accum, style, None, fold = true)
    def apply[T,C[T]](accum: C[T], zero: T) = MemReduceAccum[T,C](accum, style, Some(zero), fold = true)
  }


  protected class ReduceAccum[T](accum: Option[Reg[T]], style: ControlStyle, zero: Option[T], fold: Option[T]) {
    /** 1 dimensional reduction **/
    def apply(domain1D: Counter)(map: Index => T)(reduce: (T,T) => T)(implicit ctx: SrcCtx, mT: Type[T], bits: Bits[T]): Reg[T] = {
      val acc = accum.getOrElse(Reg[T])
      reduceND(List(domain1D), acc, {x: List[Index] => map(x.head)}, reduce, style, zero, fold)
      acc
    }
    /** 2 dimensional reduction **/
    def apply(domain1: Counter, domain2: Counter)(map: (Index,Index) => T)(reduce: (T,T) => T)(implicit ctx: SrcCtx, mT: Type[T], bits: Bits[T]): Reg[T] = {
      val acc = accum.getOrElse(Reg[T])
      reduceND(List(domain1, domain2), acc, {x: List[Index] => map(x(0),x(1)) }, reduce, style, zero, fold)
      acc
    }

    /** 3 dimensional reduction **/
    def apply(domain1: Counter, domain2: Counter, domain3: Counter)(map: (Index,Index,Index) => T)(reduce: (T,T) => T)(implicit ctx: SrcCtx, mT: Type[T], bits: Bits[T]): Reg[T] = {
      val acc = accum.getOrElse(Reg[T])
      reduceND(List(domain1, domain2, domain3), acc, {x: List[Index] => map(x(0),x(1),x(2)) }, reduce, style, zero, fold)
      acc
    }

    /** N dimensional reduction **/
    def apply(domain1: Counter, domain2: Counter, domain3: Counter, domain4: Counter, domain5plus: Counter*)(map: List[Index] => T)(reduce: (T,T) => T)(implicit ctx: SrcCtx, mT: Type[T], bits: Bits[T]): Reg[T] = {
      val acc = accum.getOrElse(Reg[T])
      reduceND(List(domain1, domain2, domain3, domain4) ++ domain5plus, acc, map, reduce, style, zero, fold)
      acc
    }

  }

  protected case class ReduceClass(style: ControlStyle) extends ReduceAccum(None, style, None, None) {
    import org.virtualized.SourceContext
    /** Reduction with implicit accumulator **/
    // TODO: Can't use ANY implicits if we want to be able to use Reduce(0)(...). Maybe a macro can help here?
    def apply(zero: scala.Int) = new ReduceAccum(Some(Reg[Int32](int2fixpt[TRUE,_32,_0](zero))), style, Some(lift[Int,Int32](zero)), None)
    def apply(zero: scala.Long) = new ReduceAccum(Some(Reg[Int64](long2fixpt[TRUE,_64,_0](zero))), style, Some(lift[Long,Int64](zero)), None)
    def apply(zero: scala.Float) = new ReduceAccum(Some(Reg[Float32](float2fltpt[_24,_8](zero))), style, Some(lift[Float,Float32](zero)), None)
    def apply(zero: scala.Double) = new ReduceAccum(Some(Reg[Float64](double2fltpt[_53,_11](zero))), style, Some(lift[Double,Float64](zero)), None)

    //def apply(zero: FixPt[_,_,_]) = new ReduceAccum(Reg[FixPt[S,I,F]](zero), style)
    //def apply(zero: FltPt[_,_]) = new ReduceAccum(Reg[FltPt[G,E]](zero), style)

    /** Reduction with explicit accumulator **/
    // TODO: Should initial value of accumulator be assumed to be the identity value?
    def apply[T](accum: Reg[T]) = new ReduceAccum(Some(accum), style, None, None)
  }

  protected case class FoldClass(style: ControlStyle) {
    import org.virtualized.SourceContext
    /** Fold with implicit accumulator **/
    // TODO: Can't use ANY implicits if we want to be able to use Reduce(0)(...). Maybe a macro can help here?
    def apply(zero: scala.Int) = new ReduceAccum(Some(Reg[Int32](int2fixpt[TRUE,_32,_0](zero))), style, None, Some(lift[Int,Int32](zero)))
    def apply(zero: scala.Long) = new ReduceAccum(Some(Reg[Int64](long2fixpt[TRUE,_64,_0](zero))), style, None, Some(lift[Long,Int64](zero)))
    def apply(zero: scala.Float) = new ReduceAccum(Some(Reg[Float32](float2fltpt[_24,_8](zero))), style, None, Some(lift[Float,Float32](zero)))
    def apply(zero: scala.Double) = new ReduceAccum(Some(Reg[Float64](double2fltpt[_53,_11](zero))), style, None, Some(lift[Double,Float64](zero)))

    def apply[T](accum: Reg[T]) = {
      val sty = if (style == InnerPipe) MetaPipe else style
      MemReduceAccum(accum, sty, None, true)
    }
  }

  protected class ForeachClass(style: ControlStyle) {
    /** 1 dimensional parallel foreach **/
    def apply(domain1D: Counter)(func: Index => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1D), {x: List[Index] => func(x.head) }, style)
      ()
    }
    /** 2 dimensional parallel foreach **/
    def apply(domain1: Counter, domain2: Counter)(func: (Index,Index) => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1,domain2), {x: List[Index] => func(x(0),x(1)) }, style)
      ()
    }
    /** 3 dimensional parallel foreach **/
    def apply(domain1: Counter, domain2: Counter, domain3: Counter)(func: (Index,Index,Index) => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1,domain2,domain3), {x: List[Index] => func(x(0),x(1),x(2)) }, style)
      ()
    }
    /** N dimensional parallel foreach **/
    def apply(domain1: Counter, domain2: Counter, domain3: Counter, domain4: Counter, domain5plus: Counter*)(func: List[Index] => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1,domain2,domain3,domain4) ++ domain5plus, func, style)
      ()
    }
    def apply(domain: Seq[Counter])(func: List[Index] => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(domain, func, style)
      ()
    }
  }

  object MemReduce extends MemReduceClass(MetaPipe)
  object MemFold   extends MemFoldClass(MetaPipe)

  object Reduce    extends ReduceClass(InnerPipe)
  object Fold      extends FoldClass(InnerPipe)

  object Foreach   extends ForeachClass(InnerPipe)

  object Accel {
    def apply(func: => Void)(implicit ctx: SrcCtx): Void = {
      val f = () => { func; Unit() }
      accel_blk(f(), false); ()
    }
    def apply(ctr: Counter)(func: => Any)(implicit ctx: SrcCtx) = {
      val f = () => { func; Unit() }
      accel_blk(f(), ctr); ()
    }
  }

  // NOTE: Making applies here not take Void results in ambiguity between 1D foreach and unit pipe

  object Pipe extends ForeachClass(InnerPipe) {
    /** "Pipelined" unit controller **/
    def apply(func: => Void)(implicit ctx: SrcCtx): Void = { unit_pipe(func, SeqPipe); () }
    def Foreach   = new ForeachClass(InnerPipe)
    def Reduce    = ReduceClass(InnerPipe)
    def Fold      = FoldClass(InnerPipe)
    def MemReduce = MemReduceClass(MetaPipe)
    def MemFold   = MemFoldClass(MetaPipe)
  }

  object Sequential extends ForeachClass(SeqPipe) {
    /** Sequential unit controller **/
    def apply(func: => Void)(implicit ctx: SrcCtx): Void = { unit_pipe(func, SeqPipe); () }
    def Foreach   = new ForeachClass(SeqPipe)
    def Reduce    = ReduceClass(SeqPipe)
    def Fold      = FoldClass(SeqPipe)
    def MemReduce = MemReduceClass(SeqPipe)
    def MemFold   = MemFoldClass(SeqPipe)
  }

  object Stream extends ForeachClass(StreamPipe) {
    /** Streaming unit controller **/
    def apply(func: => Void)(implicit ctx: SrcCtx): Void = { unit_pipe(func, StreamPipe); () }
    def Foreach   = new ForeachClass(StreamPipe)
    def Reduce    = ReduceClass(StreamPipe)
    def Fold      = FoldClass(StreamPipe)
    def MemReduce = MemReduceClass(StreamPipe)
    def MemFold   = MemFoldClass(StreamPipe)
  }

  object Parallel {
    def apply(func: => Any)(implicit ctx: SrcCtx): Void = {
      val f = () => { func; Unit() }
      parallel_pipe(f()); ()
    }
  }
}

trait ControllerExp extends Staging {
  this: SpatialExp =>

  /** API **/
  implicit object ControllerType extends Meta[Controller] {
    override def wrapped(x: Exp[Controller]) = Controller(x)
    override def stagedClass = classOf[Controller]
    override def isPrimitive = true
  }

  case class Controller(s: Exp[Controller]) extends Template[Controller]


  private[spatial] def accel_blk(func: => Void, ctr: Counter)(implicit ctx: SrcCtx): Controller = {
    if (isForever(ctr.s)) {
      accel_blk(func, isForever = true)
    }
    else {
      accel_blk({
        foreachND(Seq(ctr), {_: List[Index] => func }, SeqPipe)
        ()
      }, false)
    }
  }

  private[spatial] def accel_blk(func: => Void, isForever: Boolean)(implicit ctx: SrcCtx): Controller = {
    val fFunc = () => unwrap(func)
    val pipe = op_accel(fFunc(), isForever)
    styleOf(pipe) = SeqPipe
    levelOf(pipe) = InnerControl
    Controller(pipe)
  }

  private[spatial] def unit_pipe(func: => Void, style: ControlStyle)(implicit ctx: SrcCtx): Controller = {
    val fFunc = () => unwrap(func)
    val pipe = op_unit_pipe(Nil, fFunc())
    styleOf(pipe) = style
    levelOf(pipe) = InnerControl // Fixed in Level Analyzer
    Controller(pipe)
  }

  private[spatial] def parallel_pipe(func: => Void)(implicit ctx: SrcCtx): Controller = {
    val fFunc = () => unwrap(func)
    val pipe = op_parallel_pipe(Nil, fFunc()) // Sets ForkJoin and OuterController
    Controller(pipe)
  }

  private[spatial] def foreachND(
    domain: Seq[Counter],
    func:   List[Index] => Void,
    style:  ControlStyle
  )(implicit ctx: SrcCtx): Controller = {
    val iters = List.tabulate(domain.length){_ => fresh[Index] }

    val fFunc = () => unwrap( func(wrap(iters)) )
    val cchain = CounterChain(domain: _*)

    val pipe = op_foreach(cchain.s, fFunc(), iters)
    styleOf(pipe) = style
    levelOf(pipe) = InnerControl // Fixed in Level Analyzer
    Controller(pipe)
  }

  private[spatial] def reduceND[T:Meta:Bits](
    domain: Seq[Counter],
    reg:    Reg[T],
    map:    List[Index] => T,
    reduce: (T,T) => T,
    style:  ControlStyle,
    ident:  Option[T],
    fold:   Option[T]
  )(implicit ctx: SrcCtx): Controller = {

    val rV = (fresh[T], fresh[T])
    val iters = List.tabulate(domain.length){_ => fresh[Index] }

    val mBlk  = stageBlock{ map(wrap(iters)).s }
    val ldBlk = stageLambda(reg.s) { reg.value.s }
    val rBlk  = stageBlock{ reduce(wrap(rV._1),wrap(rV._2)).s }
    val stBlk = stageLambda(reg.s, rBlk.result){ unwrap( reg := wrap(rBlk.result) ) }

    val cchain = CounterChain(domain: _*)
    val z = ident.map(_.s)
    val f = fold.map(_.s)

    val effects = mBlk.summary andAlso ldBlk.summary andAlso rBlk.summary andAlso stBlk.summary
    val pipe = stageEffectful(OpReduce[T](cchain.s, reg.s, mBlk, ldBlk, rBlk, stBlk, z, f, rV, iters), effects)(ctx)
    styleOf(pipe) = style
    levelOf(pipe) = InnerControl // Fixed in Level Analyzer
    Controller(pipe)
  }

  private[spatial] def mem_reduceND[T:Meta:Bits,C[T]](
    domain: Seq[Counter],
    accum:  C[T],
    map:    List[Index] => C[T],
    reduce: (T,T) => T,
    style:  ControlStyle,
    ident:  Option[T],
    fold:   Boolean
  )(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Meta[C[T]]): Controller = {
    val rV = (fresh[T], fresh[T])
    val itersMap = List.tabulate(domain.length){_ => fresh[Index] }

    val ctrsRed = mem.iterators(accum)
    val itersRed = ctrsRed.map{_ => fresh[Index] }

    val mBlk  = stageBlock{ map(wrap(itersMap)).s }
    val rBlk  = stageBlock{ reduce(wrap(rV._1), wrap(rV._2)).s }
    val ldResBlk = stageLambda(mBlk.result){ mem.load(wrap(mBlk.result), wrap(itersRed), true).s }
    val ldAccBlk = stageLambda(accum.s) { mem.load(accum, wrap(itersRed), true).s }
    val stAccBlk = stageLambda(accum.s, rBlk.result){ mem.store(accum, wrap(itersRed), wrap(rBlk.result), true).s }

    val cchainMap = CounterChain(domain: _*)
    val cchainRed = CounterChain(ctrsRed: _*)
    val z = ident.map(_.s)

    val effects = mBlk.summary andAlso rBlk.summary andAlso ldResBlk.summary andAlso ldAccBlk.summary andAlso stAccBlk.summary
    val node = stageEffectful(OpMemReduce[T,C](cchainMap.s,cchainRed.s,accum.s,mBlk,ldResBlk,ldAccBlk,rBlk,stAccBlk,z,fold,rV,itersMap,itersRed), effects)(ctx)
    styleOf(node) = style
    levelOf(node) = OuterControl
    Controller(node)
  }

  /** IR Nodes **/
  case class Hwblock(func: Block[Void], isForever: Boolean) extends Op[Controller] {
    def mirror(f:Tx) = op_accel(f(func), isForever)
    override def freqs = cold(func)
  }

  abstract class EnabledController extends Op[Controller] {
    def en: Seq[Exp[Bool]]
    final def mirror(f:Tx): Exp[Controller] = mirrorWithEn(f, Nil)
    def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bool]]): Exp[Controller]
  }

  case class UnitPipe(en: Seq[Exp[Bool]], func: Block[Void]) extends EnabledController {
    def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bool]]) = op_unit_pipe(f(en) ++ addEn, f(func))
    override def freqs = cold(func)
  }

  case class ParallelPipe(en: Seq[Exp[Bool]], func: Block[Void]) extends EnabledController {
    def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bool]]) = op_parallel_pipe(f(en) ++ addEn, f(func))
    override def freqs = cold(func)
  }


  case class OpForeach(cchain: Exp[CounterChain], func: Block[Void], iters: List[Bound[Index]]) extends Op[Controller] {
    def mirror(f:Tx) = op_foreach(f(cchain), f(func), iters)

    override def inputs = dyns(cchain) ++ dyns(func)
    override def freqs  = cold(func) ++ normal(cchain)
    override def binds  = super.binds ++ iters
  }

  case class OpReduce[T:Type:Bits](
    cchain: Exp[CounterChain],
    accum:  Exp[Reg[T]],
    map:    Block[T],
    load:   Block[T],
    reduce: Block[T],
    store:  Block[Void],
    ident:  Option[Exp[T]],
    fold:   Option[Exp[T]],
    rV:     (Bound[T],Bound[T]),
    iters:  List[Bound[Index]]
  ) extends Op[Controller] {
    def mirror(f:Tx) = op_reduce(f(cchain), f(accum), f(map), f(load), f(reduce), f(store), f(ident), f(fold), rV, iters)

    override def inputs = dyns(cchain) ++ dyns(map) ++ dyns(reduce) ++ dyns(accum) ++ dyns(load) ++ dyns(store) ++ dyns(ident)
    override def freqs  = cold(map) ++ cold(reduce) ++ normal(cchain) ++ normal(accum) ++ hot(load) ++ hot(store)
    override def binds  = super.binds ++ iters ++ List(rV._1, rV._2)
    override def aliases = Nil
    val mT = typ[T]
    val bT = bits[T]
  }

  case class OpMemReduce[T:Type:Bits,C[T]](
    cchainMap: Exp[CounterChain],
    cchainRed: Exp[CounterChain],
    accum:     Exp[C[T]],
    map:       Block[C[T]],
    loadRes:   Block[T],
    loadAcc:   Block[T],
    reduce:    Block[T],
    storeAcc:  Block[Void],
    ident:     Option[Exp[T]],
    fold:      Boolean,
    rV:        (Bound[T], Bound[T]),
    itersMap:  Seq[Bound[Index]],
    itersRed:  Seq[Bound[Index]]
  )(implicit val mem: Mem[T,C], val mC: Type[C[T]]) extends Op[Controller] {
    def mirror(f:Tx) = op_mem_reduce(f(cchainMap),f(cchainRed),f(accum),f(map),f(loadRes),f(loadAcc),f(reduce),
                                     f(storeAcc), f(ident), fold, rV, itersMap, itersRed)

    override def inputs = dyns(cchainMap) ++ dyns(cchainRed) ++ dyns(accum) ++ dyns(map) ++ dyns(reduce) ++
                          dyns(ident) ++ dyns(loadRes) ++ dyns(loadAcc) ++ dyns(storeAcc)
    override def freqs = cold(map) ++ cold(reduce) ++ normal(cchainMap) ++ normal(cchainRed) ++ normal(accum)
    override def binds = super.binds ++ itersMap ++ itersRed ++ List(rV._1, rV._2)
    override def aliases = Nil

    val mT = typ[T]
    val bT = bits[T]
  }


  /** Constructors **/
  def op_accel(func: => Exp[Void], isForever: Boolean)(implicit ctx: SrcCtx): Sym[Controller] = {
    val fBlk = stageBlock{ func }
    val effects = fBlk.summary andAlso Simple
    stageEffectful( Hwblock(fBlk, isForever), effects)(ctx)
  }

  def op_unit_pipe(en: Seq[Exp[Bool]], func: => Exp[Void])(implicit ctx: SrcCtx): Sym[Controller] = {
    val fBlk = stageBlock{ func }
    val effects = fBlk.summary
    stageEffectful( UnitPipe(en, fBlk), effects)(ctx)
  }

  def op_parallel_pipe(en: Seq[Exp[Bool]], func: => Exp[Void])(implicit ctx: SrcCtx): Sym[Controller] = {
    val fBlk = stageBlock{ func }
    val effects = fBlk.summary
    val pipe = stageEffectful( ParallelPipe(en, fBlk), effects)(ctx)
    styleOf(pipe) = ForkJoin
    levelOf(pipe) = OuterControl
    pipe
  }

  def op_foreach(domain: Exp[CounterChain], func: => Exp[Void], iters: List[Bound[Index]])(implicit ctx: SrcCtx): Sym[Controller] = {
    val fBlk = stageBlock{ func }
    val effects = fBlk.summary.star
    stageEffectful( OpForeach(domain, fBlk, iters), effects)(ctx)
  }

  def op_reduce[T:Type:Bits](
    cchain: Exp[CounterChain],
    reg:    Exp[Reg[T]],
    map:    => Exp[T],
    load:   => Exp[T],
    reduce: => Exp[T],
    store:  => Exp[Void],
    ident:  Option[Exp[T]],
    fold:   Option[Exp[T]],
    rV:     (Bound[T],Bound[T]),
    iters:  List[Bound[Index]]
  )(implicit ctx: SrcCtx): Sym[Controller] = {

    val mBlk  = stageBlock{ map }
    val ldBlk = stageLambda(reg){ load }
    val rBlk  = stageBlock{ reduce }
    val stBlk = stageLambda(reg, rBlk.result){ store }

    val effects = mBlk.summary andAlso ldBlk.summary andAlso rBlk.summary andAlso stBlk.summary
    stageEffectful( OpReduce[T](cchain, reg, mBlk, ldBlk, rBlk, stBlk, ident, fold, rV, iters), effects)(ctx)
  }
  def op_mem_reduce[T:Type:Bits,C[T]](
    cchainMap: Exp[CounterChain],
    cchainRed: Exp[CounterChain],
    accum:     Exp[C[T]],
    map:       => Exp[C[T]],
    loadRes:   => Exp[T],
    loadAcc:   => Exp[T],
    reduce:    => Exp[T],
    storeAcc:  => Exp[Void],
    ident:     Option[Exp[T]],
    fold:      Boolean,
    rV:        (Bound[T], Bound[T]),
    itersMap:  Seq[Bound[Index]],
    itersRed:  Seq[Bound[Index]]
  )(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Type[C[T]]): Sym[Controller] = {

    val mBlk = stageBlock{ map }
    val ldResBlk = stageLambda(mBlk.result){ loadRes }
    val ldAccBlk = stageLambda(accum){ loadAcc }
    val rBlk = stageBlock{ reduce }
    val stBlk = stageLambda(accum, rBlk.result){ storeAcc }

    val effects = mBlk.summary andAlso ldResBlk.summary andAlso ldAccBlk.summary andAlso rBlk.summary andAlso stBlk.summary
    stageEffectful( OpMemReduce[T,C](cchainMap, cchainRed, accum, mBlk, ldResBlk, ldAccBlk, rBlk, stBlk, ident, fold, rV, itersMap, itersRed), effects)(ctx)
  }


}

