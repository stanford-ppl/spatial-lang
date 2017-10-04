package spatial.lang.cake

import argon.lang.cake._
import forge._

/** Language type aliases
  * NOTE: No cyclic aliases allowed, e.g. cannot have "type X = spatial.lang.X")
  */
trait SpatialLangAliases extends ArgonCommonAliases {
  type Bit = argon.lang.Boolean
  val Bit = argon.lang.Boolean

  type MRange = spatial.lang.Range
  val MRange = spatial.lang.Range
  type CRange = scala.collection.immutable.Range

  type Bus = spatial.targets.Bus
  type FPGATarget = spatial.targets.FPGATarget

  type BitVector = spatial.lang.VectorN[Bit]
  @generate type VectorJJ$JJ$1to128[T] = spatial.lang.Vec[_JJ,T]

  type MBinaryFile = spatial.lang.BinaryFile
  type MFile = spatial.lang.File

  type Tile[T] = spatial.lang.DRAMDenseTile[T]
  type SparseTile[T] = spatial.lang.DRAMSparseTile[T]

  @internal def unit = argon.lang.Unit.const()
}


/** External (outside spatial.lang) aliases **/
trait SpatialCommonAliases extends SpatialLangAliases {
  type Matrix[T] = spatial.lang.Matrix[T]
  val Matrix = spatial.lang.Matrix
  type Tensor3[T] = spatial.lang.Tensor3[T]
  val Tensor3 = spatial.lang.Tensor3
  type Tensor4[T] = spatial.lang.Tensor4[T]
  val Tensor4 = spatial.lang.Tensor4
  type Tensor5[T] = spatial.lang.Tensor5[T]
  val Tensor5 = spatial.lang.Tensor5

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

  type Range64 = spatial.lang.Range64
  val Range64 = spatial.lang.Range64
  type Wildcard = spatial.lang.Wildcard
  val Wildcard = spatial.lang.Wildcard

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
  val BurstCmd = spatial.lang.BurstCmd
  type IssuedCmd = spatial.lang.IssuedCmd
  val IssuedCmd = spatial.lang.IssuedCmd

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

trait SpatialInternalAliases extends SpatialCommonAliases with ArgonInternalAliases {
  val BitOps = spatial.lang.BitOps
  val DebuggingOps = spatial.lang.DebuggingOps
  val Delays = spatial.lang.Delays
  val VarReg = spatial.lang.VarReg

  val HostTransferOps = spatial.lang.HostTransferOps
  val DRAMTransfers = spatial.lang.DRAMTransfers
  val DRAMTransfersInternal = spatial.lang.DRAMTransfersInternal
  val FringeTransfers = spatial.lang.FringeTransfers

  val MFile = spatial.lang.File
  val MBinaryFile = spatial.lang.BinaryFile
  val Switches = spatial.lang.Switches

  type Blk  = (argon.core.Exp[_], Int)
  type Ctrl = (argon.core.Exp[_], Int)
  type Access = (argon.core.Exp[_], Ctrl)
  type StreamInfo = (argon.core.Exp[_], argon.core.Exp[_])
  type PortMap = (Int, Int, Int)
}

trait SpatialExternalAliases extends SpatialCommonAliases with ArgonExternalAliases {
  type File = spatial.lang.File
  type Tup2[A,B] = argon.lang.Tuple2[A,B]
  type Range = spatial.lang.Range

  lazy val Math = spatial.lang.Math
  lazy val bound = spatial.metadata.Bound
  lazy val targets = spatial.targets.Targets
}
