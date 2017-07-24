package spatial.lang.static

import forge._

trait StreamApi { this: SpatialApi =>
  /** Static methods **/
  @api implicit def readStream[T](stream: StreamIn[T]): T = stream.value()
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
