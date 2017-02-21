package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait AlteraVideoApi extends AlteraVideoExp with BurstTransferExp with ControllerApi with FIFOApi with RangeApi with PinApi{
  this: SpatialExp =>

  def AXI_Master_Slave()(implicit ctx: SrcCtx): AXI_Master_Slave = AXI_Master_Slave(axi_ms_alloc())

  def Decoder_Template()(implicit ctx: SrcCtx): Decoder_Template = Decoder_Template(decoder_alloc())
  /** Internals **/
  def Decoder[T:Staged:Bits,C[T]](
    popFrom: StreamIn[T],
    pushTo:     FIFO[T]
  )(implicit ctx: SrcCtx): Void = {


    // def Decoder(popFrom: Exp[StreamIn[T]], pushTo: Exp[FIFO[T]]): Void = {
      Pipe { 
        Decoder_Template()
        pushTo.enq(popFrom.deq())
      }
    // }

  
  }

}


trait AlteraVideoExp extends Staging with MemoryExp {
  this: SpatialExp =>

  /** Infix methods **/
  case class AXI_Master_Slave(s: Exp[AXI_Master_Slave]) {
  }
  case class Decoder_Template(s: Exp[Decoder_Template]) {
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


  /** IR Nodes **/
  case class AxiMSNew() extends Op[AXI_Master_Slave] {
    def mirror(f:Tx) = axi_ms_alloc()
  }
  case class DecoderTemplateNew() extends Op[Decoder_Template] {
    def mirror(f:Tx) = decoder_alloc()
  }

  /** Constructors **/
  def axi_ms_alloc()(implicit ctx: SrcCtx): Sym[AXI_Master_Slave] = {
    stageCold( AxiMSNew() )(ctx)
  }
  def decoder_alloc()(implicit ctx: SrcCtx): Sym[Decoder_Template] = {
    stageCold( DecoderTemplateNew() )(ctx)
  }

  /** Internal methods **/

  // private[spatial] def source(x: Exp[Reg]): Exp = x match {
  //   case Op(AxiMSNew())    => 
  // }

}

