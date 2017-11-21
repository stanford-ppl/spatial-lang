package spatial.poly

import argon.core._
import forge._
import spatial.Subproc

object Polytope {
  @stateful def isEmpty(matrix: String): Boolean = {
    dbg(s"Running ISL with input: ")
    dbg(matrix)
    val proc = Subproc("/isl/isl_polyhedron_sample"){(_,_) => None }
    proc.send(matrix)
    val lines = proc.blockAndReturnOut()
    dbg("Got lines: ")
    lines.foreach{line => dbg(line) }
    lines.contains{"[]"}
  }

}
