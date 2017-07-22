package spatial.stdlib

import spatial.dsl._
import org.virtualized._
import spatial.metadata._
import argon.core.State

abstract class Matrix[T: Type: Num]()(implicit state: State) {

  protected def sqrtT(x: T): T

  protected def one: T
  protected def zero: T

  protected def vecParF(n: scala.Int) = n (1 -> n)

  sealed trait RegView1 {
    def apply(i: Index): T
  }

  def toRegView1(reg: RegFile1[T]): RegView1 =
    RegId1(reg)

  case class RegId1(reg: RegFile1[T]) extends RegView1 {
    def apply(i: Index) = reg(i)
  }

  case class RegSRAM1(reg: SRAM2[T], pos: Index, ofs: scala.Int) extends RegView1 {
    def apply(i: Index) = reg(pos, i + ofs)
  }
  
  case class RegAdd1(reg1: RegView1, reg2: RegView1) extends RegView1 {
    def apply(i: Index) = reg1(i) + reg2(i)
  }

  case class RegSub1(reg1: RegView1, reg2: RegView1) extends RegView1 {
    def apply(i: Index) = reg1(i) - reg2(i)
  }

  case class RegMult1(reg: RegView1, factor: T) extends RegView1 {
    def apply(i: Index) = reg(i)*factor
  }
  
  case class RegCol(reg: RegView2, col: Int) extends RegView1 {
    def apply(i: Index) = reg(i, col)
  }

  case class RegRow(reg: RegView2, row: Int) extends RegView1 {
    def apply(i: Index) = reg(row, i)
  }

  sealed trait RegView2 {
    def apply(y: Index, x: Index): T
  }  

  def toRegView2(reg: RegFile2[T]): RegView2 =
    RegId2(reg)
  
  case class RegId2(reg: RegFile2[T]) extends RegView2 {
    def apply(y: Index, x: Index) = reg(y, x)
  }

  case class RegSRAM2(reg: SRAM3[T], pos: Index) extends RegView2 {
    def apply(y: Index, x: Index) = reg(pos, y, x)
  }
  

  case class RegVec2(reg: RegView1) extends RegView2 {
    def apply(y: Index, x: Index) = reg(y)
  }
  
  case class RegDiag2(data: T) extends RegView2 {
    def apply(y: Index, x: Index) =
      if (y == x)
        data
      else
        zero
  }  

  
  case class RegAdd2(reg1: RegView2, reg2: RegView2) extends RegView2 {
    def apply(y: Index, x: Index) = reg1(y, x) + reg2(y, x)
  }

  case class RegMult2(reg: RegView2, factor: T) extends RegView2 {
    def apply(y: Index, x: Index) = reg(y, x)*factor
  }
  

  case class RegSub2(reg1: RegView2, reg2: RegView2) extends RegView2 {
    def apply(y: Index, x: Index) = reg1(y, x) - reg2(y, x)
  }

  case class RegView2Diag(reg: RegFile1[T]) extends RegView2 {
    def apply(y: Index, x: Index) =
      if (x == y)
        reg(x)
      else
        zero
  }

  case class RegTranspose2(reg: RegView2) extends RegView2 {
    def apply(y: Index, x: Index) = reg(x, y)
  }
  
  

  object Vec {
    def apply(elems: T*) = {
      val n              = elems.size
      val inits: List[T] = elems.toList
      val reg            = RegId1(RegFile[T](n, inits))
      new Vec(n, reg)
    }
  }

  case class Vec(n: scala.Int, reg: RegView1) {

    val parF = vecParF(n)

    def apply(i: Index) =
      reg(i)

    def binOp(e: Index => (T, T), f: (T, T) => T) = {
      val nreg = RegFile[T](n)
      Foreach(0::n){ i =>
        val (e1, e2) = e(i)
        nreg(i) = f(e1, e2)
      }
      copy(reg = RegId1(nreg))
    }

    def *(x: T) =
      binOp(i => (reg(i), x), _*_)
//      copy(reg = RegMult1(reg, x))

    def +(v: Vec) = {
      require(n == v.n)
      binOp(i => (reg(i), v.reg(i)), _+_)
//      copy(reg = RegAdd1(reg, v.reg))            
    }

    def -(v: Vec) = {
      require(n == v.n)
      binOp(i => (reg(i), v.reg(i)), _-_)
//      copy(reg = RegSub1(reg, v.reg))                  
    }
    
    def dot(v: Vec) = {
      require(n == v.n)            
      val r = Reg[T]
      Reduce(r)(1 by n par parF)(i => reg(i) * v.reg(i))(_+_)
      r.value
    }

    def norm =
      sqrtT(dot(this))
    
  }

  sealed trait Matrix {
    def apply(y: Index, x: Index): T
    
    def h: scala.Int
    def w: scala.Int
    def t: Matrix
    def det: T
    def inv: Matrix
    def *(m: Matrix): Matrix
    def *(y: T): Matrix
    def +(m: Matrix): Matrix
    def -(m: Matrix): Matrix
    def toMatrixDense: MatrixDense

    def loadTo(sram: SRAM2[T], i: Index) =
      Foreach(0::h)(y => sram(i, y) = apply(y, 0))
    def loadTo(sram: SRAM3[T], i: Index) =
      Foreach(0::h, 0::w)((y, x) => sram(i, y, x) = apply(y, x))
    
  }

  object Matrix {
    def apply(h: scala.Int, w: scala.Int, l: List[T]) = {
      val nreg = RegFile[T](h, w, l)
      MatrixDense(h, w, RegId2(nreg))
    }

    def fromSRAM1(h: scala.Int, sram: SRAM2[T], i: Int, ofs: scala.Int = 0) = {
      MatrixDense(h, 1, RegVec2(RegSRAM1(sram, i, ofs)))
    }
    
    def fromSRAM2(h: scala.Int, w:scala.Int, sram: SRAM3[T], i: Int) = {
      MatrixDense(h, w, RegSRAM2(sram, i))
    }
      

    def sparse(h: scala.Int, w: scala.Int, l: IndexedSeq[Option[T]]) = {
      MatrixSparse(h, w, l.sliding(w, w).toIndexedSeq)
    }
    

    def eye(n: scala.Int, v: T) =
      MatrixDiag(n, v)
  }
  case class MatrixDense(h: scala.Int, w: scala.Int, reg: RegView2) extends Matrix {

    def toMatrixDense = this

    def apply(y: Index, x: Index) = {
      reg(y, x)
    }

    def t =
      copy(h = w, w = h, reg = RegTranspose2(reg))

    def binOp(e: (Index, Index) => (T, T), f: (T, T) => T) = {
      val nreg = RegFile[T](h, w)
      Foreach(0::h, 0::w){ (j, i) =>
        val (e1, e2) = e(j, i)
        nreg(j, i) = f(e1, e2)
      }
      copy(reg = RegId2(nreg))
    }
    
    def +(m: Matrix) = {
      require(w == m.w && h == m.h)
      binOp((j,i) => (reg(j,i), m.toMatrixDense.reg(j,i)), _+_)
      //      copy(reg = RegAdd2(reg, m.toMatrixDense.reg))
    }
    def -(m: Matrix) = {
      require(w == m.w && h == m.h)
      binOp((j,i) => (reg(j,i), m.toMatrixDense.reg(j,i)), _-_)      
     // copy(reg = RegSub2(reg, m.toMatrixDense.reg))
    }
    
    def *(x: T) =
      binOp((j,i) => (reg(j,i), x), _*_)            

    
    def *(m: Matrix): Matrix =  {
      require(w == m.h)
      m match {
        case m: MatrixDense => 
          val nreg = RegFile[T](h, m.w)
          Foreach(0::h) { y =>
            Foreach(0::m.w) { x =>
              nreg(y, x) = row(y).dot(m.col(x))
            }
          }
          MatrixDense(h, m.w, RegId2(nreg))
        case m: MatrixSparse =>
          this*(m.toMatrixDense)
        case m: MatrixDiag =>
          *(m.factor)
      }
    }
    

    def col(x: Index) =
      Vec(h, RegCol(reg, x))

    def row(y: Index) =
      Vec(w, RegRow(reg, y))
    

    def det = {
      val (a11, a12, a13) = (apply(0, 0), apply(0, 1), apply(0, 2))
      val (a21, a22, a23) = (apply(1, 0), apply(1, 1), apply(1, 2))
      val (a31, a32, a33) = (apply(2, 0), apply(2, 1), apply(2, 2))
      a11 * (a33 * a22 - a32 * a23) - a21 * (a33 * a12 - a32 * a13) + a31 * (a23 * a12 - a22 * a13)
    }

    def inv = {
      val (a11, a12, a13) = (apply(0, 0), apply(0, 1), apply(0, 2))
      val (a21, a22, a23) = (apply(1, 0), apply(1, 1), apply(1, 2))
      val (a31, a32, a33) = (apply(2, 0), apply(2, 1), apply(2, 2))

      val A = Matrix(h, w, List(
        a33*a22-a32*a23, -(a33*a12-a32*a13), a23*a12-a22*a13,
        -(a33*a21-a31*a23), a33*a11-a31*a13, -(a23*a11-a21*a13),
        a32*a21-a31*a22, -(a32*a11-a31*a12), a22*a11-a21*a12)
      )
      
      A*(one / det)
    }

   
  }


  case class MatrixSparse(h: scala.Int, w: scala.Int, data: IndexedSeq[IndexedSeq[Option[T]]]) extends Matrix {

    lazy val toMatrixDense: MatrixDense =
      Matrix(h, w, data.flatten.toList.map(x => x.getOrElse(zero)))

    def apply(y: Index, x: Index) =
      toMatrixDense.apply(y, x)

    def t =
      copy(h = w, w = h, data = data.transpose)

    def det: T =
      toMatrixDense.det

    def inv: Matrix =
      toMatrixDense.inv

    def +(m: Matrix) =
      toMatrixDense + m.toMatrixDense
    //      MatrixDense(h, w, RegAdd2(toMatrixDense.reg, m.toMatrixDense.reg))

    def -(m: Matrix) =
      toMatrixDense - m.toMatrixDense      
    //MatrixDense(h, w, RegSub2(toMatrixDense.reg, m.toMatrixDense.reg))
    
    def *(y: T) =
      copy(data = data.map(_.map(_.map(_*y))))

    def *(m: Matrix) = {
      require(w == m.h)
      m match {
        case m: MatrixDense => 
          val nreg = RegFile[T](h, m.w)
          Pipe {
            List.tabulate(h){ y =>
              List.tabulate(m.w){ x =>
                val sum = List.tabulate(w){ i =>
                  data(y)(i).map(t => m(i, x) * t)
                }
                  .filter(_.isDefined)
                  .map(_.get)
                  .reduce(_+_)
                Pipe { nreg(y, x) = sum }
              }
            }
            ()
          }
          MatrixDense(h, m.w, RegId2(nreg))
        case m: MatrixSparse =>
          this*(m.toMatrixDense)
        case m: MatrixDiag =>
          this*m.factor
      }
    }
      
  }

  case class MatrixDiag(n: scala.Int, factor: T) extends Matrix {

    lazy val toMatrixDense =
      MatrixDense(n, n, RegDiag2(factor))

    def apply(y: Index, x: Index) =
      lift(factor)

    def h = n
    def w = n

    def t =
      this

    def +(m: Matrix) =
      toMatrixDense + m.toMatrixDense
    //      MatrixDense(h, w, RegAdd2(toMatrixDense.reg, m.toMatrixDense.reg))

    def -(m: Matrix) =
      toMatrixDense - m.toMatrixDense      
    //MatrixDense(h, w, RegSub2(toMatrixDense.reg, m.toMatrixDense.reg))
    
    def *(y: T) =
      copy(factor = y*factor)

    def *(m: Matrix) =
      m*factor

    def inv =
      *(sqrtT(factor))

    def det = {
      factor**n
    }

  }
  

}
