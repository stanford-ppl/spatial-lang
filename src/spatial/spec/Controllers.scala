package spatial.spec
import argon.ops._

// MemReduce and views
//   If view is staged, requires either direct access to its target via a def or its own load/store defs
//   If view is unstaged, requires unwrapping prior to use in result of Blocks / use as dependencies
//   However, if view is staged, have mutable sharing..

trait ControllerOps extends RegOps with SRAMOps with VoidOps with CounterOps { this: SpatialOps =>

  object MemReduce {
    /** 1 dimensional memory reduction with explicit accumulator **/
    def apply[T:Bits,C[T]](accum: C[T])(domain1D: Counter)(map: Index => C[T])(reduce: (T,T) => T)(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Staged[C[T]]): C[T] = {
      mem_reduceND(List(domain1D), accum, {x: List[Index] => map(x.head)}, reduce)
      accum
    }
  }

  object Reduce {
    /** 1 dimensional reduction with implicit accumulator **/
    def apply[A,T:Bits](zero: A)(domain1D: Counter)(map: Index => T)(reduce: (T,T) => T)(implicit ctx: SrcCtx, l: Lift[A,T]): Reg[T] = {
      val accum = Reg[T](lift(zero))
      reduceND(List(domain1D), accum, {x: List[Index] => map(x.head)}, reduce)
      accum
    }

    /** 1 dimensional reduction with explicit accumulator **/
    def apply[T:Bits](accum: Reg[T])(domain1D: Counter)(map: Index => T)(reduce: (T,T) => T)(implicit ctx: SrcCtx): Reg[T] = {
      reduceND(List(domain1D), accum, {x: List[Index] => map(x.head)}, reduce)
      accum
    }
  }
  object Foreach {
    /** 1 dimensional parallel foreach **/
    def apply(domain1D: Counter)(func: Index => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1D), {x: List[Index] => func(x.head) })
    }
    /** 2 dimensional parallel foreach **/
    def apply(domain1: Counter, domain2: Counter)(func: (Index,Index) => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1,domain2), {x: List[Index] => func(x(0),x(1)) })
    }
    /** 3 dimensional parallel foreach **/
    def apply(domain1: Counter, domain2: Counter, domain3: Counter)(func: (Index,Index,Index) => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1,domain2,domain3), {x: List[Index] => func(x(0),x(1),x(2)) })
    }
    /** N dimensional parallel foreach **/
    def apply(domains: Counter*)(func: List[Index] => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(domains.toList, func)
    }
  }

  object Pipe {
    def apply(func: => Void)(implicit ctx: SrcCtx): Void = unit_pipe(func)

    def apply(domain1D: Counter)(func: Index => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1D), {x: List[Index] => func(x.head) })
    }
    def apply(domain1: Counter, domain2: Counter)(func: (Index,Index) => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1,domain2), {x: List[Index] => func(x(0),x(1)) })
    }
    def apply(domain1: Counter, domain2: Counter, domain3: Counter)(func: (Index,Index,Index) => Void)(implicit ctx: SrcCtx): Void = {
      foreachND(List(domain1,domain2,domain3), {x: List[Index] => func(x(0),x(1),x(2)) })
    }
  }

  object Accel {
    def apply(func: => Void)(implicit ctx: SrcCtx): Void = accel_blk(func)
  }

  private[spatial] def accel_blk(func: => Void)(implicit ctx: SrcCtx): Void
  private[spatial] def unit_pipe(func: => Void)(implicit ctx: SrcCtx): Void
  private[spatial] def foreachND(domain: Seq[Counter], func: List[Index] => Void)(implicit ctx: SrcCtx): Void
  private[spatial] def reduceND[T:Bits](domain: Seq[Counter], reg: Reg[T], map: List[Index] => T, reduce: (T,T) => T)(implicit ctx: SrcCtx): Void
  private[spatial] def mem_reduceND[T:Bits,C[T]](domain: Seq[Counter], accum: C[T], map: List[Index] => C[T], reduce: (T,T) => T)(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Staged[C[T]]): Void
}
trait ControllerApi extends ControllerOps with RegApi with SRAMApi with CounterApi { this: SpatialApi => }

trait ControllerExp extends ControllerOps with RegExp with SRAMExp with CounterExp { this: SpatialExp =>
  /** API **/
  private[spatial] def accel_blk(func: => Void)(implicit ctx: SrcCtx): Void = {
    val fFunc = () => unwrap(func)
    Void(op_accel(fFunc()))
  }

  private[spatial] def unit_pipe(func: => Void)(implicit ctx: SrcCtx): Void = {
    val fFunc = () => unwrap(func)
    Void(op_unit_pipe(fFunc()))
  }

  private[spatial] def foreachND(domain: Seq[Counter], func: List[Index] => Void)(implicit ctx: SrcCtx): Void = {
    val iters = List.tabulate(domain.length){_ => fresh[Index] }

    val fFunc = () => unwrap( func(wrap(iters)) )
    val cchain = CounterChain(domain: _*)

    Void(op_foreach(cchain.s, fFunc(), iters))
  }

  private[spatial] def reduceND[T:Bits](domain: Seq[Counter], reg: Reg[T], map: List[Index] => T, reduce: (T,T) => T)(implicit ctx: SrcCtx): Void = {
    val rV = (fresh[T], fresh[T])
    val iters = List.tabulate(domain.length){_ => fresh[Index] }

    val mBlk  = stageBlock{ map(wrap(iters)).s }
    val ldBlk = stageBlock{ reg.value.s }
    val rBlk  = stageBlock{ reduce(wrap(rV._1),wrap(rV._2)).s }
    val stBlk = stageLambda(rBlk.result){ unwrap( reg := wrap(rBlk.result) ) }

    val cchain = CounterChain(domain: _*)

    val effects = mBlk.summary andAlso ldBlk.summary andAlso rBlk.summary andAlso stBlk.summary
    val node = stageEffectful(OpReduce[T](cchain.s, reg.s, mBlk, ldBlk, rBlk, stBlk, rV, iters), effects)(ctx)
    Void(node)
  }

  private[spatial] def mem_reduceND[T:Bits,C[T]](domain: Seq[Counter], accum: C[T], map: List[Index] => C[T], reduce: (T,T) => T)(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Staged[C[T]]): Void = {
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
    Void(node)
  }

  /** IR Nodes **/
  case class Hwblock(func: Block[Void]) extends Op[Void] {
    def mirror(f:Tx) = op_accel(f(func))
    override def freqs = cold(func)
  }

  case class UnitPipe(func: Block[Void]) extends Op[Void] {
    def mirror(f:Tx) = op_unit_pipe(f(func))
    override def freqs = cold(func)
  }

  case class OpForeach(cchain: Sym[CounterChain], func: Block[Void], iters: List[Sym[Index]]) extends Op[Void] {
    def mirror(f:Tx) = op_foreach(f(cchain), f(func), f(iters))

    override def inputs = syms(cchain) ++ syms(func)
    override def freqs  = cold(func) ++ normal(cchain)
    override def binds  = super.binds ++ iters
  }

  case class OpReduce[T:Bits](
    cchain: Sym[CounterChain],
    accum:  Sym[Reg[T]],
    map:    Block[T],
    load:   Block[T],
    reduce: Block[T],
    store:  Lambda[Void],
    rV:     (Sym[T],Sym[T]),
    iters:  List[Sym[Index]]
  ) extends Op[Void] {

    def mirror(f:Tx) = op_reduce(f(cchain), f(accum), f(map), f(load), f(reduce), f(store), rV, iters)

    override def inputs = syms(cchain) ++ syms(map) ++ syms(reduce) ++ syms(accum) ++ syms(load) ++ syms(store)
    override def freqs  = cold(map) ++ cold(reduce) ++ normal(cchain) ++ normal(accum) ++ hot(load) ++ hot(store)
    override def binds  = super.binds ++ iters ++ List(rV._1, rV._2)
    override def tunnels = List(accum)
  }

  case class OpMemReduce[T:Bits,C[T]](
    cchainMap: Sym[CounterChain],
    cchainRed: Sym[CounterChain],
    accum:     Sym[C[T]],
    map:       Block[C[T]],
    loadRes:   Lambda[T],
    loadAcc:   Block[T],
    reduce:    Block[T],
    storeAcc:  Lambda[Void],
    rV:        (Sym[T], Sym[T]),
    itersMap:  Seq[Sym[Index]],
    itersRed:  Seq[Sym[Index]]
  )(implicit mem: Mem[T,C], mC: Staged[C[T]]) extends Op[Void] {

    def mirror(f:Tx) = op_mem_reduce(f(cchainMap),f(cchainRed),f(accum),f(map),f(loadRes),f(loadAcc),f(reduce),
                                     f(storeAcc), rV, itersMap, itersRed)

    override def inputs = syms(cchainMap) ++ syms(cchainRed) ++ syms(accum) ++ syms(map) ++ syms(reduce)
    override def freqs = cold(map) ++ cold(reduce) ++ normal(cchainMap) ++ normal(cchainRed) ++ normal(accum)
    override def binds = super.binds ++ itersMap ++ itersRed ++ List(rV._1, rV._2)
    override def tunnels = List(accum)
  }


  /** Constructors **/
  def op_accel(func: => Sym[Void])(implicit ctx: SrcCtx): Sym[Void] = {
    val fBlk = stageBlock{ func }
    val effects = fBlk.summary
    stageEffectful( Hwblock(fBlk), effects)(ctx)
  }

  def op_unit_pipe(func: => Sym[Void])(implicit ctx: SrcCtx): Sym[Void] = {
    val fBlk = stageBlock{ func }
    val effects = fBlk.summary
    stageEffectful( UnitPipe(fBlk), effects)(ctx)
  }
  def op_foreach(domain: Sym[CounterChain], func: => Sym[Void], iters: List[Sym[Index]])(implicit ctx: SrcCtx): Sym[Void] = {
    val fBlk = stageBlock{ func }
    val effects = fBlk.summary.star
    stageEffectful( OpForeach(domain, fBlk, iters), effects)(ctx)
  }

  def op_reduce[T:Bits](
    cchain: Sym[CounterChain],
    reg:    Sym[Reg[T]],
    map:    => Sym[T],
    load:   => Sym[T],
    reduce: => Sym[T],
    store:  => Sym[Void],
    rV:     (Sym[T],Sym[T]),
    iters:  List[Sym[Index]]
  )(implicit ctx: SrcCtx): Sym[Void] = {

    val mBlk  = stageBlock{ map }
    val ldBlk = stageBlock{ load }
    val rBlk  = stageBlock{ reduce }
    val stBlk = stageLambda(rBlk.result){ store }

    val effects = mBlk.summary andAlso ldBlk.summary andAlso rBlk.summary andAlso stBlk.summary
    stageEffectful( OpReduce[T](cchain, reg, mBlk, ldBlk, rBlk, stBlk, rV, iters), effects)(ctx)
  }
  def op_mem_reduce[T:Bits,C[T]](
    cchainMap: Sym[CounterChain],
    cchainRed: Sym[CounterChain],
    accum:     Sym[C[T]],
    map:       => Sym[C[T]],
    loadRes:   => Sym[T],
    loadAcc:   => Sym[T],
    reduce:    => Sym[T],
    storeAcc:  => Sym[Void],
    rV:        (Sym[T], Sym[T]),
    itersMap:  Seq[Sym[Index]],
    itersRed:  Seq[Sym[Index]]
  )(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Staged[C[T]]): Sym[Void] = {

    val mBlk = stageBlock{ map }
    val ldResBlk = stageLambda(mBlk.result){ loadRes }
    val ldAccBlk = stageBlock{ loadAcc }
    val rBlk = stageBlock{ reduce }
    val stBlk = stageLambda(rBlk.result){ storeAcc }

    val effects = mBlk.summary andAlso ldResBlk.summary andAlso ldAccBlk.summary andAlso rBlk.summary andAlso stBlk.summary
    stageEffectful( OpMemReduce[T,C](cchainMap, cchainRed, accum, mBlk, ldResBlk, ldAccBlk, rBlk, stBlk, rV, itersMap, itersRed), effects)(ctx)
  }


}

