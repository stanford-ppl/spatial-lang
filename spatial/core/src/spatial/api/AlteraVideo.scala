package spatial.api

import spatial._
import forge._

// TODO: Is this still used by anything? If not, delete
trait AlteraVideoApi extends AlteraVideoExp {
  this: SpatialApi =>

  @api def AXI_Master_Slave(): AXI_Master_Slave = AXI_Master_Slave(axi_ms_alloc())

  @api def Decoder_Template[T:Type:Bits](popFrom: StreamIn[T], pushTo: FIFO[T]): Decoder_Template[T] = {
    Decoder_Template(decoder_alloc[T](popFrom.s.asInstanceOf[Exp[T]], pushTo.s.asInstanceOf[Exp[T]]))
  }

  @api def DMA_Template[T:Type:Bits](popFrom: FIFO[T], loadIn: SRAM1[T]): DMA_Template[T] = {
    DMA_Template(dma_alloc[T](popFrom.s.asInstanceOf[Exp[T]], loadIn.s.asInstanceOf[Exp[T]]))
  }

  @api def Decoder[T:Type:Bits,C[T]](popFrom: StreamIn[T], pushTo: FIFO[T]): Void = {
    Pipe { 
      Decoder_Template(popFrom, pushTo)
      popFrom.value()
      ()
      // pushTo.enq(popFrom.deq())
    }
  }

  @api def DMA[T:Type:Bits](popFrom: FIFO[T], loadIn: SRAM1[T] /*frameRdy:  StreamOut[T]*/): Void = {
    Pipe {
      DMA_Template(popFrom, loadIn)
      Foreach(64 by 1){ i =>
        loadIn(i) = popFrom.deq()
      }
      // Pipe {
      //   frameRdy.push(1.to[T])
      // }
      ()
    }
  }

}


trait AlteraVideoExp { this: SpatialExp =>

  /** Infix methods **/
  // TODO: Do these need to be staged types?
  case class AXI_Master_Slave(s: Exp[AXI_Master_Slave]) extends Template[AXI_Master_Slave]
  case class Decoder_Template[T:Meta:Bits](s: Exp[Decoder_Template[T]]) extends Template[Decoder_Template[T]]
  case class DMA_Template[T:Meta:Bits](s: Exp[DMA_Template[T]]) extends Template[DMA_Template[T]]

  /** Staged Type **/
  object AXIMasterSlaveType extends Meta[AXI_Master_Slave] {
    override def wrapped(x: Exp[AXI_Master_Slave]) = AXI_Master_Slave(x)
    override def stagedClass = classOf[AXI_Master_Slave]
    override def isPrimitive = false // ???
  }
  implicit def aXIMasterSlaveType: Meta[AXI_Master_Slave] = AXIMasterSlaveType

  case class DecoderTemplateType[T:Bits](child: Meta[T]) extends Meta[Decoder_Template[T]] {
    override def wrapped(x: Exp[Decoder_Template[T]]) = Decoder_Template(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[Decoder_Template[T]]
    override def isPrimitive = true // ???
  }
  implicit def decoderTemplateType[T:Meta:Bits]: Meta[Decoder_Template[T]] = DecoderTemplateType(meta[T])

  case class DMATemplateType[T:Bits](child: Meta[T]) extends Meta[DMA_Template[T]] {
    override def wrapped(x: Exp[DMA_Template[T]]) = DMA_Template[T](x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[DMA_Template[T]]
    override def isPrimitive = true // ???
  }
  implicit def dMATemplateType[T:Meta:Bits]: Meta[DMA_Template[T]] = DMATemplateType(meta[T])


  /** IR Nodes **/
  case class AxiMSNew() extends Op[AXI_Master_Slave] {
    def mirror(f:Tx) = axi_ms_alloc()
  }
  case class DecoderTemplateNew[T:Type:Bits](popFrom: Exp[T], pushTo: Exp[T]) extends Op[Decoder_Template[T]] {
    def mirror(f:Tx) = decoder_alloc[T](f(popFrom), f(pushTo))
    val mT = typ[T]
    val bT = bits[T]

  }
  case class DMATemplateNew[T:Type:Bits](popFrom: Exp[T], loadIn: Exp[T]) extends Op[DMA_Template[T]] {
    def mirror(f:Tx) = dma_alloc[T](f(popFrom), f(loadIn))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  @internal def axi_ms_alloc(): Sym[AXI_Master_Slave] = {
    stageSimple( AxiMSNew() )(ctx)
  }
  @internal def decoder_alloc[T:Type:Bits](popFrom: Exp[T], pushTo: Exp[T]): Sym[Decoder_Template[T]] = {
    stageSimple( DecoderTemplateNew[T](popFrom, pushTo) )(ctx)
  }
  @internal def dma_alloc[T:Type:Bits](popFrom: Exp[T], loadIn: Exp[T]): Sym[DMA_Template[T]] = {
    stageSimple( DMATemplateNew[T](popFrom, loadIn) )(ctx)
  }

  /** Internal methods **/

  // private[spatial] def source(x: Exp[Reg]): Exp = x match {
  //   case Op(AxiMSNew())    => 
  // }

}

