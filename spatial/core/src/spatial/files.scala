package spatial

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.nodes._

import scala.io.Source

object files {
  /**
    * Load a CSV file as an Array (not staged - happens immediately)
    */
  def loadCSV[T](filename: String, delim: String)(func: String => T): Seq[T] = {
    Source.fromFile(filename).getLines().flatMap{line =>
      line.split(delim).map(_.trim).flatMap(_.split(" ")).map{x => func(x.trim) }
    }.toSeq
  }

  @internal def parseValue[T:Type](s: String): T = {
    val ms = constant[MString](StringType)(s)
    try {
      (typ[T] match {
        case BooleanType => wrap(MBoolean.from_string(ms))
        case tp: FixPtType[s, i, f] => wrap(FixPt.from_string(ms)(tp.mS, tp.mI, tp.mF, ctx, state))
        case tp: FltPtType[g, e] => wrap(FltPt.from_string(ms)(tp.mG, tp.mE, ctx, state))
        case tp: StructType[_] =>
          val entries = s.split(";").map(_.trim).zip(tp.fields.map(_._2)).map { case (str, tp) => tp.unwrapped(parseValue(str)(mtyp(tp), ctx, state)) }
          Struct[T](tp.fields.map(_._1).zip(entries): _*)(tp.asInstanceOf[StructType[T]], ctx, state)

      }).asInstanceOf[T]
    }
    catch {case e: Throwable =>
      error(ctx, c"Could not parse $s as a ${typ[T]}")
      error(ctx)
      throw e
    }
  }


}
