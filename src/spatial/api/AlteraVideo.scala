package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait AlteraVideoApi extends AlteraVideoExp {
  this: SpatialExp =>

  def AXI_Master_Slave[T:Staged:Bits]()(implicit ctx: SrcCtx): AXI_Master_Slave[T] = AXI_Master_Slave(axi_ms_alloc[T]())

}


trait AlteraVideoExp extends Staging with MemoryExp {
  this: SpatialExp =>

  /** Infix methods **/
  case class AXI_Master_Slave[T:Staged:Bits](s: Exp[AXI_Master_Slave[T]]) {
  }

  /** Staged Type **/
  case class AXIMasterSlaveType[T:Bits](child: Staged[T]) extends Staged[AXI_Master_Slave[T]] {
    override def unwrapped(x: AXI_Master_Slave[T]) = x.s
    override def wrapped(x: Exp[AXI_Master_Slave[T]]) = AXI_Master_Slave(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[AXI_Master_Slave[T]]
    override def isPrimitive = false
  }
  implicit def aXIMasterSlaveType[T:Staged:Bits]: Staged[AXI_Master_Slave[T]] = AXIMasterSlaveType[T](typ[T])


  /** IR Nodes **/
  case class AxiMSNew[T:Staged:Bits]() extends Op[AXI_Master_Slave[T]] {
    def mirror(f:Tx) = axi_ms_alloc[T]()
  }

  /** Constructors **/
  def axi_ms_alloc[T:Staged:Bits]()(implicit ctx: SrcCtx): Sym[AXI_Master_Slave[T]] = {
    stage( AxiMSNew[T]() )(ctx)
  }

  /** Internal methods **/

  // private[spatial] def source[T](x: Exp[Reg[T]]): Exp[T] = x match {
  //   case Op(AxiMSNew())    => 
  // }

}

