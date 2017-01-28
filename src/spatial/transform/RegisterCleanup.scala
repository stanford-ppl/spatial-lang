package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp

trait RegisterCleanup extends ForwardTransformer {
  val IR: SpatialExp
  import IR._

  override val name = "Register Cleanup"

  private var pendingUsers = Map[Exp[_], Map[Exp[_], Exp[_]]]()

  override def transform[T: Staged](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case RegRead(reg) =>
      dbg(c"$lhs = $rhs")
      dbg(c"  readers: ${readersOf(reg)}")
      dbg(c"  read users: ${usersOf(reg)}")

      val hasReader = readersOf(reg).exists{_.node == lhs}
      if (!hasReader) {
        dbg(c"REMOVING register read $lhs")
        constant[T](666)  // Shouldn't be used
      }
      else if (hasReader && usersOf(lhs).nonEmpty) {
        val reads = usersOf(lhs).map{use =>
          val read = mirror(lhs, rhs)
          val map = pendingUsers.getOrElse(use, Map.empty) + (lhs -> read)
          dbg(s"    Reader $use: $lhs -> $read")
          pendingUsers += use -> map
          read
        }
        reads.head
      }
      else mirror(lhs, rhs)

    case RegWrite(reg,value,en) =>
      dbg(c"$lhs = $rhs")
      if (readersOf(reg).isEmpty && !isArgOut(reg)) {
        dbg(c"REMOVING register write $lhs")
        constant[T](666)  // Shouldn't be used
      }
      else mirrorWithDuplication(lhs, rhs)

    case RegNew(_) =>
      dbg(c"$lhs = $rhs")
      if (readersOf(lhs).isEmpty) {
        dbg(c"REMOVING register $lhs")
        constant[T](666)  // Shouldn't be used
      }
      else mirror(lhs, rhs)

    case _ => mirrorWithDuplication(lhs, rhs)
  }

  private def mirrorWithDuplication[T:Staged](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = {
    if (pendingUsers.contains(lhs)) {
      dbg(c"$lhs = $rhs")
      dbg(c"  External reader - adding read substitutions")
      val lhs2 = withSubstScope(pendingUsers(lhs).toList : _*){ mirror(lhs, rhs) }
      dbg(c"  ${str(lhs2)}")
      lhs2
    }
    else mirror(lhs, rhs)
  }

}
