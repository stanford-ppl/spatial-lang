package spatial

import argon.internals._
import argon.{ArgonLangInternal, ArgonLangExternal, ArgonExp, ArgonApi}
import forge._
import spatial.lang._
import spatial.targets.FPGATarget

trait SpatialCommonAliases extends SpatialLangAliases {
  type Matrix[T] = spatial.lang.Matrix[T]
  type Tensor3[T] = spatial.lang.Tensor3[T]
  type Tensor4[T] = spatial.lang.Tensor4[T]
  type Tensor5[T] = spatial.lang.Tensor5[T]

  type AXI_Master_Slave = spatial.lang.AXI_Master_Slave
  val AXI_Master_Slave = spatial.lang.AXI_Master_Slave
  type Decoder_Template[T] = spatial.lang.Decoder_Template[T]
  val Decoder_Template = spatial.lang.Decoder_Template
  type DMA_Template[T] = spatial.lang.DMA_Template[T]
  val DMA_Template = spatial.lang.DMA_Template

  type Controller = spatial.lang.control.Controller
  val Accel = spatial.lang.control.Accel
  val Foreach = spatial.lang.control.Foreach
  val FSM = spatial.lang.control.FSM
  val Fold = spatial.lang.control.Fold
  val Reduce = spatial.lang.control.Reduce
  val MemFold = spatial.lang.control.MemFold
  val MemReduce = spatial.lang.control.MemReduce

  val Pipe = spatial.lang.control.Pipe
  val Sequential = spatial.lang.control.Sequential
  val Parallel = spatial.lang.control.Parallel
  val Stream = spatial.lang.control.Stream

  type Counter = spatial.lang.Counter
  val Counter = spatial.lang.Counter
  type CounterChain = spatial.lang.CounterChain
  val CounterChain = spatial.lang.CounterChain

  type DRAM[T] = spatial.lang.DRAM[T]
  val DRAM = spatial.lang.DRAM
  type DRAM1[T] = spatial.lang.DRAM1[T]
  type DRAM2[T] = spatial.lang.DRAM2[T]
  type DRAM3[T] = spatial.lang.DRAM3[T]
  type DRAM4[T] = spatial.lang.DRAM4[T]
  type DRAM5[T] = spatial.lang.DRAM5[T]

  type Mem[T,C[_]] = spatial.lang.Mem[T,C]

  type FIFO[T] = spatial.lang.FIFO[T]
  val FIFO = spatial.lang.FIFO
  type FILO[T] = spatial.lang.FILO[T]
  val FILO = spatial.lang.FILO

  type LineBuffer[T] = spatial.lang.LineBuffer[T]
  val LineBuffer = spatial.lang.LineBuffer

  type LUT[T] = spatial.lang.LUT[T]
  val LUT = spatial.lang.LUT
  type LUT1[T] = spatial.lang.LUT1[T]
  type LUT2[T] = spatial.lang.LUT2[T]
  type LUT3[T] = spatial.lang.LUT3[T]
  type LUT4[T] = spatial.lang.LUT4[T]
  type LUT5[T] = spatial.lang.LUT5[T]

  type Reg[T] = spatial.lang.Reg[T]
  val Reg = spatial.lang.Reg
  val ArgIn = spatial.lang.ArgIn
  val ArgOut = spatial.lang.ArgOut
  val HostIO = spatial.lang.HostIO

  type RegFile[T] = spatial.lang.RegFile[T]
  val RegFile = spatial.lang.RegFile
  type RegFile1[T] = spatial.lang.RegFile1[T]
  type RegFile2[T] = spatial.lang.RegFile2[T]
  type RegFile3[T] = spatial.lang.RegFile3[T]

  type SRAM[T] = spatial.lang.SRAM[T]
  val SRAM = spatial.lang.SRAM
  type SRAM1[T] = spatial.lang.SRAM1[T]
  type SRAM2[T] = spatial.lang.SRAM2[T]
  type SRAM3[T] = spatial.lang.SRAM3[T]
  type SRAM4[T] = spatial.lang.SRAM4[T]
  type SRAM5[T] = spatial.lang.SRAM5[T]

  type VarReg[T] = spatial.lang.VarReg[T]

  type MRange = spatial.lang.Range
  val MRange = spatial.lang.Range
  type CRange = scala.collection.immutable.Range
  type Range64 = spatial.lang.Range64
  val Range64 = spatial.lang.Range64
  type Wildcard = spatial.lang.Wildcard

  type BufferedOut[T] = spatial.lang.BufferedOut[T]
  val BufferedOut = spatial.lang.BufferedOut
  type StreamIn[T] = spatial.lang.StreamIn[T]
  val StreamIn = spatial.lang.StreamIn
  type StreamOut[T] = spatial.lang.StreamOut[T]
  val StreamOut = spatial.lang.StreamOut

  type Vector[T] = spatial.lang.Vector[T]
  val Vector = spatial.lang.Vector
  type VectorN[T] = spatial.lang.VectorN[T]
  val VectorN = spatial.lang.VectorN
  type Vec[N,T] = spatial.lang.Vec[N,T]
  val Vec = spatial.lang.Vec

  type BurstCmd = spatial.lang.BurstCmd
  type IssuedCmd = spatial.lang.IssuedCmd
  type DRAMBus[T] = spatial.lang.DRAMBus[T]
  val BurstCmdBus = spatial.lang.BurstCmdBus
  val BurstAckBus = spatial.lang.BurstAckBus
  type BurstDataBus[T] = spatial.lang.BurstDataBus[T]
  val BurstDataBus = spatial.lang.BurstDataBus
  type BurstFullDataBus[T] = spatial.lang.BurstFullDataBus[T]
  val BurstFullDataBus = spatial.lang.BurstFullDataBus

  val GatherAddrBus = spatial.lang.GatherAddrBus
  type GatherDataBus[T] = spatial.lang.GatherDataBus[T]
  val GatherDataBus = spatial.lang.GatherDataBus

  type ScatterCmdBus[T] = spatial.lang.ScatterCmdBus[T]
  val ScatterCmdBus = spatial.lang.ScatterCmdBus
  val ScatterAckBus = spatial.lang.ScatterAckBus
}

protected trait SpatialExp extends ArgonExp with SpatialCommonAliases {
  def target: FPGATarget = SpatialConfig.target // Needs to be filled in by application, defaults to Default
}

trait SpatialImplicits {
  // HACK: Insert MUnit where required to make programs not have to include () at the end of ... => MUnit functions
  @api implicit def insert_unit[T:Type](x: T): MUnit = MUnit()

  // Hacks required to allow .to[T] syntax on various primitive types
  // Naming is apparently important here (has to have same names as in Predef)
  implicit class longWrapper(x: scala.Long) {
    @api def to[B:Type](implicit cast: Cast[scala.Long,B]): B = cast(x)
  }
  implicit class floatWrapper(x: scala.Float) {
    @api def to[B:Type](implicit cast: Cast[scala.Float,B]): B = cast(x)
  }
  implicit class doubleWrapper(x: scala.Double) {
    @api def to[B:Type](implicit cast: Cast[scala.Double,B]): B = cast(x)
  }
}

trait SpatialApi extends ArgonApi with SpatialExp with SpatialImplicits
  with BitOpsApi
  with DebuggingApi
  with DRAMTransfersApi
  with FileIOApi
  with HostTransferApi
  with MathApi
  with MatrixApi
  with Parameters
  with RangeApi
  with RegApi
  with StagedUtils
  with StreamApi
  with VectorApi


trait SpatialLangInternal extends ArgonLangInternal with SpatialExp {
  val BitOps = spatial.lang.BitOps
  val DebuggingOps = spatial.lang.DebuggingOps
  val Delays = spatial.lang.Delays
  val VarReg = spatial.lang.VarReg

  val HostTransferOps = spatial.lang.HostTransferOps

  val MFile = spatial.lang.File
  val Switches = spatial.lang.Switches

  type Ctrl = (Exp[_], Boolean)
  type Access = (Exp[_], Ctrl)
}

trait SpatialLangExternal extends ArgonLangExternal with SpatialApi {
  type File = spatial.lang.File

  val Math = spatial.lang.Math
  val bound = spatial.metadata.bound
}

object compiler extends SpatialLangInternal