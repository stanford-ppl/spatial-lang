// See LICENSE.txt for license details.
package templates

import chisel3._
import chisel3.util.{log2Ceil, isPow2}
import chisel3.internal.sourceinfo._
import types._
import fringe._

sealed trait DeviceTarget
object Default extends DeviceTarget
object Zynq extends DeviceTarget
object DE1 extends DeviceTarget // Do not use this one
object de1soc extends DeviceTarget
object AWS_F1 extends DeviceTarget

object ops {



  implicit class ArrayOps[T](val b:Array[types.FixedPoint]) {
    def raw = {
      chisel3.util.Cat(b.map{_.raw})
    }
    def FP(s: Boolean, d: Int, f: Int): FixedPoint = {
      chisel3.util.Cat(b.map{_.raw}).FP(s, d, f)
    }
  }
  implicit class ArrayBoolOps[T](val b:Array[Bool]) {
    def raw = {
      chisel3.util.Cat(b.map{_.raw})
    }
    def FP(s: Boolean, d: Int, f: Int): FixedPoint = {
      chisel3.util.Cat(b.map{_.raw}).FP(s, d, f)
    }
  }

  implicit class IndexedSeqOps[T](val b:scala.collection.immutable.IndexedSeq[types.FixedPoint]) {
    def raw = {
      chisel3.util.Cat(b.map{_.raw})
    }
    def FP(s: Boolean, d: Int, f: Int): FixedPoint = {
      chisel3.util.Cat(b.map{_.raw}).FP(s, d, f)
    }
  }

  implicit class VecOps[T](val b:chisel3.core.Vec[types.FixedPoint]) {
    def raw = {
      chisel3.util.Cat(b.map{_.raw})
    }
    def FP(s: Boolean, d: Int, f: Int): FixedPoint = {
      chisel3.util.Cat(b.map{_.raw}).FP(s, d, f)
    }

  }

  implicit class BoolOps(val b:Bool) {
    def D(delay: Int, retime_released: Bool = true.B) = {
//      Mux(retime_released, chisel3.util.ShiftRegister(b, delay, false.B, true.B), false.B)
      Mux(retime_released, Utils.getRetimed(b, delay), false.B)
    }

  }
  
  // implicit class DspRealOps(val b:DspReal) {
  //   def raw = {
  //     b.node
  //   }
  //   def number = {
  //     b.node
  //   }
  //   def r = {
  //     b.node
  //   }
  // }

  implicit class UIntOps(val b:UInt) {
    // Define number so that we can be compatible with FixedPoint type
    def number = {
      b
    }
    def raw = {
      b
    }
    def r = {
      b
    }
    def msb = {
      b(b.getWidth-1)
    }

    // override def connect (rawop: Data)(implicit sourceInfo: SourceInfo, connectionCompileOptions: chisel3.core.CompileOptions): Unit = {
    //   rawop match {
    //     case op: FixedPoint =>
    //       b := op.number
    //     case op: UInt =>
    //       b := op
    //   }
    // }

    def < (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) < c
    }

    def ^ (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) ^ c
    }

    def <= (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) <= c
    }

    def > (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) > c
    }

    def >= (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) >= c
    }

    def === (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) === c      
    }

    def =/= (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) =/= c      
    }

    def - (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) - c      
    }

    def <-> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) <-> c
    }

    def + (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) + c      
    }

    def <+> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) <+> c      
    }

    def *-* (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) *-* c
    }

    def *-* (c: UInt): UInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.multiply(b, c, Utils.fixmul_latency)
      } else {
        Utils.target match {
          case AWS_F1 => b*c // Raghu's box
          case Zynq => b*c // Raghu's box
          case DE1 => b*c // Raghu's box
          case de1soc => b*c // Raghu's box
          case Default => b*c
        }
      }
    }

    def *-* (c: SInt): SInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.multiply(b.asSInt, c, Utils.fixmul_latency)
      } else {
        Utils.target match {
          case AWS_F1 => b.asSInt*c // Raghu's box
          case Zynq => b.asSInt*c // Raghu's box
          case DE1 => b.asSInt*c // Raghu's box
          case de1soc => b.asSInt*c // Raghu's box
          case Default => b.asSInt*c
        }
      }
    }

    def <*> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) <*> c      
    }

    def *& (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) *& c      
    }

    def <*&> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) <*&> c      
    }

    def /-/ (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) /-/ c
    }

    def /-/ (c: UInt): UInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.divide(b, c, Utils.fixdiv_latency) // Raghu's box. Divide latency set to 16.
      } else {
       Utils.target match {
         case AWS_F1 => b/c // Raghu's box
         case Zynq => FringeGlobals.bigIP.divide(b, c, Utils.fixdiv_latency) // Raghu's box. Divide latency set to 16.
         case DE1 => b/c // Raghu's box
        case de1soc => b/c // Raghu's box
         case Default => b/c
       }
     }
    }

    def /-/ (c: SInt): SInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.divide(b.asSInt, c, Utils.fixdiv_latency) // Raghu's box. Divide latency set to 16.
      } else {
       Utils.target match {
         case AWS_F1 => b.asSInt/c // Raghu's box
         case Zynq => b.asSInt/c // Raghu's box
         case DE1 => b.asSInt/c // Raghu's box
        case de1soc => b.asSInt/c // Raghu's box
         case Default => b.asSInt/c
       }
     }
    }

    def </> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) </> c      
    }

    def /& (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) /& c      
    }

    def </&> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) </&> c      
    }

    def % (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) %-% c      
    }

    def %-% (c: UInt): UInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.mod(b, c, Utils.fixmod_latency)
      } else {
        Utils.target match {
          case AWS_F1 => b%c // Raghu's box
          case Zynq => b%c // Raghu's box
          case DE1 => b%c // Raghu's box
          case de1soc => b%c // Raghu's box
          case Default => b%c
        }
      }
    }

    def %-% (c: SInt): SInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.mod(b.asSInt, c, Utils.fixmod_latency)
      } else {
        Utils.target match {
          case AWS_F1 => b.asSInt%c // Raghu's box
          case Zynq => b.asSInt%c // Raghu's box
          case DE1 => b.asSInt%c // Raghu's box
          case de1soc => b.asSInt%c // Raghu's box
          case Default => b.asSInt%c
        }
      }
    }

    def FP(s: Boolean, d: Int, f: Int): FixedPoint = {
      Utils.FixedPoint(s, d, f, b)
    }

    def cast(c: FixedPoint): Unit = {
      c.r := Utils.FixedPoint(c.s,c.d,c.f,b).r
    }

  }

  implicit class SIntOps(val b:SInt) {
    // Define number so that we can be compatible with FixedPoint type
    def number = {
      b.asUInt
    }
    def raw = {
      b.asUInt
    }
    def r = {
      b.asUInt
    }
    def msb = {
      b(b.getWidth-1)
    }

    // override def connect (rawop: Data)(implicit sourceInfo: SourceInfo, connectionCompileOptions: chisel3.core.CompileOptions): Unit = {
    //   rawop match {
    //     case op: FixedPoint =>
    //       b := op.number
    //     case op: UInt =>
    //       b := op
    //   }
    // }

    def < (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) < c
    }

    def ^ (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) ^ c
    }

    def <= (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) <= c
    }

    def > (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) > c
    }

    def >= (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) >= c
    }

    def === (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) === c      
    }

    def =/= (c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) =/= c      
    }

    def - (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) - c      
    }

    def <-> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) <-> c
    }

    def + (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) + c      
    }

    def <+> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) <+> c      
    }

    def *-* (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) *-* c      
    }

    def *-* (c: UInt): SInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.multiply(b, c.asSInt, Utils.fixmul_latency)
      } else {
        Utils.target match {
          case AWS_F1 => b*c.asSInt // Raghu's box
          case Zynq => b*c.asSInt // Raghu's box
          case DE1 => b*c.asSInt // Raghu's box
          case de1soc => b*c.asSInt // Raghu's box
          case Default => b*c.asSInt
        }
      }
    }

    def *-* (c: SInt): SInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.multiply(b, c, Utils.fixmul_latency)
      } else {
        Utils.target match {
          case AWS_F1 => b*c // Raghu's box
          case Zynq => b*c // Raghu's box
          case DE1 => b*c // Raghu's box
          case de1soc => b*c // Raghu's box
          case Default => b*c
        }
      }
    }

    def <*> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) <*> c      
    }

    def *& (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) *& c      
    }

    def <*&> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) <*&> c      
    }

    def /-/ (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) /-/ c      
    }

    def /-/ (c: UInt): SInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.divide(b, c.asSInt, Utils.fixdiv_latency) // Raghu's box. Divide latency set to 16.
      } else {
       Utils.target match {
         case AWS_F1 => b/c.asSInt // Raghu's box
         case Zynq => b/c.asSInt // Raghu's box
         case DE1 => b/c.asSInt // Raghu's box
        case de1soc => b/c.asSInt // Raghu's box
         case Default => b/c.asSInt
       }
     }
    }

    def /-/ (c: SInt): SInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.divide(b, c, Utils.fixdiv_latency) // Raghu's box. Divide latency set to 16.
      } else {
       Utils.target match {
         case AWS_F1 => b/c // Raghu's box
         case Zynq => b/c // Raghu's box
         case DE1 => b/c // Raghu's box
        case de1soc => b/c // Raghu's box
         case Default => b/c
       }
     }
    }

    def </> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) </> c      
    }

    def /& (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) /& c      
    }

    def </&> (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) </&> c      
    }

    def %-% (c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) %-% c
    }

    def %-% (c: UInt): SInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.mod(b, c.asSInt, Utils.fixmod_latency)
      } else {
        Utils.target match {
          case AWS_F1 => b%c.asSInt // Raghu's box
          case Zynq => b%c.asSInt // Raghu's box
          case DE1 => b%c.asSInt // Raghu's box
          case de1soc => b%c.asSInt // Raghu's box
          case Default => b%c.asSInt
        }
      }
    }

    def %-% (c: SInt): SInt = { // TODO: Find better way to capture UInt / UInt, since implicit resolves won't make it this far
      if (Utils.retime) {
        FringeGlobals.bigIP.mod(b, c, Utils.fixmod_latency)
      } else {
        Utils.target match {
          case AWS_F1 => b%c // Raghu's box
          case Zynq => b%c // Raghu's box
          case DE1 => b%c // Raghu's box
          case de1soc => b%c // Raghu's box
          case Default => b%c
        }
      }
    }

    def FP(s: Boolean, d: Int, f: Int): FixedPoint = {
      Utils.FixedPoint(s, d, f, b)
    }
    def FlP(m: Int, e: Int): FloatingPoint = {
      Utils.FloatPoint(m, e, b)
    }

    def cast(c: FixedPoint): Unit = {
      c.r := Utils.FixedPoint(c.s,c.d,c.f,b).r
    }


  }


  implicit class IntOps(val b: Int) {
    def FP(s: Boolean, d: Int, f: Int): FixedPoint = {
      Utils.FixedPoint(s, d, f, b)
    }
    def FP(s: Int, d: Int, f: Int): FixedPoint = {
      Utils.FixedPoint(s, d, f, b)
    }
    def FlP(m: Int, e: Int): FloatingPoint = {
      Utils.FloatPoint(m, e, b)
    }
    def *-*(x: Int): Int = {b*x}
    def /-/(x: Int): Int = {b/x}
    def %-%(x: Int): Int = {b%x}
    def *-*(x: Double): Double = {b*x}
    def /-/(x: Double): Double = {b/x}
    def %-%(x: Double): Double = {b%x}
    def *-*(x: Long): Long = {b*x}
    def /-/(x: Long): Long = {b/x}
    def %-%(x: Long): Long = {b%x}
  }

  implicit class DoubleOps(val b: Double) {
    def FP(s: Boolean, d: Int, f: Int): FixedPoint = {
      Utils.FixedPoint(s, d, f, b)
    }
    def FP(s: Int, d: Int, f: Int): FixedPoint = {
      Utils.FixedPoint(s, d, f, b)
    }
    def FlP(m: Int, e: Int): FloatingPoint = {
      Utils.FloatPoint(m, e, b)
    }
    def *-*(x: Double): Double = {b*x}
    def /-/(x: Double): Double = {b/x}
    def %-%(x: Double): Double = {b%x}
    def *-*(x: Int): Double = {b*x}
    def /-/(x: Int): Double = {b/x}
    def %-%(x: Int): Double = {b%x}
    def *-*(x: Long): Double = {b*x}
    def /-/(x: Long): Double = {b/x}
    def %-%(x: Long): Double = {b%x}
  }
}

object Utils {

  var regression_testing = scala.util.Properties.envOrElse("RUNNING_REGRESSION", "0")

  // These properties should be set inside IOModule
  var target: DeviceTarget = Default
  var fixmul_latency = 6
  var fixdiv_latency = 20
  var fixadd_latency = 1
  var fixsub_latency = 1
  var fixmod_latency = 16
  var fixeql_latency = 1
  var sramload_latency = 0
  var sramstore_latency = 0
  var SramThreshold = 4 // Threshold between turning Mem1D into register array vs real memory
  var mux_latency = 1
  var retime = false

  val delay_per_numIter = List(
              fixsub_latency + fixdiv_latency + fixadd_latency,
              fixmul_latency + fixdiv_latency + fixadd_latency,
              fixsub_latency + fixmod_latency + fixeql_latency + mux_latency + fixadd_latency,
              fixmul_latency + fixmod_latency + fixeql_latency + mux_latency + fixadd_latency
    ).max

  def singleCycleDivide(num: SInt, den: SInt): SInt = {
    num / den
  }
  def singleCycleModulo(num: SInt, den: SInt): SInt = {
    num % den
  }
  def singleCycleDivide(num: UInt, den: UInt): UInt = {
    num / den
  }
  def singleCycleModulo(num: UInt, den: UInt): UInt = {
    num % den
  }
  def sqrt(num: FloatingPoint): FloatingPoint = {
    val m = num.m
    val e = num.e
    val result = Wire(new FloatingPoint(m, e))
    val fma = Module(new DivSqrtRecFN_small(m,e,0))
    fma.io.a := num.r
    fma.io.inValid := true.B // TODO: What should this be?
    fma.io.sqrtOp := true.B // TODO: What should this be?
    fma.io.roundingMode := 0.U(3.W) // TODO: What should this be?
    fma.io.detectTininess := true.B // TODO: What should this be?
    result.r := fNFromRecFN(m, e, fma.io.out)
    result
  }
  def getFloatBits(num: Float) = java.lang.Float.floatToRawIntBits(num)
  // def getDoubleBits(num: Double) = java.lang.Double.doubleToRawIntBits(num)
  def delay[T <: chisel3.core.Data](sig: T, length: Int):T = {
    if (length == 0) {
      sig
    } else {
      val regs = (0 until length).map { i => RegInit(0.U) } // TODO: Make this type T
      sig match {
        case s:Bool => 
          regs(0) := Mux(s, 1.U, 0.U)
          (length-1 until 0 by -1).map { i => 
            regs(i) := regs(i-1)
          }
          (regs(length-1) === 1.U).asInstanceOf[T]
        case s:UInt => 
          regs(0) := s
          (length-1 until 0 by -1).map { i => 
            regs(i) := regs(i-1)
          }
          (regs(length-1)).asInstanceOf[T]
        case s:FixedPoint =>
          regs(0) := s.r
          (length-1 until 0 by -1).map { i => 
            regs(i) := regs(i-1)
          }
          (regs(length-1)).asInstanceOf[T]
      }
    }
  }

  // def ShiftRegister[T <: chisel3.core.Data](data: T, size: Int):T = {
  //   data match {
  //     case d: UInt => chisel3.util.ShiftRegister(data, size)
  //     case d: FixedPoint => chisel3.util.ShiftRegister(data, size)
  //   }
  // }

  // def Reverse[T <: chisel3.core.Data](data: T):T = {
  //   data match {
  //     case d: UInt => chisel3.util.Reverse(d)
  //     case d: FixedPoint => 
  //       val res = Wire(new FixedPoint(d.s, d.d, d.f))
  //       res.r := chisel3.util.Reverse(d.r)
  //   }
  // }

  def risingEdge(sig:Bool): Bool = {
    sig & Utils.delay(~sig,1)
  }
  // Helper for making fixedpt when you know the value at creation time
  def FixedPoint[T](s: Int, d: Int, f: Int, init: T): types.FixedPoint = {
    FixedPoint(s > 0, d, f, init)
  }
  def FixedPoint[T](s: Boolean, d: Int, f: Int, init: T): types.FixedPoint = {
    val cst = Wire(new types.FixedPoint(s, d, f))
    init match {
      case i: Double => cst.raw := (i * scala.math.pow(2,f)).toLong.S((d+f+1).W).asUInt()
      case i: Bool => cst.r := i
      case i: UInt => 
        val tmp = Wire(new types.FixedPoint(s, i.getWidth, 0))
        tmp.r := i
        tmp.cast(cst)
        // if (f > 0) cst.r := chisel3.util.Cat(i, 0.U(f.W)) else cst.r := i
      case i: SInt => cst.r := FixedPoint(s,d,f,i.asUInt).r
      case i: FixedPoint => cst.raw := i.raw
      case i: Int => cst.raw := (i * scala.math.pow(2,f)).toLong.S((d+f+1).W).asUInt()
    }
    cst
  }

  def FloatPoint[T](m: Int, e: Int, init: T): FloatingPoint = {
    val cst = Wire(new FloatingPoint(m, e))
    init match {
      case i: Double => cst.raw := getFloatBits(i.toFloat).S.asUInt
      case i: Bool => cst.r := mux(i, getFloatBits(1f).U, getFloatBits(0f).U)
      // case i: UInt => 
      // case i: SInt => 
      case i: Int => cst.raw := getFloatBits(i.toFloat).U
    }
    cst
  }

  // def Cat[T1 <: chisel3.core.Data, T2 <: chisel3.core.Data](x1: T1, x2: T2): UInt = {
  //   val raw_x1 = x1 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x2 = x2 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }

  //   util.Cat(raw_x1,raw_x2)
  // }

  // def Cat[T1 <: chisel3.core.Data, T2 <: chisel3.core.Data, T3 <: chisel3.core.Data](x1: T1, x2: T2, x3: T3): UInt = {
  //   val raw_x1 = x1 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x2 = x2 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x3 = x3 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }

  //   util.Cat(raw_x1,raw_x2,raw_x3)
  // }
  // def Cat[T1 <: chisel3.core.Data, T2 <: chisel3.core.Data, T3 <: chisel3.core.Data, T4 <: chisel3.core.Data](x1: T1, x2: T2, x3: T3, x4: T4): UInt = {
  //   val raw_x1 = x1 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x2 = x2 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x3 = x3 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x4 = x4 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }

  //   util.Cat(raw_x1,raw_x2,raw_x3,raw_x4)
  // }
  // def Cat[T1 <: chisel3.core.Data, T2 <: chisel3.core.Data, T3 <: chisel3.core.Data, T4 <: chisel3.core.Data, T5 <: chisel3.core.Data](x1: T1, x2: T2, x3: T3, x4: T4, x5: T5): UInt = {
  //   val raw_x1 = x1 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x2 = x2 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x3 = x3 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x4 = x4 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x5 = x5 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }

  //   util.Cat(raw_x1,raw_x2,raw_x3,raw_x4,raw_x5)
  // }
  // def Cat[T1 <: chisel3.core.Data, T2 <: chisel3.core.Data, T3 <: chisel3.core.Data, T4 <: chisel3.core.Data, T5 <: chisel3.core.Data, T6 <: chisel3.core.Data](x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6): UInt = {
  //   val raw_x1 = x1 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x2 = x2 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x3 = x3 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x4 = x4 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x5 = x5 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }
  //   val raw_x6 = x6 match {
  //     case x:UInt => x
  //     case x:FixedPoint => x.raw
  //   }

  //   util.Cat(raw_x1,raw_x2,raw_x3,raw_x4,raw_x5,raw_x6)
  // }

  def mux[T1 <: chisel3.core.Data, T2 <: chisel3.core.Data](cond: T1, op1: T2, op2: T2): T2 = {
    val bool_cond = cond match {
      case x:Bool => x
      case x:UInt => x(0)
    }
    Mux(bool_cond, op1, op2)
  }


  def floor(a: UInt): UInt = { a }
  def ceil(a: UInt): UInt = { a }
  def floor(a: FixedPoint): FixedPoint = { a.floor() }
  def ceil(a: FixedPoint): FixedPoint = { a.ceil() }

  def min[T <: chisel3.core.Data](a: T, b: T): T = {
    (a,b) match {
      case (aa:UInt,bb:UInt) => Mux(aa < bb, a, b)
      case (_,_) => a // TODO: implement for other types
    }
  }

  def max[T <: chisel3.core.Data](a: T, b: T): T = {
    (a,b) match {
      case (aa:UInt,bb:UInt) => Mux(aa > bb, a, b)
      case (_,_) => a // TODO: implement for other types
    }
  }

  def log2Up[T](raw:T): Int = {
    raw match {
      case n: Int => if (n < 0) {1 max log2Ceil(1 max {1+scala.math.abs(n)})} else {1 max log2Ceil(1 max n)}
      case n: scala.math.BigInt => if (n < 0) {1 max log2Ceil(1.asInstanceOf[scala.math.BigInt] max {1.asInstanceOf[scala.math.BigInt]+n.abs})} 
                                   else {1 max log2Ceil(1.asInstanceOf[scala.math.BigInt] max n)}
      case n: Double => log2Up(n.toInt)
    }
  }

  def getFF[T<: chisel3.core.Data](sig: T, en: UInt) = {
    val in = sig match {
      case v: Vec[UInt] => v.reverse.reduce { chisel3.util.Cat(_,_) }
      case u: UInt => u
    }

    val ff = Module(new fringe.FF(sig.getWidth))
    ff.io.init := 0.U
    ff.io.in := in
    ff.io.enable := en
    ff.io.out
  }

  def getRetimed[T<:chisel3.core.Data](sig: T, delay: Int) = {
    if (delay == 0) {
      sig
    }
    else {
      if (regression_testing == "1") { // Major hack until someone helps me include the sv file in Driver (https://groups.google.com/forum/#!topic/chisel-users/_wawG_guQgE)
        chisel3.util.ShiftRegister(sig, delay)
      } else {
        val sr = Module(new RetimeWrapper(sig.getWidth, delay))
        sr.io.in := sig.asUInt
        sig.cloneType.fromBits(sr.io.out)
      }
    }
  }

  class PrintStackTraceException extends Exception
  def printStackTrace = {
    try { throw new PrintStackTraceException }
    catch {
      case ste: PrintStackTraceException => ste.printStackTrace
    }
  }
  // def toFix[T <: chisel3.core.Data](a: T): FixedPoint = {
  //   a match {
  //     case aa: FixedPoint => Mux(aa > bb, a, b)
  //     case a => a // TODO: implement for other types
  //   }
  // }
}
