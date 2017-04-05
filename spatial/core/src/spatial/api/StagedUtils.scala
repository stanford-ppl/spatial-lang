package spatial.api

import argon.core.Staging
import argon.ops.ArrayApi
import spatial._
import org.virtualized._
import forge._

trait StagedUtilApi extends StagedUtilExp with ArrayApi {
  this: SpatialApi =>

  @virtualize
  @api def printArray[T:Meta](array: Array[T], header: String = ""): Void = {
    println(header)
    (0 until array.length) foreach { i => print(array(i).toString + " ") }
    println("")
  }

  @virtualize
  @api def printMatrix[T: Meta](matrix: Matrix[T], header: String = ""): Void = {
    println(header)
    (0 until matrix.rows) foreach { i =>
      (0 until matrix.cols) foreach { j =>
        print(matrix(i, j).toString + "\t")
      }
      println("")
    }
  }

  // These don't work anyway..
  /*implicit def insert_void[A,R](x: A => R): A => Void = x.andThen{_ => unit2void(()) }
  implicit def insert_void[A,B,R](x: (A,B) => R): (A,B) => Void = x.andThen{_ => unit2void(()) }
  implicit def insert_void[A,B,C,R](x: (A,B,C) => R): (A,B,C) => Void = x.andThen{_ => unit2void(()) }
  implicit def insert_void[A,B,C,D,R](x: (A,B,C,D) => R): (A,B,C,D) => Void = x.andThen{_ => unit2void(()) }
  implicit def insert_void[A,B,C,D,E,R](x: (A,B,C,D,E) => R): (A,B,C,D,E) => Void = x.andThen{_ => unit2void(()) }

  // Why on earth don't these already exist in Scala?
  implicit class Func2Ops[A,B,R](f: (A,B) => R) { def andThen[T](g: R => T): (A,B) => T = {(a: A, b: B) => g(f(a,b)) } }
  implicit class Func3Ops[A,B,C,R](f: (A,B,C) => R) { def andThen[T](g: R => T): (A,B,C) => T = {(a: A, b: B, c: C) => g(f(a,b,c)) } }
  implicit class Func4Ops[A,B,C,D,R](f: (A,B,C,D) => R) { def andThen[T](g: R => T): (A,B,C,D) => T = {(a: A, b: B, c: C, d: D) => g(f(a,b,c,d)) } }
  implicit class Func5Ops[A,B,C,D,E,R](f: (A,B,C,D,E) => R) { def andThen[T](g: R => T): (A,B,C,D,E) => T = {(a: A, b: B, c: C, d: D, e: E) => g(f(a,b,c,d,e)) } }
  */
}

trait StagedUtilExp extends Staging {
  this: SpatialExp =>


}
