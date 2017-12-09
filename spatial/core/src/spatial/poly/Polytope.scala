package spatial.poly

import argon.core._
import forge._
import spatial.Subproc
import spatial.aliases._

object Polytope {
  @stateful def isEmpty(matrix: String): Boolean = {
    dbg(s"Running ISL with input: ")
    dbg(matrix)
    try {
      val HOME = sys.env("SPATIAL_HOME")
      val proc = Subproc("./emptiness"){(_,_) => None }
      //val proc = Subproc("ls"){(_,_) => None }
      proc.run(s"$HOME/isl-bin/")

      try {
        proc.send(matrix)
      }
      catch {case _:Throwable =>
        bug(s"Unable to send matrix to ISL")
        proc.kill()
      }

      val lines = proc.blockAndReturnOut()
      dbg("Got lines: ")
      lines.foreach{line => dbg(line) }
      lines.contains{"empty"}
    }
    catch {case t: Throwable =>
      bug(s"Could not open the ISL subprocess.")
      throw t
    }
  }

}
