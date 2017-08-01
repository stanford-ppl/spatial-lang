package spatial.stdlib

import spatial.dsl._
import org.virtualized._
import spatial.metadata._
import argon.core.State

abstract class Matrix[T: Type: Num]()(implicit state: State) {

  def IN_PLACE: scala.Boolean

  protected def sqrtT(x: T): T

  protected def one: T
  protected def zero: T

  protected def vecParF(n: scala.Int) = n (1 -> n)

  sealed trait RegView1 {
    def apply(i: Index)(implicit sc: SourceContext): T
    def update(i: Index, v: T)(implicit sc: SourceContext): Unit
  }

  def toRegView1(reg: RegFile1[T]): RegView1 =
    RegId1(reg)

  case class RegId1(reg: RegFile1[T]) extends RegView1 {
    def apply(i: Index)(implicit sc: SourceContext) = 
      reg(i)
    def update(i: Index, v: T)(implicit sc: SourceContext) =
      reg(i) = v
  }

  case class RegSRAM1(reg: SRAM2[T], pos: Index, ofs: scala.Int) extends RegView1 {
    def apply(i: Index)(implicit sc: SourceContext) =
      reg(pos, i + ofs)
    def update(i: Index, v: T)(implicit sc: SourceContext) =
      reg(pos, i + ofs) = v
    
  }
  
  case class RegMult1(reg: RegView1, factor: T) extends RegView1 {
    def apply(i: Index)(implicit sc: SourceContext) =
      reg(i)*factor
    def update(i: Index, v: T)(implicit sc: SourceContext) =
      reg(i) = v
      
  }
  
  case class RegCol(reg: RegView2, col: Int) extends RegView1 {
    def apply(i: Index)(implicit sc: SourceContext) =
      reg(i, col)
    def update(i: Index, v: T)(implicit sc: SourceContext) =
      reg(i, col) = v      
  }

  case class RegRow(reg: RegView2, row: Int) extends RegView1 {
    def apply(i: Index)(implicit sc: SourceContext) =
      reg(row, i)
    def update(i: Index, v: T)(implicit sc: SourceContext) =
      reg(row, i) = v
      
  }

  sealed trait RegView2 {
    def apply(y: Index, x: Index)(implicit sc: SourceContext): T
    def update(y: Index, x: Index, v: T)(implicit sc: SourceContext): Unit      
  }  

  def toRegView2(reg: RegFile2[T]): RegView2 =
    RegId2(reg)
  
  case class RegId2(reg: RegFile2[T]) extends RegView2 {
    def apply(y: Index, x: Index)(implicit sc: SourceContext) =
      reg(y, x)
    def update(y: Index, x: Index, v: T)(implicit sc: SourceContext) =
      reg(y, x) = v      

  }

  case class RegSRAM2(reg: SRAM3[T], pos: Index) extends RegView2 {
    def apply(y: Index, x: Index)(implicit sc: SourceContext) = reg(pos, y, x)
    def update(y: Index, x: Index, v: T)(implicit sc: SourceContext) =
      reg(pos, y, x) = v      
    
  }
  

  case class RegVec2(reg: RegView1) extends RegView2 {
    def apply(y: Index, x: Index)(implicit sc: SourceContext) = reg(y)
    def update(y: Index, x: Index, v: T)(implicit sc: SourceContext) =
      reg(y) = v      
    
  }
  
  case class RegDiag2(data: T) extends RegView2 {
    @virtualize def apply(y: Index, x: Index)(implicit sc: SourceContext) =
      mux(y == x, data, zero)

    def update(y: Index, x: Index, v: T)(implicit sc: SourceContext) =
      ???    
  }  
  

  case class RegMult2(reg: RegView2, factor: T) extends RegView2 {
    def apply(y: Index, x: Index)(implicit sc: SourceContext) = reg(y, x)*factor
    def update(y: Index, x: Index, v: T)(implicit sc: SourceContext) =
      reg(y, x) = v
    
  }  

  case class RegTranspose2(reg: RegView2) extends RegView2 {
    def apply(y: Index, x: Index)(implicit sc: SourceContext) = reg(x, y)
    def update(y: Index, x: Index, v: T)(implicit sc: SourceContext) =
      reg(x, y) = v
    
  }

  
  

  object Vec {
    def apply(n: scala.Int, vec: Vector[T]) = {
      val nreg = RegFile[T](n)
      nreg <<= vec
      val regv            = RegId1(nreg)
      new Vec(n, regv)
    }
    def apply(elems: T*) = {
      val n              = elems.size
      val inits: List[T] = elems.toList
      val nreg = RegFile[T](n)
//      val vec = Vector.LittleEndian(elems:_*)
//      nreg <<= vec
      Pipe {
        List.tabulate(n){i =>        
          Pipe { nreg(i) = inits(i) }
        }
        ()
      }
      val regv            = RegId1(nreg)
      new Vec(n, regv)
    }
  }

  case class Vec(n: scala.Int, reg: RegView1) {

    val parF = vecParF(n)

    def apply(i: Index)(implicit sc: SourceContext) =
      reg(i)

    def binOp(e: Index => (T, T), f: (T, T) => T)(implicit sc: SourceContext) = {
      val nreg = RegFile[T](n)
      Foreach(0::n){ i =>
        val (e1, e2) = e(i)
        nreg(i) = f(e1, e2)
      }
      copy(reg = RegId1(nreg))
    }

    def binOpInPlace(e: Index => (T, T), f: (T, T) => T)(implicit sc: SourceContext) = {
      if (!IN_PLACE)
        binOp(e, f)
      else {
        Foreach(0::n){ i =>
          val (e1, e2) = e(i)
          reg(i) = f(e1, e2)
        }
        this
      }
    }    

    def *(x: T)(implicit sc: SourceContext) =
      binOp(i => (reg(i), x), _*_)

    def :*(x: T)(implicit sc: SourceContext) =
      binOpInPlace(i => (reg(i), x), _*_)
    
    def +(v: Vec)(implicit sc: SourceContext) = {
      require(n == v.n)
      binOp(i => (reg(i), v.reg(i)), _+_)
    }

    def :+(v: Vec) = {
      require(n == v.n)
      binOpInPlace(i => (reg(i), v.reg(i)), _+_)      
    }
    
    def -(v: Vec)(implicit sc: SourceContext) = {
      require(n == v.n)
      binOp(i => (reg(i), v.reg(i)), _-_)
    }

    def :-(v: Vec) = {
      require(n == v.n)
      binOpInPlace(i => (reg(i), v.reg(i)), _-_)      
    }
    
    
    def dot(v: Vec)(implicit sc: SourceContext) = {
      require(n == v.n)            
      val r = Reg[T]
      Reduce(r)(0::n)(i => reg(i) * v.reg(i))(_+_)
      r.value
    }

    def norm(implicit sc: SourceContext) = {
      val r = Reg[T]
      Reduce(r)(0::n)(i => reg(i)**2)(_+_)
      sqrtT(r.value)
    }

    
  }

  sealed trait Matrix {
    def apply(y: Index, x: Index)(implicit sc: SourceContext): T
    
    def h: scala.Int
    def w: scala.Int
    def t(implicit sc: SourceContext): Matrix
    def det(implicit sc: SourceContext): T
    def inv(implicit sc: SourceContext): Matrix
    def *(m: Matrix)(implicit sc: SourceContext): Matrix
    def *(y: T)(implicit sc: SourceContext): Matrix
    def +(m: Matrix)(implicit sc: SourceContext): Matrix
    def -(m: Matrix)(implicit sc: SourceContext): Matrix
    def :*(y: T)(implicit sc: SourceContext): Matrix
    def :+(m: Matrix)(implicit sc: SourceContext): Matrix
    def :-(m: Matrix)(implicit sc: SourceContext): Matrix    
    def toMatrixDense: MatrixDense

    def loadTo(sram: SRAM2[T], i: Index)(implicit sc: SourceContext) =
      Foreach(0::h)(y => sram(i, y) = apply(y, 0))
    def loadTo(sram: SRAM3[T], i: Index)(implicit sc: SourceContext) =
      Foreach(0::h, 0::w)((y, x) => sram(i, y, x) = apply(y, x))
    
  }

  object Matrix {

    def apply(h: scala.Int, w: scala.Int, inits: List[T])(implicit sc: SourceContext) = {
      val nreg = RegFile[T](h, w)
      Pipe {
        List.tabulate(h,w){(y, x) =>
          Pipe { nreg(y, x) = inits(y*w+x) }
        }
        ()
      }      
      MatrixDense(h, w, RegId2(nreg))
    }

    def fromSRAM1(h: scala.Int, sram: SRAM2[T], i: Int, copy: scala.Boolean = false, ofs: scala.Int = 0)(implicit sc: SourceContext) = {
      if (copy) {
        val nreg = RegFile[T](h)
        Foreach(0::h) { j =>
          nreg(j) = sram(i, j +ofs)
        }
        MatrixDense(h, 1, RegVec2(RegId1(nreg)))
      }
      else       
        MatrixDense(h, 1, RegVec2(RegSRAM1(sram, i, ofs)))
    }
    
    def fromSRAM2(h: scala.Int, w:scala.Int, sram: SRAM3[T], i: Int, copy: scala.Boolean = false)(implicit sc: SourceContext) = {
      if (copy) {
        val nreg = RegFile[T](h,w)
        Foreach(0 :: h, 0::w) { (y, x) =>
          nreg(y, x) = sram(i, y, x)
        }
        MatrixDense(h, w, RegId2(nreg))
      }
      else       
        MatrixDense(h, w, RegSRAM2(sram, i))
    }
      

    def sparse(h: scala.Int, w: scala.Int, l: IndexedSeq[Option[T]])(implicit sc: SourceContext) = {
      MatrixSparse(h, w, l.sliding(w, w).toIndexedSeq)
    }
    

    def eye(n: scala.Int, v: T)(implicit sc: SourceContext) =
      MatrixDiag(n, v)
  }
  case class MatrixDense(h: scala.Int, w: scala.Int, reg: RegView2) extends Matrix {

    def toMatrixDense = this

    def apply(y: Index, x: Index)(implicit sc: SourceContext) = {
      reg(y, x)
    }

    def t(implicit sc: SourceContext) =
      copy(h = w, w = h, reg = RegTranspose2(reg))

    def binOp(e: (Index, Index) => (T, T), f: (T, T) => T)(implicit sc: SourceContext) = {
      val nreg = RegFile[T](h, w)
      Foreach(0::h, 0::w){ (j, i) =>
        val (e1, e2) = e(j, i)
        nreg(j, i) = f(e1, e2)
      }
      copy(reg = RegId2(nreg))
    }

    def binOpInPlace(e: (Index, Index) => (T, T), f: (T, T) => T)(implicit sc: SourceContext) = {
      if (!IN_PLACE)
        binOp(e, f)
      else {
        Foreach(0::h, 0::w){ (j, i) =>
          val (e1, e2) = e(j, i)
          reg(j, i) = f(e1, e2)
        }
        this
      }
    }        
    
    def +(m: Matrix)(implicit sc: SourceContext) = {
      require(w == m.w && h == m.h)
      binOp((j,i) => (reg(j,i), m.toMatrixDense.reg(j,i)), _+_)
    }

    def :+(m: Matrix)(implicit sc: SourceContext) = {
      require(w == m.w && h == m.h)
      binOpInPlace((j,i) => (reg(j,i), m.toMatrixDense.reg(j,i)), _+_)
    }
    
    def -(m: Matrix)(implicit sc: SourceContext) = {
      require(w == m.w && h == m.h)
      binOp((j,i) => (reg(j,i), m.toMatrixDense.reg(j,i)), _-_)      
    }

    def :-(m: Matrix)(implicit sc: SourceContext) = {
      require(w == m.w && h == m.h)
      binOpInPlace((j,i) => (reg(j,i), m.toMatrixDense.reg(j,i)), _-_)
    }    
    
    def *(x: T)(implicit sc: SourceContext) =
      binOp((j,i) => (reg(j,i), x), _*_)            

    def :*(x: T)(implicit sc: SourceContext) =
      binOpInPlace((j,i) => (reg(j,i), x), _*_)            
    
    
    def *(m: Matrix)(implicit sc: SourceContext): Matrix =  {
      require(w == m.h)
      m match {
        case m: MatrixDense => 
          val nreg = RegFile[T](h, m.w)
          Foreach(0::h, 0::m.w) { (y, x) =>
              nreg(y, x) = row(y).dot(m.col(x))
          }
          MatrixDense(h, m.w, RegId2(nreg))
        case m: MatrixSparse =>
          this*(m.toMatrixDense)
        case m: MatrixDiag =>
          *(m.factor)
      }
    }
    

    def col(x: Index)(implicit sc: SourceContext) =
      Vec(h, RegCol(reg, x))

    def row(y: Index)(implicit sc: SourceContext) =
      Vec(w, RegRow(reg, y))
    

    def det(implicit sc: SourceContext) = {
      val (a11, a12, a13) = (apply(0, 0), apply(0, 1), apply(0, 2))
      val (a21, a22, a23) = (apply(1, 0), apply(1, 1), apply(1, 2))
      val (a31, a32, a33) = (apply(2, 0), apply(2, 1), apply(2, 2))
      a11 * (a33 * a22 - a32 * a23) - a21 * (a33 * a12 - a32 * a13) + a31 * (a23 * a12 - a22 * a13)
    }

    def inv(implicit sc: SourceContext) = {
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

    def apply(y: Index, x: Index)(implicit sc: SourceContext) =
      toMatrixDense.apply(y, x)

    def t(implicit sc: SourceContext) =
      copy(h = w, w = h, data = data.transpose)

    def det(implicit sc: SourceContext): T =
      toMatrixDense.det

    def inv(implicit sc: SourceContext): Matrix =
      toMatrixDense.inv

    def +(m: Matrix)(implicit sc: SourceContext) =
      toMatrixDense + m.toMatrixDense

    def :+(m: Matrix)(implicit sc: SourceContext) =
      toMatrixDense :+ m.toMatrixDense
    
    def -(m: Matrix)(implicit sc: SourceContext) =
      toMatrixDense - m.toMatrixDense      

    def :-(m: Matrix)(implicit sc: SourceContext) =
      toMatrixDense :- m.toMatrixDense      
    
    def *(y: T)(implicit sc: SourceContext) =
      copy(data = data.map(_.map(_.map(_*y))))

    def :*(y: T)(implicit sc: SourceContext) =
      toMatrixDense :* y
    
    def *(m: Matrix)(implicit sc: SourceContext) = {
      require(w == m.h)
      m match {
        case m: MatrixDense => 
          val nreg = RegFile[T](h, m.w)
          Pipe {
            List.tabulate(h, m.w){ (y, x) =>
                val sum = List.tabulate(w){ i =>
                  data(y)(i).map(t => m(i, x) * t)
                }
                  .filter(_.isDefined)
                  .map(_.get)
                  .reduce(_+_)
                Pipe { nreg(y, x) = sum }
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

    def apply(y: Index, x: Index)(implicit sc: SourceContext) =
      lift(factor)

    def h = n
    def w = n

    def t(implicit sc: SourceContext) =
      this

    def +(m: Matrix)(implicit sc: SourceContext) =
      toMatrixDense + m.toMatrixDense

    def :+(m: Matrix)(implicit sc: SourceContext) =
      toMatrixDense :+ m.toMatrixDense
    
    def -(m: Matrix)(implicit sc: SourceContext) =
      toMatrixDense - m.toMatrixDense      

    def :-(m: Matrix)(implicit sc: SourceContext) =
      toMatrixDense :- m.toMatrixDense      
    
    def *(y: T)(implicit sc: SourceContext) =
      copy(factor = y*factor)

    def :*(y: T)(implicit sc: SourceContext) =
      toMatrixDense :* y
    
    def *(m: Matrix)(implicit sc: SourceContext) =
      m*factor

    def inv(implicit sc: SourceContext) =
      *(sqrtT(factor))

    def det(implicit sc: SourceContext) = {
      factor**n
    }

  }
  

}
