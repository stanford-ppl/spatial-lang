// See LICENSE for license details.

package axi4

import util.GenericParameterizedBundle
import chisel3._
import chisel3.util.{Cat, Irrevocable}

abstract class AXI4BundleBase(params: AXI4BundleParameters) extends GenericParameterizedBundle(params)

abstract class AXI4BundleA(params: AXI4BundleParameters) extends AXI4BundleBase(params)
{
  val id     = UInt(width = params.idBits)
  val addr   = UInt(width = params.addrBits)
  val len    = UInt(width = params.lenBits)  // number of beats - 1
  val size   = UInt(width = params.sizeBits) // bytes in beat = 2^size
  val burst  = UInt(width = params.burstBits)
  val lock   = UInt(width = params.lockBits)
  val cache  = UInt(width = params.cacheBits)
  val prot   = UInt(width = params.protBits)
  val qos    = UInt(width = params.qosBits)  // 0=no QoS, bigger = higher priority
  // val region = UInt(width = 4) // optional

  // Number of bytes-1 in this operation
  def bytes1(x:Int=0) = {
    val maxShift = 1 << params.sizeBits
    val tail = UInt((BigInt(1) << maxShift) - 1)
    (Cat(len, tail) << size) >> maxShift
  }
}

// A non-standard bundle that can be both AR and AW
class AXI4BundleARW(params: AXI4BundleParameters) extends AXI4BundleA(params)
{
  val wen = Bool()
}

class AXI4BundleAW(params: AXI4BundleParameters) extends AXI4BundleA(params)
class AXI4BundleAR(params: AXI4BundleParameters) extends AXI4BundleA(params)

class AXI4BundleW(params: AXI4BundleParameters) extends AXI4BundleBase(params)
{
  // id ... removed in AXI4
  val data = UInt(width = params.dataBits)
  val strb = UInt(width = params.dataBits/8)
  val last = Bool()
}

class AXI4BundleR(params: AXI4BundleParameters) extends AXI4BundleBase(params)
{
  val id   = UInt(width = params.idBits)
  val data = UInt(width = params.dataBits)
  val resp = UInt(width = params.respBits)
  val last = Bool()
}

class AXI4BundleB(params: AXI4BundleParameters) extends AXI4BundleBase(params)
{
  val id   = UInt(width = params.idBits)
  val resp = UInt(width = params.respBits)
}

class AXI4Bundle(params: AXI4BundleParameters) extends AXI4BundleBase(params)
{
  val aw = Irrevocable(new AXI4BundleAW(params))
  val w  = Irrevocable(new AXI4BundleW (params))
  val b  = Irrevocable(new AXI4BundleB (params)).flip
  val ar = Irrevocable(new AXI4BundleAR(params))
  val r  = Irrevocable(new AXI4BundleR (params)).flip
}

/**
 * Inlined AXI4 interface definition, same as 'AXI4Bundle'. Inlining helps Vivado
 * to auto-detect AXI4 and hence enables using block connection automation features
 */
class AXI4Inlined(params: AXI4BundleParameters) extends AXI4BundleBase(params)
{
  // aw
  val AWID     = Output(UInt(width = params.idBits))
  val AWADDR   = Output(UInt(width = params.addrBits))
  val AWLEN    = Output(UInt(width = params.lenBits))  // number of beats - 1
  val AWSIZE   = Output(UInt(width = params.sizeBits)) // bytes in beat = 2^size
  val AWBURST  = Output(UInt(width = params.burstBits))
  val AWLOCK   = Output(UInt(width = params.lockBits))
  val AWCACHE  = Output(UInt(width = params.cacheBits))
  val AWPROT   = Output(UInt(width = params.protBits))
  val AWQOS    = Output(UInt(width = params.qosBits))  // 0=no QoS, bigger = higher priority
  val AWVALID  = Output(Bool())
  val AWREADY  = Input(Bool())

  // ar
  val ARID     = Output(UInt(width = params.idBits))
  val ARADDR   = Output(UInt(width = params.addrBits))
  val ARLEN    = Output(UInt(width = params.lenBits))  // number of beats - 1
  val ARSIZE   = Output(UInt(width = params.sizeBits)) // bytes in beat = 2^size
  val ARBURST  = Output(UInt(width = params.burstBits))
  val ARLOCK   = Output(UInt(width = params.lockBits))
  val ARCACHE  = Output(UInt(width = params.cacheBits))
  val ARPROT   = Output(UInt(width = params.protBits))
  val ARQOS    = Output(UInt(width = params.qosBits))  // 0=no QoS, bigger = higher priority
  val ARVALID  = Output(Bool())
  val ARREADY  = Input(Bool())


  // w
  val WDATA = Output(UInt(width = params.dataBits))
  val WSTRB = Output(UInt(width = params.dataBits/8))
  val WLAST = Output(Bool())
  val WVALID  = Output(Bool())
  val WREADY  = Input(Bool())

  // r: Input
  val RID   = Input(UInt(width = params.idBits))
  val RDATA = Input(UInt(width = params.dataBits))
  val RRESP = Input(UInt(width = params.respBits))
  val RLAST = Input(Bool())
  val RVALID  = Input(Bool())
  val RREADY  = Output(Bool())

  // b: Input
  val BID   = Input(UInt(width = params.idBits))
  val BRESP = Input(UInt(width = params.respBits))
  val BVALID  = Input(Bool())
  val BREADY  = Output(Bool())
}

class AXI4Lite(params: AXI4BundleParameters) extends AXI4BundleBase(params)
{
  // aw
  val AWADDR   = Output(UInt(width = params.addrBits))
  val AWPROT   = Output(UInt(width = params.protBits))
  val AWVALID  = Output(Bool())
  val AWREADY  = Input(Bool())

  // ar
  val ARADDR   = Output(UInt(width = params.addrBits))
  val ARPROT   = Output(UInt(width = params.protBits))
  val ARVALID  = Output(Bool())
  val ARREADY  = Input(Bool())

  // w
  val WDATA = Output(UInt(width = params.dataBits))
  val WSTRB = Output(UInt(width = params.dataBits/8))
  val WVALID  = Output(Bool())
  val WREADY  = Input(Bool())

  // r: Input
  val RDATA = Input(UInt(width = params.dataBits))
  val RRESP = Input(UInt(width = params.respBits))
  val RVALID  = Input(Bool())
  val RREADY  = Output(Bool())

  // b: Input
  val BRESP = Input(UInt(width = params.respBits))
  val BVALID  = Input(Bool())
  val BREADY  = Output(Bool())
}

object AXI4Bundle
{
  def apply(params: AXI4BundleParameters) = new AXI4Bundle(params)
}
