package spatial

import argon.core._
import forge._

package object banking {
  type AccessPair  = (Matrix, Array[Int])
  type IndexDomain = Array[Array[Int]]

  implicit class AccessPairOps(x: AccessPair) {
    @stateful def printWithTab(tab: String): Unit = {
      val data = x._1.data
      val as = x._2

      val entries = data.map{x => x.map(_.toString) }
      val maxCol  = entries.map{_.map(_.length).fold(0){Math.max}}.fold(0){Math.max}
      entries.zip(as).foreach{case (row,a) =>
        dbg(tab + row.map{x => " "*(maxCol - x.length + 1) + x}.mkString(" ") + " " + a)
      }
    }
  }
}
