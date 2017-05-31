package spatial.api

import spatial.{SpatialApi, SpatialExp}
import forge._

trait StreamApi extends StreamExp { this: SpatialApi =>

  /** Static methods **/
  @api def StreamIn[T:Meta:Bits](bus: Bus): StreamIn[T] = {
    bus_check[T](bus)
    StreamIn(stream_in[T](bus))
  }

  @api def StreamOut[T:Meta:Bits](bus: Bus): StreamOut[T] = {
    bus_check[T](bus)
    StreamOut(stream_out[T](bus))
  }

  @api def BufferedOut[T:Meta:Bits](bus: Bus): BufferedOut[T] = { // (rows: Index, cols: Index)
    bus_check[T](bus)
    BufferedOut(buffered_out[T](Seq(lift(240).s,lift(320).s),bus))
  }

  @api implicit def readStream[T](stream: StreamIn[T]): T = stream.value
}

trait StreamExp { this: SpatialExp =>

  case class StreamIn[T:Meta:Bits](s: Exp[StreamIn[T]]) extends Template[StreamIn[T]] {
    @api def value(): T = this.value(true)
    @api def value(en: Bool): T = wrap(stream_read(s, en.s)) // Needed?
  }

  case class StreamOut[T:Meta:Bits](s: Exp[StreamOut[T]]) extends Template[StreamOut[T]] {
    @api def :=(value: T): Void = this := (value, true)
    @api def :=(value: T, en: Bool): Void = Void(stream_write(s, value.s, en.s))
  }

  case class BufferedOut[T:Meta:Bits](s: Exp[BufferedOut[T]]) extends Template[BufferedOut[T]] {
    @api def update(row: Index, col: Index, data: T): Void = Void(buffered_out_write(s, data.s, Seq(row.s,col.s), bool(true)))
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

  case class BufferedOutType[T:Bits](child: Meta[T]) extends Meta[BufferedOut[T]] {
    override def wrapped(x: Exp[BufferedOut[T]]) = BufferedOut(x)(child, bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[BufferedOut[T]]
    override def isPrimitive = false
  }
  implicit def bufferedOutType[T:Meta:Bits]: Meta[BufferedOut[T]] = BufferedOutType(meta[T])


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

  case class BufferedOutNew[T:Type:Bits](dims: Seq[Exp[Index]], bus: Bus) extends Op[BufferedOut[T]] {
    def mirror(f:Tx) = buffered_out[T](f(dims), bus)
    val mT = typ[T]
  }

  case class BufferedOutWrite[T:Type:Bits](buffer: Exp[BufferedOut[T]], data: Exp[T], is: Seq[Exp[Index]], en: Exp[Bool]) extends EnabledOp[Void] {
    def mirror(f:Tx) = buffered_out_write[T](f(buffer),f(data),f(is),f(en))
    val mT = typ[T]
    val bT = bits[T]
  }


  /** Constructors **/
  @internal def stream_in[T:Type:Bits](bus: Bus): Exp[StreamIn[T]] = {
    stageMutable(StreamInNew[T](bus))(ctx)
  }

  @internal def stream_out[T:Type:Bits](bus: Bus): Exp[StreamOut[T]] = {
    stageMutable(StreamOutNew[T](bus))(ctx)
  }

  @internal def stream_read[T:Type:Bits](stream: Exp[StreamIn[T]], en: Exp[Bool]) = {
    stageWrite(stream)(StreamRead(stream, en))(ctx)
  }

  @internal def stream_write[T:Type:Bits](stream: Exp[StreamOut[T]], data: Exp[T], en: Exp[Bool]) = {
    stageWrite(stream)(StreamWrite(stream, data, en))(ctx)
  }

  @internal def buffered_out[T:Type:Bits](dims: Seq[Exp[Index]], bus: Bus): Exp[BufferedOut[T]] = {
    stageMutable(BufferedOutNew[T](dims, bus))(ctx)
  }

  @internal def buffered_out_write[T:Type:Bits](buffer: Exp[BufferedOut[T]], data: Exp[T], is: Seq[Exp[Index]], en: Exp[Bool]) = {
    stageWrite(buffer)(BufferedOutWrite(buffer,data,is,en))(ctx)
  }

  /** Internals **/
  @internal def bus_check[T:Type:Bits](bus: Bus): Unit = {
    if (bits[T].length < bus.length) {
      warn(ctx, s"Bus length is greater than size of StreamIn type - will use the first ${bits[T].length} bits in the bus")
      warn(ctx)
    }
    else if (bits[T].length > bus.length) {
      warn(ctx, s"Bus length is smaller than size of StreamIn type - will set the first ${bus.length} bits in the stream")
      warn(ctx)
    }
  }

}