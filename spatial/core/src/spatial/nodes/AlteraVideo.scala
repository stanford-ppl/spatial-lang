package spatial.nodes

import argon.core._
import spatial.aliases._

object AXIMasterSlaveType extends Type[AXI_Master_Slave] {
  override def wrapped(x: Exp[AXI_Master_Slave]) = new AXI_Master_Slave(x)
  override def stagedClass = classOf[AXI_Master_Slave]
  override def isPrimitive = false
}

case class DecoderTemplateType[T:Bits](child: Type[T]) extends Type[Decoder_Template[T]] {
  override def wrapped(x: Exp[Decoder_Template[T]]) = new Decoder_Template(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[Decoder_Template[T]]
  override def isPrimitive = false
}

case class DMATemplateType[T:Bits](child: Type[T]) extends Type[DMA_Template[T]] {
  override def wrapped(x: Exp[DMA_Template[T]]) = new DMA_Template[T](x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[DMA_Template[T]]
  override def isPrimitive = false
}

/** IR Nodes **/
case class AxiMSNew() extends Op[AXI_Master_Slave] {
  def mirror(f:Tx) = AXI_Master_Slave.axi_ms_alloc()
}
case class DecoderTemplateNew[T:Type:Bits](popFrom: Exp[T], pushTo: Exp[T]) extends Op[Decoder_Template[T]] {
  def mirror(f:Tx) = Decoder_Template.decoder_alloc[T](f(popFrom), f(pushTo))
  val mT = typ[T]
  val bT = bits[T]

}
case class DMATemplateNew[T:Type:Bits](popFrom: Exp[T], loadIn: Exp[T]) extends Op[DMA_Template[T]] {
  def mirror(f:Tx) = DMA_Template.dma_alloc[T](f(popFrom), f(loadIn))
  val mT = typ[T]
  val bT = bits[T]
}