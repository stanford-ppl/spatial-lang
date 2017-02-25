package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait AlteraVideoApi extends AlteraVideoExp with BurstTransferExp with ControllerApi with FIFOApi with RangeApi with PinApi{
  this: SpatialExp =>

  def AXI_Master_Slave()(implicit ctx: SrcCtx): AXI_Master_Slave = AXI_Master_Slave(axi_ms_alloc())

  def Decoder_Template[T:Staged:Bits](popFrom: StreamIn[T], pushTo: FIFO[T])(implicit ctx: SrcCtx): Decoder_Template[T] = Decoder_Template(decoder_alloc[T](popFrom.s.asInstanceOf[Exp[T]], pushTo.s.asInstanceOf[Exp[T]]))

  def DMA_Template[T:Staged:Bits](popFrom: FIFO[T], loadIn: SRAM[T])(implicit ctx: SrcCtx): DMA_Template[T] = DMA_Template(dma_alloc[T](popFrom.s.asInstanceOf[Exp[T]], loadIn.s.asInstanceOf[Exp[T]]))

  /** Internals **/
  def Decoder[T:Staged:Bits,C[T]](
    popFrom: StreamIn[T],
    pushTo:     FIFO[T]
  )(implicit ctx: SrcCtx): Void = {

    Pipe { 
      Decoder_Template(popFrom, pushTo)
      pushTo.enq(popFrom.deq())
    }
  
  }

  def DMA[T:Staged:Bits,C[T]](
    popFrom: FIFO[T],
    loadIn:     SRAM[T]
    // frameRdy:  StreamOut[T]
  )(implicit ctx: SrcCtx): Void = {

    Pipe {
      DMA_Template(popFrom, loadIn)
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
  case class Decoder_Template[T:Bits](s: Exp[Decoder_Template[T]]) {
  }
  case class DMA_Template(s: Exp[DMA_Template[T]]) {
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

  case class DecoderTemplateType[T:Bits](child: Staged[T]) extends Staged[Decoder_Template[T]] {
    override def unwrapped(x: Decoder_Template[T]) = x.s
    override def wrapped(x: Exp[Decoder_Template[T]]) = Decoder_Template(x)(bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[Decoder_Template[T]]
    override def isPrimitive = true
  }
  implicit def decoderTemplateType[T:Staged:Bits]: Staged[Decoder_Template[T]] = DecoderTemplateType(typ[T])

  case class DMATemplateType[T:Bits](child: Staged[T]) extends Staged[DMA_Template[T]] {
    override def unwrapped(x: DMA_Template[T]) = x.s
    override def wrapped(x: Exp[DMA_Template[T]]) = DMA_Template[T](x)(bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[DMA_Template[T]]
    override def isPrimitive = true
  }
  implicit def dMATemplateType[T:Staged:Bits]: Staged[DMA_Template[T]] = DMATemplateType(typ[T])


  /** IR Nodes **/
  case class AxiMSNew() extends Op[AXI_Master_Slave] {
    def mirror(f:Tx) = axi_ms_alloc()
  }
  case class DecoderTemplateNew[T:Staged:Bits](popFrom: Exp[T], pushTo: Exp[T]) extends Op[Decoder_Template[T]] {
    def mirror(f:Tx) = decoder_alloc[T](f(popFrom), f(pushTo))
    val mT = typ[T]
    val bT = bits[T]

  }
  case class DMATemplateNew[T:Staged:Bits](popFrom: Exp[T], loadIn: Exp[T]) extends Op[DMA_Template[T]] {
    def mirror(f:Tx) = dma_alloc[T](f(popFrom), f(loadIn))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  def axi_ms_alloc()(implicit ctx: SrcCtx): Sym[AXI_Master_Slave] = {
    stageSimple( AxiMSNew() )(ctx)
  }
  def decoder_alloc[T:Staged:Bits](popFrom: Exp[T], pushTo: Exp[T])(implicit ctx: SrcCtx): Sym[Decoder_Template[T]] = {
    stageSimple( DecoderTemplateNew[T](popFrom, pushTo) )(ctx)
  }
  def dma_alloc[T:Staged:Bits](popFrom: Exp[T], loadIn: Exp[T])(implicit ctx: SrcCtx): Sym[DMA_Template[T]] = {
    stageSimple( DMATemplateNew[T](popFrom, pushTo) )(ctx)
  }

  /** Internal methods **/

  // private[spatial] def source(x: Exp[Reg]): Exp = x match {
  //   case Op(AxiMSNew())    => 
  // }

}

