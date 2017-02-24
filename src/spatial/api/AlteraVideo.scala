package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait AlteraVideoApi extends AlteraVideoExp with BurstTransferExp with ControllerApi with FIFOApi with RangeApi with PinApi{
  this: SpatialExp =>

  def AXI_Master_Slave()(implicit ctx: SrcCtx): AXI_Master_Slave = AXI_Master_Slave(axi_ms_alloc())

  def Decoder_Template()(implicit ctx: SrcCtx): Decoder_Template = Decoder_Template(decoder_alloc())

  def DMA_Template()(implicit ctx: SrcCtx): DMA_Template = DMA_Template(dma_alloc())

  /** Internals **/
  def Decoder[T:Staged:Bits,C[T]](
    popFrom: StreamIn[T],
    pushTo:     FIFO[T]
  )(implicit ctx: SrcCtx): Void = {

    Pipe { 
      Decoder_Template()
      pushTo.enq(popFrom.deq())
    }
  
  }

  def DMA[T:Staged:Bits,C[T]](
    popFrom: FIFO[T],
    loadIn:     SRAM[T]
    // frameRdy:  StreamOut[T]
  )(implicit ctx: SrcCtx): Void = {

    Pipe {
      DMA_Template()
      Pipe (64 by 1) { i =>
        loadIn(i) = popFrom.deq()
      }
      // Pipe {
      //   frameRdy.push(1.as[T])
      // }
    }
  
  }

}


trait AlteraVideoExp extends Staging with MemoryExp {
  this: SpatialExp =>

  /** Infix methods **/
  case class AXI_Master_Slave(s: Exp[AXI_Master_Slave]) {
  }
  case class Decoder_Template(s: Exp[Decoder_Template]) {
  }
  case class DMA_Template(s: Exp[DMA_Template]) {
  }


  /** Staged Type **/
  object AXIMasterSlaveType extends Staged[AXI_Master_Slave] {
    override def unwrapped(x: AXI_Master_Slave) = x.s
    override def wrapped(x: Exp[AXI_Master_Slave]) = AXI_Master_Slave(x)
    override def typeArguments = Nil
    override def stagedClass = classOf[AXI_Master_Slave]
    override def isPrimitive = false
  }
  implicit def aXIMasterSlaveType: Staged[AXI_Master_Slave] = AXIMasterSlaveType

  object DecoderTemplateType extends Staged[Decoder_Template] {
    override def unwrapped(x: Decoder_Template) = x.s
    override def wrapped(x: Exp[Decoder_Template]) = Decoder_Template(x)
    override def typeArguments = Nil
    override def stagedClass = classOf[Decoder_Template]
    override def isPrimitive = true
  }
  implicit def decoderTemplateType: Staged[Decoder_Template] = DecoderTemplateType

  object DMATemplateType extends Staged[DMA_Template] {
    override def unwrapped(x: DMA_Template) = x.s
    override def wrapped(x: Exp[DMA_Template]) = DMA_Template(x)
    override def typeArguments = Nil
    override def stagedClass = classOf[DMA_Template]
    override def isPrimitive = true
  }
  implicit def dMATemplateType: Staged[DMA_Template] = DMATemplateType


  /** IR Nodes **/
  case class AxiMSNew() extends Op[AXI_Master_Slave] {
    def mirror(f:Tx) = axi_ms_alloc()
  }
  case class DecoderTemplateNew() extends Op[Decoder_Template] {
    def mirror(f:Tx) = decoder_alloc()
  }
  case class DMATemplateNew() extends Op[DMA_Template] {
    def mirror(f:Tx) = dma_alloc()
  }

  /** Constructors **/
  def axi_ms_alloc()(implicit ctx: SrcCtx): Sym[AXI_Master_Slave] = {
    stageSimple( AxiMSNew() )(ctx)
  }
  def decoder_alloc()(implicit ctx: SrcCtx): Sym[Decoder_Template] = {
    stageSimple( DecoderTemplateNew() )(ctx)
  }
  def dma_alloc()(implicit ctx: SrcCtx): Sym[DMA_Template] = {
    stageSimple( DMATemplateNew() )(ctx)
  }

  /** Internal methods **/

  // private[spatial] def source(x: Exp[Reg]): Exp = x match {
  //   case Op(AxiMSNew())    => 
  // }

}

