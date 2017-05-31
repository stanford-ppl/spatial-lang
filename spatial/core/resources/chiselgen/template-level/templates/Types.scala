package types

import chisel3._
import templates._
import templates.ops._
import chisel3.util

import scala.language.experimental.macros

import chisel3.internal._
import chisel3.internal.firrtl._
import chisel3.internal.sourceinfo._
import chisel3.internal.firrtl.PrimOp.AsUIntOp

// Raw numbers
class RawBits(b: Int) extends Bundle { 
	val raw = UInt(b.W)

	// Conversions
	def storeFix(dst: FixedPoint): Unit = { 
	  assert(dst.d + dst.f == b)
	  dst.number := raw
	}

	// Arithmetic

  override def cloneType = (new RawBits(b)).asInstanceOf[this.type] // See chisel3 bug 358
}

// Fixed point numbers
class FixedPoint(val s: Boolean, val d: Int, val f: Int) extends Bundle {
	// Overloaded
	def this(s: Int, d: Int, f: Int) = this(s == 1, d, f)
	def this(tuple: (Boolean, Int, Int)) = this(tuple._1, tuple._2, tuple._3)

	def apply(msb:Int, lsb:Int): UInt = this.number(msb,lsb)
	def apply(bit:Int): Bool = this.number(bit)

	// Properties
	val number = UInt((d + f).W)
	val debug_overflow = Bool()

	def raw():UInt = number
	def r():UInt = number
	// Conversions
	def storeRaw(dst: RawBits): Unit = {
		dst.raw := number
	}

	def msb():Bool = number(d+f-1)

	def cast(dst: FixedPoint, rounding: String = "truncate", saturating: String = "lazy", expect_neg: Bool = false.B, expect_pos: Bool = false.B): Unit = {
		val has_frac = dst.f > 0
		val has_dec = dst.d > 0
		val up_frac = dst.f max 1
		val up_dec = dst.d max 1
		val tmp_frac = Wire(UInt(up_frac.W))
		val new_frac = Wire(UInt(up_frac.W))
		val new_dec = Wire(UInt(up_dec.W))
		if (!has_frac) { tmp_frac := 0.U(1.W) }
		if (!has_frac) { new_frac := 0.U(1.W) }
		if (!has_dec) { new_dec := 0.U(1.W) }

		// Compute new frac part
		// val new_frac = Wire(UInt(dst.f.W))
		val shave_f = f - dst.f
		val shave_d = d - dst.d
		if (has_frac) {
			if (dst.f < f) { // shrink decimals
				rounding match {
					case "truncate" => 
						tmp_frac := number(shave_f + dst.f - 1, shave_f)
						// (0 until dst.f).map{ i => number(shave_f + i)*scala.math.pow(2,i).toInt.U }.reduce{_+_}
					case "unbiased" => 
						val prng = Module(new PRNG(scala.math.abs(scala.util.Random.nextInt)))
						prng.io.en := true.B
						val salted = number + prng.io.output(shave_f-1,0)
						tmp_frac := salted(shave_f + dst.f -1, shave_f)
					// case "biased" =>
					// 	Mux(number(shave_f-1), number + 1.U(shave_f.W) << (shave_f-1), number(shave_f + dst.f - 1, shave_f)) // NOT TESTED!!
					case _ =>
						tmp_frac := 0.U(dst.f.W)
						// TODO: throw error
				}
			} else if (dst.f > f) { // expand decimals
				val expand = dst.f - f
				if (f > 0) { tmp_frac := util.Cat(number(f-1,0), 0.U(expand.W))} else {tmp_frac := 0.U(expand.W)}
				// (0 until dst.f).map{ i => if (i < expand) {0.U} else {number(i - expand)*scala.math.pow(2,i).toInt.U}}.reduce{_+_}
			} else { // keep same
				tmp_frac := number(dst.f-1,0)
				// (0 until dst.f).map{ i => number(i)*scala.math.pow(2,i).toInt.U }.reduce{_+_}
			}
		}

		// Compute new dec part (concatenated with frac part from before)
		if (has_dec) {
			if (dst.d < d) { // shrink decimals
				saturating match { 
					case "lazy" =>
						dst.debug_overflow := (0 until shave_d).map{i => number(d + f - 1 - i)}.reduce{_||_}
						new_frac := tmp_frac
						new_dec := number(dst.d + f - 1, f)
						// (0 until dst.d).map{i => number(f + i) * scala.math.pow(2,i).toInt.U}.reduce{_+_}
					case "saturation" =>
						val sign = number.msb
						val overflow = (sign & expect_pos) | (~sign & expect_neg)
					  val not_saturated = ( number(f+d-1,f+d-1-shave_d) === 0.U(shave_d.W) ) | ( ~number(f+d-1,f+d-1-shave_d) === 0.U(shave_d.W) )

					  val saturated_frac = Mux(expect_pos, 
					  			util.Cat(util.Fill(up_frac, true.B)), 
					  			Mux(expect_neg, 0.U(up_frac.W), 0.U(up_frac.W)))
					  val saturated_dec = Mux(expect_pos, 
					  			util.Cat((0 until up_frac).map{i => if (i == 0 & (dst.s | s)) false.B else true.B}), 
					  			Mux(expect_neg, 1.U((dst.d).W) << (dst.d-1), 1.U((dst.d).W) << (dst.d-1))) 

					  new_frac := Mux(not_saturated & ~overflow, tmp_frac, saturated_frac)
					  new_dec := Mux(not_saturated & ~overflow, number(dst.d + f - 1, f), saturated_dec)
					case _ =>
						new_frac := tmp_frac
						new_dec := 0.U(dst.d.W)
				}
			} else if (dst.d > d) { // expand decimals
				val expand = dst.d - d
				val sgn_extend = if (s) { number(d+f-1) } else {0.U(1.W)}
				new_frac := tmp_frac
				new_dec := util.Cat(util.Fill(expand, sgn_extend), number(f+d-1, f))
				// (0 until dst.d).map{ i => if (i >= dst.d - expand) {sgn_extend*scala.math.pow(2,i).toInt.U} else {number(i+f)*scala.math.pow(2,i).toInt.U }}.reduce{_+_}
			} else { // keep same
				new_frac := tmp_frac
				new_dec := number(f+d-1, f)
				// (0 until dst.d).map{ i => number(i + f)*scala.math.pow(2,i).toInt.U }.reduce{_+_}
			}

		}

		if (has_dec & has_frac) {
			dst.number := chisel3.util.Cat(new_dec, new_frac)	
		} else if (has_dec & !has_frac) {
			dst.number := new_dec		
		} else if (!has_dec & has_frac) {
			dst.number := new_frac
		}
		
		
		// dst.number := util.Cat(new_dec, new_frac) //new_frac + new_dec*(scala.math.pow(2,dst.f).toInt.U)

	}
	

	def raw_dec[T] (): UInt = {
		this.number(d+f-1, f)
	}
	def raw_frac[T] (): UInt = {
		this.number(f, 0)
	}

	// Arithmetic
	override def connect (rawop: Data)(implicit sourceInfo: SourceInfo, connectionCompileOptions: chisel3.core.CompileOptions): Unit = {
		rawop match {
			case op: FixedPoint =>
				number := op.number
			case op: UInt =>
				number := op
		}
	}

	def +[T] (rawop: T, rounding:String = "truncate", saturating:String = "lazy"): FixedPoint = {
		rawop match {
			case op: FixedPoint => 
				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d) + 1, scala.math.max(op.f, f))
				val return_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val full_result = Wire(new FixedPoint(upcasted_type))
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				// Do upcasted operation
				this.cast(lhs)
				op.cast(rhs)
				full_result.number := lhs.number + rhs.number
				// Downcast to result
				val result = Wire(new FixedPoint(return_type))
				val expect_neg = if (op.s | s) {lhs.msb & rhs.msb} else false.B
				val expect_pos = if (op.s | s) {~lhs.msb & ~rhs.msb} else true.B
				full_result.cast(result, rounding = rounding, saturating = saturating, expect_neg = expect_neg, expect_pos = expect_pos)
				result
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this + op_cast
		}
	}
	def <+>[T] (rawop: T): FixedPoint = {this.+(rawop, saturating = "saturation")}

	def -[T] (rawop: T, rounding:String = "truncate", saturating:String = "lazy"): FixedPoint = {
		rawop match { 
			case op: FixedPoint => 
				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d) + 1, scala.math.max(op.f, f))
				val return_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val full_result = Wire(new FixedPoint(upcasted_type))
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				// Do upcasted operation
				this.cast(lhs)
				op.cast(rhs)
				full_result.number := lhs.number - rhs.number
				// Downcast to result
				val result = Wire(new FixedPoint(return_type))
				val expect_neg = if (op.s | s) {(lhs.msb & ~rhs.msb)} else true.B
				val expect_pos = if (op.s | s) {(~lhs.msb & rhs.msb)} else false.B
				full_result.cast(result, rounding = rounding, saturating = saturating, expect_neg = expect_neg, expect_pos = expect_pos)
				result
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this - op_cast

		}
	}
	def <->[T] (rawop: T): FixedPoint = {this.-(rawop, saturating = "saturation")}

	def *[T] (rawop: T, rounding:String = "truncate", saturating:String = "lazy"): FixedPoint = {
		rawop match { 
			case op: FixedPoint => 
				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, op.d + d, op.f + f)
				val return_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val full_result = Wire(new FixedPoint(upcasted_type))
				// Do upcasted operation
				val expanded_self = util.Cat(util.Fill(op.d+op.f, this.msb), this.number)
				val expanded_op = util.Cat(util.Fill(d+f, op.msb), op.number)
				full_result.number := expanded_self * expanded_op

				// Downcast to result
				val result = Wire(new FixedPoint(return_type))
				val expect_neg = if (op.s | s) {(this.msb ^ op.msb)} else false.B
				val expect_pos = if (op.s | s) {~(this.msb ^ op.msb)} else true.B
				full_result.cast(result, rounding = rounding, saturating = saturating, expect_neg = expect_neg, expect_pos = expect_pos)
				result
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this * op_cast
			}
	}
	def *&[T] (rawop: T): FixedPoint = {this.*(rawop, rounding = "unbiased")}
	def <*&>[T] (rawop: T): FixedPoint = {this.*(rawop, rounding = "unbiased", saturating = "saturation")}
	def <*>[T] (rawop: T): FixedPoint = {this.*(rawop, saturating = "saturation")}

	def /[T] (rawop: T, rounding:String = "truncate", saturating:String = "lazy"): FixedPoint = {
		rawop match { 
			case op: FixedPoint => 
				if (op.f + f == 0) {
					if (op.s | s) {
						(this.number.asSInt / op.number.asSInt).FP(false, scala.math.max(op.d, d), scala.math.max(op.f, f))
					} else {
						(this.number / op.number).FP(false, scala.math.max(op.d, d), scala.math.max(op.f, f))
					}
				} else {
					// Compute upcasted type and return type
					val upcasted_type = (op.s | s, op.d + d, op.f + f + 1)
					val return_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
					// Get upcasted operators
					val full_result = Wire(new FixedPoint(upcasted_type))
					// Do upcasted operation
					// TODO: Should go back and clean this a little, eventually..
					if (op.s | s) {
						val numerator = util.Cat(this.number, 0.U((op.f+f+1).W)).asSInt
						val denominator = op.number.asSInt
						full_result.number := (numerator/denominator).asUInt
					} else {
						val numerator = util.Cat(this.number, 0.U((op.f+f+1).W))
						val denominator = op.number
						full_result.number := (numerator/denominator) // Not sure why we need the +1 in pow2
					}
					// Downcast to result
					val result = Wire(new FixedPoint(return_type))
					val expect_neg = if (op.s | s) {(op.msb ^ this.msb)} else false.B
					val expect_pos = if (op.s | s) {~(this.msb ^ op.msb)} else true.B
					full_result.cast(result, rounding = rounding, saturating = saturating, expect_neg = expect_neg, expect_pos = expect_pos)
					result					
				}
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this / op_cast
		}
	}
	def /&[T] (rawop: T): FixedPoint = {this./(rawop, rounding = "unbiased")}
	def </&>[T] (rawop: T): FixedPoint = {this./(rawop, rounding = "unbiased", saturating = "saturation")}
	def </>[T] (rawop: T): FixedPoint = {this./(rawop, saturating = "saturation")}

	def %[T] (rawop: T): FixedPoint = {
		rawop match { 
			case op: FixedPoint => 
				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, op.d + d, op.f + f)
				val return_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val full_result = Wire(new FixedPoint(upcasted_type))
				// Do upcasted operation
				full_result.number := this.number % op.number // Not sure why we need the +1 in pow2
				// Downcast to result
				val result = Wire(new FixedPoint(return_type))
				full_result.cast(result)
				result
			case op: UInt =>
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this % op_cast

		}
	}

	def floor[T] (): FixedPoint = {
		val return_type = (s, d, f)
		val result = Wire(new FixedPoint(return_type))
		result.r := util.Cat(this.raw_dec, util.Fill(f, false.B))			
		result
	}

	def ceil[T] (): FixedPoint = {
		val return_type = (s, d, f)
		val result = Wire(new FixedPoint(return_type))
		val stay = this.raw_frac === 0.U
		result.r := Mux(stay, this.number.r, util.Cat(this.raw_dec + 1.U, util.Fill(f, false.B)))
		result
	}

	def >>[T] (shift: Int, sgnextend: Boolean = false): FixedPoint = {
		val return_type = (s, d, f)
		val result = Wire(new FixedPoint(return_type))
		if (sgnextend) {
			result.r := util.Cat(util.Fill(shift, number.msb), number(d+f-1, shift))
		} else {
			result.r := this.number >> shift			
		}
		result
	}
	def >>>[T] (shift: Int): FixedPoint = {this.>>(shift, sgnextend = true)}

	def <<[T] (shift: Int): FixedPoint = {
		val return_type = (s, d, f)
		val result = Wire(new FixedPoint(return_type))
		result.r := this.number << shift			
		result
	}

	def <[T] (rawop: T): Bool = { // TODO: Probably completely wrong for signed fixpts
		rawop match { 
			case op: FixedPoint => 

				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				this.cast(lhs)
				op.cast(rhs)
				if (op.s | s) {lhs.number.asSInt < rhs.number.asSInt} else {lhs.number < rhs.number}
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this < op_cast
			case op: SInt => 
				val op_cast = Utils.FixedPoint(true, op.getWidth max this.d, this.f, op.asUInt)
				number < op_cast
		}
	}

	def ^[T] (rawop: T): FixedPoint = { 
		rawop match { 
			case op: FixedPoint => 

				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				val return_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				val res = Wire(new FixedPoint(return_type))
				this.cast(lhs)
				op.cast(rhs)
				res.r := lhs.r ^ rhs.r
				res
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this ^ op_cast
			case op: SInt => 
				val op_cast = Utils.FixedPoint(true, op.getWidth max this.d, this.f, op.asUInt)
				number ^ op_cast
		}
	}

	def &[T] (rawop: T): FixedPoint = { 
		rawop match { 
			case op: FixedPoint => 

				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				val return_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				val res = Wire(new FixedPoint(return_type))
				this.cast(lhs)
				op.cast(rhs)
				res.r := lhs.r & rhs.r
				res
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this & op_cast
			case op: SInt => 
				val op_cast = Utils.FixedPoint(true, op.getWidth max this.d, this.f, op.asUInt)
				this & op_cast
		}
	}

	def |[T] (rawop: T): FixedPoint = { 
		rawop match { 
			case op: FixedPoint => 

				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				val return_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				val res = Wire(new FixedPoint(return_type))
				this.cast(lhs)
				op.cast(rhs)
				res.r := lhs.r | rhs.r
				res
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this | op_cast
			case op: SInt => 
				val op_cast = Utils.FixedPoint(true, op.getWidth max this.d, this.f, op.asUInt)
				this | op_cast
		}
	}

	def <=[T] (rawop: T): Bool = { // TODO: Probably completely wrong for signed fixpts
		rawop match { 
			case op: FixedPoint => 

				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				this.cast(lhs)
				op.cast(rhs)
				if (op.s | s) {lhs.number.asSInt <= rhs.number.asSInt} else {lhs.number <= rhs.number}
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this <= op_cast
			case op: SInt => 
				val op_cast = Utils.FixedPoint(true, op.getWidth max this.d, this.f, op.asUInt)
				number <= op_cast
		}
	}
	
	def >[T] (rawop: T): Bool = { // TODO: Probably completely wrong for signed fixpts
		rawop match { 
			case op: FixedPoint => 
				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				this.cast(lhs)
				op.cast(rhs)
				if (op.s | s) {lhs.number.asSInt > rhs.number.asSInt} else {lhs.number > rhs.number}
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this > op_cast
			case op: SInt => 
				val op_cast = Utils.FixedPoint(true, op.getWidth max this.d, this.f, op.asUInt)
				number > op_cast
		}
	}

	def >=[T] (rawop: T): Bool = { // TODO: Probably completely wrong for signed fixpts
		rawop match { 
			case op: FixedPoint => 
				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				this.cast(lhs)
				op.cast(rhs)
				if (op.s | s) {lhs.number.asSInt >= rhs.number.asSInt} else {lhs.number >= rhs.number}
			case op: UInt => 
				val op_cast = Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
				this >= op_cast
			case op: SInt => 
				val op_cast = Utils.FixedPoint(true, op.getWidth max this.d, this.f, op.asUInt)
				number >= op_cast
		}
	}

	def === [T](r: T): Bool = { // TODO: Probably completely wrong for signed fixpts
		r match {
			case op: FixedPoint =>
				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				this.cast(lhs)
				op.cast(rhs)
				lhs.number === rhs.number
			case op: UInt => 
				// Compute upcasted type and return type
				val upcasted_type = (s, d, f)
				// Get upcasted operators
				val rhs = Utils.FixedPoint(s,d,f, op)
				number === rhs.number
			case op: SInt => 
				val op_cast = Utils.FixedPoint(true, op.getWidth max this.d, this.f, op.asUInt)
				number === op_cast
		}
	}

	def =/= [T](r: T): Bool = { // TODO: Probably completely wrong for signed fixpts
		r match {
			case op: FixedPoint =>
				// Compute upcasted type and return type
				val upcasted_type = (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
				// Get upcasted operators
				val lhs = Wire(new FixedPoint(upcasted_type))
				val rhs = Wire(new FixedPoint(upcasted_type))
				this.cast(lhs)
				op.cast(rhs)
				lhs.number =/= rhs.number
			case op: UInt => 
				// Compute upcasted type and return type
				val upcasted_type = (s, d, f)
				// Get upcasted operators
				val rhs = Utils.FixedPoint(s,d,f, op)
				number =/= rhs.number
			case op: SInt => 
				val op_cast = Utils.FixedPoint(true, op.getWidth max this.d, this.f, op.asUInt)
				number =/= op_cast
		}
	}

	def isNeg (): Bool = {
		Mux(s.B && number(f+d-1), true.B, false.B)
	}

    def unary_-() : FixedPoint = {
    	val neg = Wire(new FixedPoint(s,d,f))
    	neg.number := ~number + 1.U
    	neg
    }



	// def * (op: FixedPoint): FixedPoint = {
	// 	// Compute upcasted type
	// 	val sign = op.s | s
	// 	val d_prec = op.d + d
	// 	val f_prec = op.f + f
	// 	// Do math on UInts
	// 	val r1 = Wire(new RawBits(d_prec + f_prec))
	// 	this.storeRaw(r1)
	// 	val r2 = Wire(new RawBits(d_prec + f_prec))
	// 	op.storeRaw(r2)
	// 	val rawResult = r1 * r2
	// 	// Store to FixedPoint result
	// 	val result = Wire(new FixedPoint(sign, scala.math.max(op.d, d), scala.math.max(op.f, f)))
	// 	rawResult.storeFix(result)
	// 	result.debug_overflow := Mux(rawResult.raw(0), true.B, false.B)
	// 	result
	// }

    override def cloneType = (new FixedPoint(s,d,f)).asInstanceOf[this.type] // See chisel3 bug 358

}

// Testing
class FixedPointTester(val s: Boolean, val d: Int, val f: Int) extends Module {
	def this(tuple: (Boolean, Int, Int)) = this(tuple._1, tuple._2, tuple._3)
	val io = IO( new Bundle {
		val num1 = new RawBits(d+f).asInput
		val num2 = new RawBits(d+f).asInput

		val add_result = new RawBits(d+f).asOutput
		val prod_result = new RawBits(d+f).asOutput
		val sub_result = new RawBits(d+f).asOutput
		val quotient_result = new RawBits(d+f).asOutput
	})

	val fix1 = Wire(new FixedPoint(s,d,f))
	io.num1.storeFix(fix1)
	val fix2 = Wire(new FixedPoint(s,d,f))
	io.num2.storeFix(fix2)
	val sum = fix1 + fix2
	sum.storeRaw(io.add_result)
	val prod = fix1 * fix2
	prod.storeRaw(io.prod_result)
	val sub = fix1 - fix2
	sub.storeRaw(io.sub_result)
	val quotient = fix1 / fix2
	quotient.storeRaw(io.quotient_result)





}



// import Node._
// import ChiselError._

// /** Factory methods for [[Chisel.Fixed Fixed]] */
// object Fixed {
//     /** Convert a double to fixed point with a specified fractional width
//       * @param x Double to convert
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A BigInt representing the bits in the fixed point
//       */
//     def toFixed(x : Double, fracWidth : Int) : BigInt = BigInt(scala.math.round(x*scala.math.pow(2, fracWidth)))
//     /** Convert a Float to fixed point with a specified fractional width
//       * @param x Float to convert
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A BigInt representing the bits in the fixed point
//       */
//     def toFixed(x : Float, fracWidth : Int) : BigInt = BigInt(scala.math.round(x*scala.math.pow(2, fracWidth)))
//     /** Convert an Int to fixed point with a specified fractional width
//       * @param x Double to convert
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A BigInt representing the bits in the fixed point
//       */
//     def toFixed(x : Int, fracWidth : Int) : BigInt = BigInt(scala.math.round(x*scala.math.pow(2, fracWidth)))

//     /** Create a Fixed [[Chisel.Node]] with specified width and fracWidth
//       * @param x An Int to convert to fixed point
//       * @param width the total number of bits to use in the representation
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A fixed node with the specified parameters
//       */
//     def apply(x : Int, width : Int, fracWidth : Int) : Fixed = apply(toFixed(x, fracWidth), width, fracWidth)
//     /** Create a Fixed [[Chisel.Node]] with specified width and fracWidth
//       * @param x An Float to convert to fixed point
//       * @param width the total number of bits to use in the representation
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A fixed node with the specified parameters
//       */
//     def apply(x : Float, width : Int, fracWidth : Int) : Fixed = apply(toFixed(x, fracWidth), width, fracWidth)
//     /** Create a Fixed [[Chisel.Node]] with specified width and fracWidth
//       * @param x An Double to convert to fixed point
//       * @param width the total number of bits to use in the representation
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A fixed node with the specified parameters
//       */
//     def apply(x : Double, width : Int, fracWidth : Int) : Fixed = apply(toFixed(x, fracWidth), width, fracWidth)
//     /** Create a Fixed [[Chisel.Node]] with specified width and fracWidth
//       * @param x An BigInt to use literally as the fixed point bits
//       * @param width the total number of bits to use in the representation
//       * @param fracWidth the integer fractional width to use
//       * @return A fixed node with the specified parameters
//       */
//     def apply(x : BigInt, width : Int, fracWidth : Int) : Fixed =  {
//       val res = Lit(x, width){Fixed()}
//       res.fractionalWidth = fracWidth
//       res
//     }

//     /** Create a Fixed I/O [[Chisel.Node]] with specified width and fracWidth
//       * @param dir Direction of I/O for the node, eg) INPUT or OUTPUT
//       * @param width the total number of bits to use in the representation
//       * @param fracWidth the integer fractional width to use
//       * @return A fixed node with the specified parameters
//       */
//     def apply(dir : IODirection = null, width : Int = -1, fracWidth : Int = -1) : Fixed = {
//         val res = new Fixed(fracWidth);
//         res.create(dir, width)
//         res
//     }
// }

// /** A Fixed point data type
//   * @constructor Use [[Chisel.Fixed$ Fixed]] object to create rather than this class directly */
// class Fixed(var fractionalWidth : Int = 0) extends Bits with Num[Fixed] {
//     type T = Fixed

//     /** Convert a Node to a Fixed data type with the same fractional width as this instantiation */
//     override def fromNode(n : Node): this.type = {
//         val res = Fixed(OUTPUT).asTypeFor(n).asInstanceOf[this.type]
//         res.fractionalWidth = this.getFractionalWidth()
//         res
//     }

//     /** Create a Fixed representation from an Int */
//     override def fromInt(x : Int) : this.type = Fixed(x, this.getWidth(), this.getFractionalWidth()).asInstanceOf[this.type]

//     /** clone this Fixed instantiation */
//     override def cloneType: this.type = Fixed(this.dir, this.getWidth(), this.getFractionalWidth()).asInstanceOf[this.type];

//     override protected def colonEquals(that : Bits): Unit = that match {
//       case f: Fixed => {
//         val res = if((f.getWidth() == this.getWidth()*2) && (f.getFractionalWidth() == this.getFractionalWidth()*2)) {
//           truncate(f, this.getFractionalWidth())
//         } else {
//           checkAligned(f)
//           f
//         }
//         super.colonEquals(res)
//       }
//       case _ => illegalAssignment(that)
//     }

//     def getFractionalWidth() : Int = this.fractionalWidth

//     private def truncate(f : Fixed, truncateAmount : Int) : Fixed = fromSInt(f.toSInt >> UInt(truncateAmount))
//     private def truncate(f : SInt, truncateAmount : Int) : SInt = f >> UInt(truncateAmount)

//     /** Ensure two Fixed point data types have the same fractional width, Error if not */
//     private def checkAligned(b : Fixed) {
//       if(this.getFractionalWidth() != b.getFractionalWidth()) ChiselError.error(this.getFractionalWidth() + " Fractional Bits does not match " + b.getFractionalWidth())
//       if(this.getWidth() != b.getWidth()) ChiselError.error(this.getWidth() + " Width does not match " + b.getWidth())
//     }

//     /** Convert a SInt to a Fixed by reinterpreting the Bits */
//     private def fromSInt(s : SInt, width : Int = this.getWidth(), fracWidth : Int = this.getFractionalWidth()) : Fixed = {
//         val res = chiselCast(s){Fixed()}
//         res.fractionalWidth = fracWidth
//         res.width = width
//         res
//     }

//     // Order Operators
//     def > (b : Fixed) : Bool = {
//         checkAligned(b)
//         this.toSInt > b.toSInt
//     }

//     def < (b : Fixed) : Bool = {
//         checkAligned(b)
//         this.toSInt < b.toSInt
//     }

//     def >= (b : Fixed) : Bool = {
//         checkAligned(b)
//         this.toSInt >= b.toSInt
//     }

//     def <= (b : Fixed) : Bool = {
//         checkAligned(b)
//         this.toSInt <= b.toSInt
//     }

//     def === (b : Fixed) : Bool = {
//         checkAligned(b)
//         this.toSInt === b.toSInt
//     }

//     def >> (b : UInt) : Fixed = {
//         fromSInt(this.toSInt >> b)
//     }

//     // Arithmetic Operators
//     def unary_-() : Fixed = Fixed(0, this.getWidth(), this.getFractionalWidth()) - this

//     def + (b : Fixed) : Fixed = {
//         checkAligned(b)
//         fromSInt(this.toSInt + b.toSInt)
//     }

//     def - (b : Fixed) : Fixed = {
//         checkAligned(b)
//         fromSInt(this.toSInt - b.toSInt)
//     }

//     /** Multiply increasing the Bit Width */
//     def * (b : Fixed) : Fixed = {
//         checkAligned(b)
//         val temp = this.toSInt * b.toSInt
//         fromSInt(temp, temp.getWidth(), this.getFractionalWidth()*2)
//     }

//     /** Multiply with one bit of rounding */
//     def *& (b : Fixed) : Fixed = {
//         checkAligned(b)
//         val temp = this.toSInt * b.toSInt
//         val res = temp + ((temp & UInt(1)<<UInt(this.getFractionalWidth()-1))<<UInt(1))
//         fromSInt(truncate(res, this.getFractionalWidth()))
//     }

//     /** Multiply truncating the result to the same Fixed format */
//     def *% (b : Fixed) : Fixed = {
//         checkAligned(b)
//         val temp = this.toSInt * b.toSInt
//         fromSInt(truncate(temp, this.getFractionalWidth()))
//     }

//     def / (b : Fixed) : Fixed = {
//         checkAligned(b)
//         fromSInt((this.toSInt << UInt(this.getFractionalWidth())) / b.toSInt)
//     }

//     /** This is just the modulo of the two fixed point bit representations changed into SInt and operated on */
//     def % (b : Fixed) : Fixed = {
//       checkAligned(b)
//       fromSInt(this.toSInt % b.toSInt)
//     }
// }
