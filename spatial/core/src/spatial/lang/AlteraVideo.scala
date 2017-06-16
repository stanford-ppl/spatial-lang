package spatial.lang

import control._

import argon.core._
import forge._
import spatial.nodes._

case class AXI_Master_Slave(s: Exp[AXI_Master_Slave]) extends Template[AXI_Master_Slave]
object AXI_Master_Slave {
  implicit def aXIMasterSlaveType: Type[AXI_Master_Slave] = AXIMasterSlaveType

  @api def apply(): AXI_Master_Slave = AXI_Master_Slave(axi_ms_alloc())
  @internal def axi_ms_alloc(): Sym[AXI_Master_Slave] = {
    stageSimple( AxiMSNew() )(ctx)
  }
}

case class Decoder_Template[T:Type:Bits](s: Exp[Decoder_Template[T]]) extends Template[Decoder_Template[T]]
object Decoder_Template {
  implicit def decoderTemplateType[T:Type:Bits]: Type[Decoder_Template[T]] = DecoderTemplateType(typ[T])

  @api def apply[T:Type:Bits](popFrom: StreamIn[T], pushTo: FIFO[T]): Decoder_Template[T] = {
    Decoder_Template(decoder_alloc[T](popFrom.s.asInstanceOf[Exp[T]], pushTo.s.asInstanceOf[Exp[T]]))
  }
  @internal def decoder_alloc[T:Type:Bits](popFrom: Exp[T], pushTo: Exp[T]): Sym[Decoder_Template[T]] = {
    stageSimple( DecoderTemplateNew[T](popFrom, pushTo) )(ctx)
  }
}

case class DMA_Template[T:Type:Bits](s: Exp[DMA_Template[T]]) extends Template[DMA_Template[T]]
object DMA_Template {
  implicit def dMATemplateType[T:Type:Bits]: Type[DMA_Template[T]] = DMATemplateType(typ[T])

  @api def apply[T:Type:Bits](popFrom: FIFO[T], loadIn: SRAM1[T]): DMA_Template[T] = {
    DMA_Template(dma_alloc[T](popFrom.s.asInstanceOf[Exp[T]], loadIn.s.asInstanceOf[Exp[T]]))
  }
  @internal def dma_alloc[T:Type:Bits](popFrom: Exp[T], loadIn: Exp[T]): Sym[DMA_Template[T]] = {
    stageSimple( DMATemplateNew[T](popFrom, loadIn) )(ctx)
  }
}

object DMA {
  @api def apply[T:Type:Bits](popFrom: FIFO[T], loadIn: SRAM1[T] /*frameRdy:  StreamOut[T]*/): MUnit = {
    Pipe {
      DMA_Template(popFrom, loadIn)
      Foreach(Range(None, 64, None, None, isUnit = false)){ i =>
        loadIn(i) = popFrom.deq()
      }
      // Pipe {
      //   frameRdy.push(1.to[T])
      // }
      ()
    }
  }
}

object Decoder {
  @api def apply[T:Type:Bits,C[T]](popFrom: StreamIn[T], pushTo: FIFO[T]): MUnit = {
    Pipe {
      Decoder_Template(popFrom, pushTo)
      popFrom.value()
      ()
      // pushTo.enq(popFrom.deq())
    }
  }
}
