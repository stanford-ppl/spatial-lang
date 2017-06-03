package spatial.analysis

/** Used to make sure the user (and David) didn't do anything stupid **/
trait ScopeCheck extends SpatialTraversal {
  import IR._

  override val name = "Accel Scope Check"
  override val recurse = Always

  def transferError(s: Exp[_]): Unit = {
    error(s.ctx, u"Untransferred host value $s was used in the Accel scope.")

  }

  def isTransferException(e: Exp[_]): Boolean = e match {
    case Exact(_) => true
    case Const(_) => true
    case s: Sym[_] if s.tp == TextType => true   // Exception to allow debug printing to work
    case s if isOffChipMemory(s) => true
    case _ => false
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case Hwblock(blk,_) =>
      val (inputs, stms) = blockInputsAndNestedContents(blk)

      val definedSyms = stms.flatMap(_.lhs)

      // Give errors on illegal var creation / reading / writing within Accel
      definedSyms.foreach{
        // Only give errors for reads when var was defined outside Accel
        case lhs @ Op(ReadVar(v)) if !definedSyms.contains(v) =>
          error(lhs.ctx, u"Variable $v defined outside Accel cannot be read within Accel.")
          error("Use an ArgIn, HostIO, or DRAM to pass values from the host to the accelerator.")
          error(lhs.ctx, showCaret = true)

        case lhs @ Op(NewVar(init)) =>
          error(lhs.ctx, u"Variables cannot be created within the Accel scope.")
          error("Use a local accelerator memory like SRAM or Reg instead.")
          error(lhs.ctx)

        // Only give errors for assigns when var was defined outside Accel
        case lhs @ Op(AssignVar(v, x)) if !definedSyms.contains(v) =>
          error(lhs.ctx, u"Variable $v defined outside Accel cannot be assigned within Accel.")
          error("Use an ArgOut, HostIO, or DRAM to pass values from the accelerator to the host.")
          error(lhs.ctx, showCaret = true)

        case lhs @ Op(ArrayApply(Def(InputArguments()), _)) =>
          error(lhs.ctx, "Input arguments cannot be accessed in Accel scope.")
          error("Use an ArgIn or HostIO to pass values from the host to the accelerator.")
          error(lhs.ctx, showCaret = true)
        case _ =>
      }

      val illegalInputs = inputs.filter{
        case s @ Def(RegRead(_)) => true // Special case on reg reads to disallow const prop through setArg
                                         // TODO: I actually can't remember what I meant here...
        case s @ Def(NewVar(_)) => false // Already gave errors for outside vars
        case s => !isTransferException(s)
      }
      if (illegalInputs.nonEmpty) {
        val n = illegalInputs.size
        if (n == 1) {
          error(illegalInputs.head.ctx, u"Value ${illegalInputs.head} was defined on the host but used in the Accel scope without explicit transfer")
        }
        else {
          error("Multiple values were defined on the host and used in the Accel scope without explicit transfer.")
        }
        error("Use ArgIns or DRAMs coupled with setArg or setMem to transfer scalars or arrays to the accelerator")

        illegalInputs.foreach{in =>
          val use = stms.find(_.rhs.inputs.contains(in))

          if (n > 1) error(in.ctx, u"Value $in")
          error(in.ctx)

          if (use.isDefined) {
            error(use.get.lhs.head.ctx, c"First use occurs here: ", noError = true)
            error(use.get.lhs.head.ctx)
          }
        }
      }

    case Switch(body,selects,cases) =>
      val contents = blockContents(body).flatMap(_.lhs).map(_.asInstanceOf[Exp[_]])
      val missing = cases.toSet diff contents.toSet
      val extra   = contents.toSet diff cases.toSet
      if (extra.nonEmpty) {
        error(c"Switch ${str(lhs)} had extra statements: ")
        extra.foreach{c => error(c"  ${str(c)}")}
        error(lhs.ctx)
      }
      if (missing.nonEmpty) {
        error(c"Switch ${str(lhs)} was missing statements: ")
        missing.foreach{c => error(c"  ${str(c)}")}
        error(lhs.ctx)
      }

    case _ => super.visit(lhs, rhs)
  }

}
