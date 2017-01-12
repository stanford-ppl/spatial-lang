/*package spatial.tests

import spatial.spec.SpatialOps

//object SobelCompiler extends SpatialCompilerX with SobelApp
trait SobelApp extends SpatialAppX {
  val fpga = Targets.ZC706

  def main(): Unit = {



  }

}




object Targets {

  val ZC706 = new FPGA_ZC706(params = 3)
}

trait Target

class FPGA_ZC706(params: Int) extends Target {
  val VIDEO:        List[String] = List("Q1", "Q2", "Q3") // ???
  val PACKET_END:   List[String] = List("F5")
  val PACKET_START: List[String] = List("F6")
  val VIDEO_DVAL:   List[String] = List("F7")
}


trait SpatialAppX extends SpatialApp with StreamingOps

trait StreamingOps extends SpatialOps {
  type Bit = Bool

  type StreamIn[T] <: StreamInOps[T]
  type StreamOut[T]

  protected trait StreamInOps[T] {
    def get: T
  }

  def StreamIn[T:Bits](pins: List[String]): StreamIn[T]
  def StreamOut[T:Bits](pins: List[String]): StreamOut[T]

  object Stream {
    def Pipe(ctr: Counter)(func: Index => Void) = Foreach(ctr)(func)
    def Reduce[T:Bits](zero: T)(ctr1: Counter, ctr2: Counter)(map: (Index,Index) => T)(reduce: (T,T) => T): T = ???
  }

  def * : Counter
}*/