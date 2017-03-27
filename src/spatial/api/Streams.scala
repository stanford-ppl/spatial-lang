package spatial.api
import argon.core.Staging
import spatial.SpatialExp

trait StreamApi extends StreamExp {
  this: SpatialExp =>

  implicit def readStream[T](stream: StreamIn[T])(implicit ctx: SrcCtx): T = stream.value

}

trait StreamExp extends Staging with PinExp {
  this: SpatialExp =>

  case class StreamIn[T:Meta:Bits](s: Exp[StreamIn[T]]) extends Template[StreamIn[T]] {
    def value()(implicit ctx: SrcCtx): T = this.value(true)
    def value(en: Bool)(implicit ctx: SrcCtx): T = wrap(stream_read(s, en.s)) // Needed?
  }

  case class StreamOut[T:Meta:Bits](s: Exp[StreamOut[T]]) extends Template[StreamOut[T]] {
    def :=(value: T)(implicit ctx: SrcCtx): Void = this := (value, true)
    def :=(value: T, en: Bool)(implicit ctx: SrcCtx): Void = Void(stream_write(s, value.s, en.s))
  }

  /** Static methods **/
  object StreamIn {
    def apply[T:Meta:Bits](bus: Bus)(implicit ctx: SrcCtx): StreamIn[T] = {
      bus_check[T](bus)
      StreamIn(stream_in[T](bus))
    }
  }

  object StreamOut {
    def apply[T:Meta:Bits](bus: Bus)(implicit ctx: SrcCtx): StreamOut[T] = {
      bus_check[T](bus)
      StreamOut(stream_out[T](bus))
    }
  }

  /** Type classes **/
  // --- Staged
  case class StreamInType[T:Bits](child: Meta[T]) extends Meta[StreamIn[T]] {
    override def wrapped(x: Exp[StreamIn[T]]) = StreamIn(x)(child, bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[StreamIn[T]]
    override def isPrimitive = false
  }
  implicit def streamInType[T:Meta:Bits]: Meta[StreamIn[T]] = StreamInType(meta[T])

  case class StreamOutType[T:Bits](child: Meta[T]) extends Meta[StreamOut[T]] {
    override def wrapped(x: Exp[StreamOut[T]]) = StreamOut(x)(child, bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[StreamOut[T]]
    override def isPrimitive = false
  }
  implicit def streamOutType[T:Meta:Bits]: Meta[StreamOut[T]] = StreamOutType(meta[T])


  /** IR Nodes **/
  case class StreamInNew[T:Type:Bits](bus: Bus) extends Op[StreamIn[T]] {
    def mirror(f: Tx) = stream_in[T](bus)
    val mT = typ[T]
  }

  case class StreamOutNew[T:Type:Bits](bus: Bus) extends Op[StreamOut[T]] {
    def mirror(f: Tx) = stream_out[T](bus)
    val mT = typ[T]
  }

  case class StreamRead[T:Type:Bits](stream: Exp[StreamIn[T]], en: Exp[Bool]) extends EnabledOp[T](en) {
    def mirror(f:Tx) = stream_read(f(stream), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  case class StreamWrite[T:Type:Bits](stream: Exp[StreamOut[T]], data: Exp[T], en: Exp[Bool]) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = stream_write(f(stream), f(data), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }


  /** Constructors **/
  private def stream_in[T:Type:Bits](bus: Bus)(implicit ctx: SrcCtx): Exp[StreamIn[T]] = {
    stageMutable(StreamInNew[T](bus))(ctx)
  }

  private def stream_out[T:Type:Bits](bus: Bus)(implicit ctx: SrcCtx): Exp[StreamOut[T]] = {
    stageMutable(StreamOutNew[T](bus))(ctx)
  }

  private def stream_read[T:Type:Bits](stream: Exp[StreamIn[T]], en: Exp[Bool])(implicit ctx: SrcCtx) = {
    stageWrite(stream)(StreamRead(stream, en))(ctx)
  }

  private def stream_write[T:Type:Bits](stream: Exp[StreamOut[T]], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx) = {
    stageWrite(stream)(StreamWrite(stream, data, en))(ctx)
  }


  /** Internals **/
  private def bus_check[T:Type:Bits](bus: Bus)(implicit ctx: SrcCtx): Unit = {
    if (bits[T].length < bus.length) {
      warn(ctx, s"Bus length is greater than size of StreamIn type - will use first ${bits[T].length} bits in the bus")
      warn(ctx)
    }
    else if (bits[T].length > bus.length) {
      error(ctx, s"Bus length is smaller than size of StreamIn type")
      error(ctx)
    }
  }

}