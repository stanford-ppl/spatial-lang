package spatial.lang

import forge._
import spatial.nodes._

case class StreamIn[T:Type:Bits](s: Exp[StreamIn[T]]) extends Template[StreamIn[T]] {
  @api def value(): T = this.value(true)
  @api def value(en: Bit): T = wrap(StreamIn.read(s, en.s)) // Needed?
}
case class StreamOut[T:Type:Bits](s: Exp[StreamOut[T]]) extends Template[StreamOut[T]] {
  @api def :=(value: T): MUnit = this := (value, true)
  @api def :=(value: T, en: Bit): MUnit = MUnit(StreamOut.write(s, value.s, en.s))
}
case class BufferedOut[T:Type:Bits](s: Exp[BufferedOut[T]]) extends Template[BufferedOut[T]] {
  @api def update(row: Index, col: Index, data: T): MUnit = MUnit(BufferedOut.write(s, data.s, Seq(row.s,col.s), Bit.const(true)))
}

private object Streams {
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

object StreamIn {
  import Streams._

  implicit def streamInType[T:Type:Bits]: Type[StreamIn[T]] = StreamInType(typ[T])

  @api def apply[T:Type:Bits](bus: Bus): StreamIn[T] = {
    bus_check[T](bus)
    StreamIn(alloc[T](bus))
  }

  /** Constructors **/
  @internal def alloc[T:Type:Bits](bus: Bus): Exp[StreamIn[T]] = {
    stageMutable(StreamInNew[T](bus))(ctx)
  }
  @internal def read[T:Type:Bits](stream: Exp[StreamIn[T]], en: Exp[Bit]) = {
    stageWrite(stream)(StreamRead(stream, en))(ctx)
  }
  @internal def par_read[T:Type:Bits](stream: Exp[StreamIn[T]], ens: Seq[Exp[Bit]]) = {
    implicit val vT = VectorN.typeFromLen[T](ens.length)
    stageWrite(stream)( ParStreamRead(stream, ens) )(ctx)
  }
}

object StreamOut {
  import Streams._

  implicit def streamOutType[T:Type:Bits]: Type[StreamOut[T]] = StreamOutType(typ[T])

  @api def apply[T:Type:Bits](bus: Bus): StreamOut[T] = {
    bus_check[T](bus)
    StreamOut(alloc[T](bus))
  }

  /** Constructors **/
  @internal def alloc[T:Type:Bits](bus: Bus): Exp[StreamOut[T]] = {
    stageMutable(StreamOutNew[T](bus))(ctx)
  }
  @internal def write[T:Type:Bits](stream: Exp[StreamOut[T]], data: Exp[T], en: Exp[Bit]) = {
    stageWrite(stream)(StreamWrite(stream, data, en))(ctx)
  }
  @internal def par_write[T:Type:Bits](stream: Exp[StreamOut[T]], data: Seq[Exp[T]], ens: Seq[Exp[Bit]]) = {
    stageWrite(stream)( ParStreamWrite(stream, data, ens) )(ctx)
  }
}

object BufferedOut {
  import Streams._

  implicit def bufferedOutType[T:Type:Bits]: Type[BufferedOut[T]] = BufferedOutType(typ[T])

  // TODO: Should also be able to specify # of rows and columns
  @api def apply[T:Type:Bits](bus: Bus): BufferedOut[T] = {   // (rows: Index, cols: Index)
    bus_check[T](bus)
    BufferedOut(alloc[T](Seq(lift(240).s,lift(320).s),bus))
  }

  /** Constructors **/
  @internal def alloc[T:Type:Bits](dims: Seq[Exp[Index]], bus: Bus): Exp[BufferedOut[T]] = {
    stageMutable(BufferedOutNew[T](dims, bus))(ctx)
  }
  @internal def write[T:Type:Bits](buffer: Exp[BufferedOut[T]], data: Exp[T], is: Seq[Exp[Index]], en: Exp[Bit]) = {
    stageWrite(buffer)(BufferedOutWrite(buffer,data,is,en))(ctx)
  }
}


trait StreamApi {
  /** Static methods **/
  @api implicit def readStream[T](stream: StreamIn[T]): T = stream.value
}

/*trait StreamTransfersApi extends StreamTransfersExp { this: SpatialApi =>

  case object VGABus extends Bus { def length = 18 }
  @struct case class VGAData(b: UInt5, g: UInt6, r: UInt5)
  @struct case class VGAStream(data: VGAData, start: Bit, end: Bit)

  private[spatial] def expandBufferedOut[T:Type:Bits](buffer: Exp[BufferedOut[T]])(implicit ctx: SrcCtx) = buffer match {
    case Def(BufferedOutNew(dims,bus)) =>
      val sram = () => SRAM[T](wrap(dims(0)),wrap(dims(1))).s
      val stream = bus match {
        case spatial.targets.DE1.VGA => () => StreamOut[VGAStream](VGABus).s
        case VGABus                  => () => StreamOut[VGAStream](VGABus).s
        case _                       => () => StreamOut[T](bus).s
      }
      (sram,stream)

    case _ => throw new Exception(u"${buffer.ctx} Cannot expand buffer ${str(buffer)}")
  }

  /*private[spatial] def expandBufferedOutWrite[T:Type:Bits](mem: Exp[SRAM2[T]], stream: Exp[StreamOut[_]]) = {

  }*/
}*/