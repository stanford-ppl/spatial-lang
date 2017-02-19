package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait AlteraVideoApi extends AlteraVideoExp {
  this: SpatialExp =>

  def AXI_Master_Slave()(implicit ctx: SrcCtx): AXI_Master_Slave = AXI_Master_Slave(axi_ms_alloc())

}


trait AlteraVideoExp extends Staging with MemoryExp {
  this: SpatialExp =>

  /** Infix methods **/
  case class AXI_Master_Slave(s: Exp[AXI_Master_Slave]) {
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

  // case class DecoderType[T:Bits](child: Staged[T]) extends Staged[Decoder[T]] {
  //   override def unwrapped(x: Decoder[T]) = x.s
  //   override def wrapped(x: Exp[Decoder[T]]) = Decoder[T](x)(child,bits[T])
  //   override def typeArguments = List(child)
  //   override def stagedClass = classOf[Decoder[T]]
  //   override def isPrimitive = false
  // }
  // implicit def decoderType[T:Staged:Bits]: Staged[Decoder[T]] = DecoderType(typ[T])


  /** IR Nodes **/
  case class AxiMSNew() extends Op[AXI_Master_Slave] {
    def mirror(f:Tx) = axi_ms_alloc()
  }
  // case class DecoderNew[T]() extends Op[Decoder[T]] {
  //   def mirror(f:Tx) = decoder_alloc()
  // }

  /** Constructors **/
  def axi_ms_alloc()(implicit ctx: SrcCtx): Sym[AXI_Master_Slave] = {
    stageCold( AxiMSNew() )(ctx)
  }
  // def decoder_alloc[T]()(implicit ctx: SrcCtx): Sym[Decoder[T]] = {
  //   stageCold( DecoderNew() )(ctx)
  // }

  /** Internal methods **/

  // private[spatial] def source(x: Exp[Reg]): Exp = x match {
  //   case Op(AxiMSNew())    => 
  // }

}

