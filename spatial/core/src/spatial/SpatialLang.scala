package spatial

import argon._
import forge._
import spatial.analysis._
import spatial.lang._
import spatial.targets.FPGATarget

trait SpatialCommonAliases extends SpatialLangAliases {
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

  type FIFO[T] = spatial.lang.FIFO[T]
  val FIFO = spatial.lang.FIFO

  type Range = spatial.lang.Range
  val Range = spatial.lang.Range
  type Range64 = spatial.lang.Range64
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
}

protected trait SpatialExp extends ArgonExp with SpatialCommonAliases
    with MatrixExp
    with FileIOExp
    with ControllerExp
    with CounterExp with DRAMExp with DRAMTransferExp with FIFOExp with FILOExp with HostTransferExp with MathExp
    with MemoryExp with ParameterExp with RangeExp with RegExp with SRAMExp with StagedUtilExp with UnrolledExp with VectorExp
    with StreamExp with PinExp with AlteraVideoExp with ShiftRegExp with LUTsExp
    with LineBufferExp with RegisterFileExp with SwitchExp with StateMachineExp with EnabledPrimitivesExp
    with NodeClasses with NodeUtils with ParameterRestrictions with SpatialMetadataExp with BankingMetadataExp
    with StreamTransfersExp
{
  def target: FPGATarget // Needs to be filled in by application, defaults to Default
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

trait SpatialApi extends ArgonApi with SpatialExp
  with DebuggingApi
  with MatrixApi
  with ControllerApi with CounterApi with DRAMApi with DRAMTransferApi with FIFOApi with FILOApi with HostTransferApi with MathApi
  with MemoryApi with ParameterApi with RangeApi with RegApi with SRAMApi with StagedUtilApi with UnrolledApi
  with StreamApi with PinApi with AlteraVideoApi with ShiftRegApi with LUTsApi
  with LineBufferApi with RegisterFileApi with SwitchApi with StateMachineApi with EnabledPrimitivesApi
  with SpatialMetadataApi with BankingMetadataApi with SpatialImplicits with FileIOApi
  with StreamTransfersApi



trait SpatialLangInternal extends ArgonLangInternal with SpatialExp {
  val BitOps = spatial.lang.BitOps
  val DebuggingOps = spatial.lang.DebuggingOps

  val MFile = spatial.lang.File
  val SwitchOps = spatial.lang.SwitchOps
}

trait SpatialLangExternal extends ArgonLangExternal with SpatialApi {
  type File = spatial.lang.File
}

object compiler extends SpatialLangInternal