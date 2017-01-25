package spatial.api
import argon.ops._
import spatial.analysis.{SpatialMetadataExp, SpatialMetadataOps}
import spatial.{SpatialApi, SpatialExp, SpatialOps}

// MemReduce and views
//   If view is staged, requires either direct access to its target via a def or its own load/store defs
//   If view is unstaged, requires unwrapping prior to use in result of Blocks / use as dependencies
//   However, if view is staged, have mutable sharing..

trait ControllerOps extends RegOps with SRAMOps with VoidOps with CounterOps with SpatialMetadataOps with FltPtOps { this: SpatialOps =>

  type Controller
  implicit val ControllerType: Staged[Controller]

  case class MemReduceClass(style: Option[ControlStyle]) {
    /** 1 dimensional memory reduction with explicit accumulator **/
    def apply[T:Bits,C[T]](accum: C[T])(domain1D: Counter)(map: Index => C[T])(reduce: (T,T) => T)(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Staged[C[T]]): C[T] = {
      mem_reduceND(List(domain1D), accum, {x: List[Index] => map(x.head)}, reduce, style)
      accum
    }
  }

  case class ReduceAccum[T](accum: Reg[T], style: Option[ControlStyle]) {
    def apply(domain1D: Counter)(map: Index => T)(reduce: (T,T) => T)(implicit ctx: SrcCtx, bits: Bits[T]): Reg[T] = {
      reduceND(List(domain1D), accum, {x: List[Index] => map(x.head)}, reduce, style)
      accum
    }
    def apply(domain1: Counter, domain2: Counter)(map: (Index,Index) => T)(reduce: (T,T) => T)(implicit ctx: SrcCtx, bits: Bits[T]): Reg[T] = {
      reduceND(List(domain1, domain2), accum, {x: List[Index] => map(x(0),x(1)) }, reduce, style)
      accum
    }
  }

  case class ReduceClass(style: Option[ControlStyle]) {
    import org.virtualized.SourceContext
    /** Reduction with implicit accumulator **/
    // TODO: Can't use ANY implicits if we want to be able to use Reduce(0)(...). Maybe a macro can help here?
    def apply(zero: Int) = ReduceAccum(Reg[Int32](int2fixpt[TRUE,_32,_0](zero)), style)
    def apply(zero: Long) = ReduceAccum(Reg[Int64](long2fixpt[TRUE,_64,_0](zero)), style)
    def apply(zero: Float) = ReduceAccum(Reg[Float32](float2fltpt[_24,_8](zero)), style)
    def apply(zero: Double) = ReduceAccum(Reg[Float64](double2fltpt[_53,_11](zero)), style)
    //def apply(zero: FixPt[_,_,_]) = ReduceAccum(Reg[FixPt[S,I,F]](zero), style)
    //def apply(zero: FltPt[_,_]) = ReduceAccum(Reg[FltPt[G,E]](zero), style)

    /** Reduction with explicit accumulator **/
    def apply[T](accum: Reg[T]) = ReduceAccum(accum, style)
  }

  case class ForeachClass(style: Option[ControlStyle]) {
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
    /** N dimensional parallel foreach **/
    def apply(domain1: Counter, domain2: Counter, domain3: Counter, domains: Counter*)(func: List[Index] => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1,domain2,domain3) ++ domains.toList, func, style)
      ()
    }
    def apply(domain: Seq[Counter])(func: List[Index] => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(domain, func, style)
      ()
    }
  }

  object MemReduce extends MemReduceClass(None)
  object Reduce    extends ReduceClass(None)
  object Foreach   extends ForeachClass(None)

  object Accel {
    def apply(func: => Void)(implicit ctx: SrcCtx): Void = { accel_blk(func); () }
  }

  object Pipe {
    /** "Pipelined" unit controller **/
    def apply(func: => Void)(implicit ctx: SrcCtx): Void = {
      unit_pipe(func, Some(InnerPipe))
      ()
    }
    def Foreach = ForeachClass(Some(InnerPipe))
    def Reduce = ReduceClass(Some(InnerPipe))
    def MemReduce = MemReduceClass(Some(InnerPipe))
  }

  object Sequential {
    /** Sequential unit controller **/
    def apply(func: => Void)(implicit ctx: SrcCtx): Void = {
      unit_pipe(func, Some(SeqPipe))
      ()
    }

    def Foreach = ForeachClass(Some(SeqPipe))
    def Reduce = ReduceClass(Some(SeqPipe))
    def MemReduce = MemReduceClass(Some(SeqPipe))
  }

  object Stream {
    /** Streaming unit controller **/
    def apply(func: => Void)(implicit ctx: SrcCtx): Void = {
      unit_pipe(func, Some(StreamPipe))
      ()
    }

    def Foreach = ForeachClass(Some(StreamPipe))
    def Reduce = ReduceClass(Some(StreamPipe))
    def MemReduce = MemReduceClass(Some(StreamPipe))
  }

  private[spatial] def accel_blk(func: => Void)(implicit ctx: SrcCtx): Controller
  private[spatial] def unit_pipe(func: => Void, style: Option[ControlStyle])(implicit ctx: SrcCtx): Controller
  private[spatial] def foreachND(
    domain: Seq[Counter],
    func: List[Index] => Void,
    style: Option[ControlStyle]
  )(implicit ctx: SrcCtx): Controller

  private[spatial] def reduceND[T:Bits](
    domain: Seq[Counter],
    reg:    Reg[T],
    map:    List[Index] => T,
    reduce: (T,T) => T,
    style:  Option[ControlStyle]
  )(implicit ctx: SrcCtx): Controller
  private[spatial] def mem_reduceND[T:Bits,C[T]](
    domain: Seq[Counter],
    accum:  C[T],
    map:    List[Index] => C[T],
    reduce: (T,T) => T,
    style: Option[ControlStyle]
  )(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Staged[C[T]]): Controller
}
trait ControllerApi extends ControllerOps with RegApi with SRAMApi with CounterApi { this: SpatialApi => }

trait ControllerExp extends ControllerOps with RegExp with SRAMExp with CounterExp with SpatialMetadataExp { this: SpatialExp =>
  /** API **/
  case class Controller(s: Exp[Controller])
  implicit object ControllerType extends Staged[Controller] {
    override def isPrimitive = true
    override def unwrapped(x: Controller) = x.s
    override def wrapped(x: Exp[Controller]) = Controller(x)
    override def typeArguments = Nil
    override def stagedClass = classOf[Controller]
  }

  private[spatial] def accel_blk(func: => Void)(implicit ctx: SrcCtx): Controller = {
    val fFunc = () => unwrap(func)
    val pipe = op_accel(fFunc())
    Controller(pipe)
  }

  private[spatial] def unit_pipe(func: => Void, style: Option[ControlStyle])(implicit ctx: SrcCtx): Controller = {
    val fFunc = () => unwrap(func)
    val pipe = op_unit_pipe(fFunc())
    style.foreach{s => styleOf(pipe) = s}
    Controller(pipe)
  }

  private[spatial] def foreachND(
    domain: Seq[Counter],
    func:   List[Index] => Void,
    style:  Option[ControlStyle]
  )(implicit ctx: SrcCtx): Controller = {
    val iters = List.tabulate(domain.length){_ => fresh[Index] }

    val fFunc = () => unwrap( func(wrap(iters)) )
    val cchain = CounterChain(domain: _*)

    val pipe = op_foreach(cchain.s, fFunc(), iters)
    style.foreach{s => styleOf(pipe) = s}
    Controller(pipe)
  }

  private[spatial] def reduceND[T:Bits](
    domain: Seq[Counter],
    reg:    Reg[T],
    map:    List[Index] => T,
    reduce: (T,T) => T,
    style:  Option[ControlStyle]
  )(implicit ctx: SrcCtx): Controller = {

    val rV = (fresh[T], fresh[T])
    val iters = List.tabulate(domain.length){_ => fresh[Index] }

    val mBlk  = stageBlock{ map(wrap(iters)).s }
    val ldBlk = stageBlock{ reg.value.s }
    val rBlk  = stageBlock{ reduce(wrap(rV._1),wrap(rV._2)).s }
    val stBlk = stageLambda(rBlk.result){ unwrap( reg := wrap(rBlk.result) ) }

    val cchain = CounterChain(domain: _*)

    val effects = mBlk.summary andAlso ldBlk.summary andAlso rBlk.summary andAlso stBlk.summary
    val pipe = stageEffectful(OpReduce[T](cchain.s, reg.s, mBlk, ldBlk, rBlk, stBlk, rV, iters), effects)(ctx)
    style.foreach{s => styleOf(pipe) = s}
    Controller(pipe)
  }

  private[spatial] def mem_reduceND[T:Bits,C[T]](domain: Seq[Counter], accum: C[T], map: List[Index] => C[T], reduce: (T,T) => T, style: Option[ControlStyle])(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Staged[C[T]]): Controller = {
    val rV = (fresh[T], fresh[T])
    val itersMap = List.tabulate(domain.length){_ => fresh[Index] }

    val ctrsRed = mem.iterators(accum)
    val itersRed = ctrsRed.map{_ => fresh[Index] }

    val mBlk  = stageBlock{ map(wrap(itersMap)).s }
    val rBlk  = stageBlock{ reduce(wrap(rV._1), wrap(rV._2)).s }
    val ldResBlk = stageLambda(mBlk.result){ mem.load(wrap(mBlk.result), wrap(itersRed), true).s }
    val ldAccBlk = stageBlock{ mem.load(accum, wrap(itersRed), true).s }
    val stAccBlk = stageLambda(rBlk.result){ mem.store(accum, wrap(itersRed), wrap(rBlk.result), true).s }

    val cchainMap = CounterChain(domain: _*)
    val cchainRed = CounterChain(ctrsRed: _*)

    val effects = mBlk.summary andAlso rBlk.summary andAlso ldResBlk.summary andAlso ldAccBlk.summary andAlso stAccBlk.summary
    val node = stageEffectful(OpMemReduce[T,C](cchainMap.s,cchainRed.s,accum.s,mBlk,ldResBlk,ldAccBlk,rBlk,stAccBlk,rV,itersMap,itersRed), effects)(ctx)
    Controller(node)
  }

  /** IR Nodes **/
  case class Hwblock(func: Block[Void]) extends Op[Controller] {
    def mirror(f:Tx) = op_accel(f(func))
    override def freqs = cold(func)
  }

  case class UnitPipe(func: Block[Void]) extends Op[Controller] {
    def mirror(f:Tx) = op_unit_pipe(f(func))
    override def freqs = cold(func)
  }

  case class OpForeach(cchain: Exp[CounterChain], func: Block[Void], iters: List[Bound[Index]]) extends Op[Controller] {
    def mirror(f:Tx) = op_foreach(f(cchain), f(func), iters)

    override def inputs = syms(cchain) ++ syms(func)
    override def freqs  = cold(func) ++ normal(cchain)
    override def binds  = super.binds ++ iters
  }

  case class OpReduce[T:Bits](
    cchain: Exp[CounterChain],
    accum:  Exp[Reg[T]],
    map:    Block[T],
    load:   Block[T],
    reduce: Block[T],
    store:  Block[Void],
    rV:     (Bound[T],Bound[T]),
    iters:  List[Bound[Index]]
  ) extends Op[Controller] {

    def mirror(f:Tx) = op_reduce(f(cchain), f(accum), f(map), f(load), f(reduce), f(store), rV, iters)

    override def inputs = syms(cchain) ++ syms(map) ++ syms(reduce) ++ syms(accum) ++ syms(load) ++ syms(store)
    override def freqs  = cold(map) ++ cold(reduce) ++ normal(cchain) ++ normal(accum) ++ hot(load) ++ hot(store)
    override def binds  = super.binds ++ iters ++ List(rV._1, rV._2)
    override def tunnels = syms(accum)
  }

  case class OpMemReduce[T:Bits,C[T]](
    cchainMap: Exp[CounterChain],
    cchainRed: Exp[CounterChain],
    accum:     Exp[C[T]],
    map:       Block[C[T]],
    loadRes:   Block[T],
    loadAcc:   Block[T],
    reduce:    Block[T],
    storeAcc:  Block[Void],
    rV:        (Bound[T], Bound[T]),
    itersMap:  Seq[Bound[Index]],
    itersRed:  Seq[Bound[Index]]
  )(implicit mem: Mem[T,C], mC: Staged[C[T]]) extends Op[Controller] {

    def mirror(f:Tx) = op_mem_reduce(f(cchainMap),f(cchainRed),f(accum),f(map),f(loadRes),f(loadAcc),f(reduce),
                                     f(storeAcc), rV, itersMap, itersRed)

    override def inputs = syms(cchainMap) ++ syms(cchainRed) ++ syms(accum) ++ syms(map) ++ syms(reduce)
    override def freqs = cold(map) ++ cold(reduce) ++ normal(cchainMap) ++ normal(cchainRed) ++ normal(accum)
    override def binds = super.binds ++ itersMap ++ itersRed ++ List(rV._1, rV._2)
    override def tunnels = syms(accum)
  }


  /** Constructors **/
  def op_accel(func: => Exp[Void])(implicit ctx: SrcCtx): Sym[Controller] = {
    val fBlk = stageBlock{ func }
    val effects = fBlk.summary
    stageEffectful( Hwblock(fBlk), effects)(ctx)
  }

  def op_unit_pipe(func: => Exp[Void])(implicit ctx: SrcCtx): Sym[Controller] = {
    val fBlk = stageBlock{ func }
    val effects = fBlk.summary
    stageEffectful( UnitPipe(fBlk), effects)(ctx)
  }
  def op_foreach(domain: Exp[CounterChain], func: => Exp[Void], iters: List[Bound[Index]])(implicit ctx: SrcCtx): Sym[Controller] = {
    val fBlk = stageBlock{ func }
    val effects = fBlk.summary.star
    stageEffectful( OpForeach(domain, fBlk, iters), effects)(ctx)
  }

  def op_reduce[T:Bits](
    cchain: Exp[CounterChain],
    reg:    Exp[Reg[T]],
    map:    => Exp[T],
    load:   => Exp[T],
    reduce: => Exp[T],
    store:  => Exp[Void],
    rV:     (Bound[T],Bound[T]),
    iters:  List[Bound[Index]]
  )(implicit ctx: SrcCtx): Sym[Controller] = {

    val mBlk  = stageBlock{ map }
    val ldBlk = stageBlock{ load }
    val rBlk  = stageBlock{ reduce }
    val stBlk = stageLambda(rBlk.result){ store }

    val effects = mBlk.summary andAlso ldBlk.summary andAlso rBlk.summary andAlso stBlk.summary
    stageEffectful( OpReduce[T](cchain, reg, mBlk, ldBlk, rBlk, stBlk, rV, iters), effects)(ctx)
  }
  def op_mem_reduce[T:Bits,C[T]](
    cchainMap: Exp[CounterChain],
    cchainRed: Exp[CounterChain],
    accum:     Exp[C[T]],
    map:       => Exp[C[T]],
    loadRes:   => Exp[T],
    loadAcc:   => Exp[T],
    reduce:    => Exp[T],
    storeAcc:  => Exp[Void],
    rV:        (Bound[T], Bound[T]),
    itersMap:  Seq[Bound[Index]],
    itersRed:  Seq[Bound[Index]]
  )(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Staged[C[T]]): Sym[Controller] = {

    val mBlk = stageBlock{ map }
    val ldResBlk = stageLambda(mBlk.result){ loadRes }
    val ldAccBlk = stageBlock{ loadAcc }
    val rBlk = stageBlock{ reduce }
    val stBlk = stageLambda(rBlk.result){ storeAcc }

    val effects = mBlk.summary andAlso ldResBlk.summary andAlso ldAccBlk.summary andAlso rBlk.summary andAlso stBlk.summary
    stageEffectful( OpMemReduce[T,C](cchainMap, cchainRed, accum, mBlk, ldResBlk, ldAccBlk, rBlk, stBlk, rV, itersMap, itersRed), effects)(ctx)
  }


}

