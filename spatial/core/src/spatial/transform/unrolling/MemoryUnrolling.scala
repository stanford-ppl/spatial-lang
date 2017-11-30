package spatial.transform.unrolling

import argon.core._
import spatial.banking._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait MemoryUnrolling extends UnrollingBase {

  def bankAddress(
    access: Exp[_],               // Pre-unrolled access
    addr:   Seq[Seq[Exp[Index]]], // Per-lane ND address (Lanes is outer Seq, ND is inner Seq)
    inst:   Memory,               // Memory instance associated with this access
    lanes:  Unroller              // Unrolling helper function
  )(implicit ctx: SrcCtx): Seq[Seq[Exp[Index]]] = access match {
    // LineBuffers are special in that their first dimension is always implicitly fully banked
    case Def(_:LineBufferLoad[_])      => addr.map{case row::col => row +: inst.bankAddress(col) }
    case Def(_:LineBufferColSlice[_])  => addr.map{case row::col => row +: inst.bankAddress(col) }
    case Def(_:LineBufferRowSlice[_])  => addr.map{case row::col => row +: inst.bankAddress(col) }
    case Def(_:LineBufferRotateEnq[_]) => addr
    case _ => addr.map{laneAddr => inst.bankAddress(laneAddr) }
  }

  def bankOffset(
    mem: Exp[_],
    access: Exp[_],
    addr: Seq[Seq[Exp[Index]]],
    inst: Memory,
    lanes: Unroller
  )(implicit ctx: SrcCtx): Seq[Exp[Index]] = access match {
    case Def(_:LineBufferRotateEnq[_]) => Nil
    case _ => addr.map{laneAddr => inst.bankOffset(mem, laneAddr) }
  }

  case class UnrollInstance(mem: Exp[_], dispatchIds: Seq[Int], laneIds: Seq[Int], muxIndex: Int)

  /**
    * Returns the instances required to unroll the given access
    * len: width of vector access for vectorized accesses, e.g. linebuffer(0, c::c+3)
    */
  def getInstances(access: Exp[_], mem: Exp[_], lanes: Unroller, isLoad: Boolean, len: Int): List[UnrollInstance] = {
    // First, group by the instance each unrolled access is being dispatched to
    val is   = accessIterators(access, mem)
    val mems = lanes.map{laneId =>
      val id = is.map{i => unrollNum(i) }
      val dispatches = dispatchOf((access,id),mem)
      if (isLoad && dispatches.size > 1) {
        bug(c"Readers should have exactly one dispatch, $access had ${dispatches.size}.")
        bug(access.ctx)
      }
      dispatches.map{dispatchId => (memories((mem,dispatchId)), dispatchId, laneId, id) }
    }.flatten

    val instances = mems.groupBy(_._1).toList

    // Then, group by each time multiplex port each access is being dispatched to
    instances.flatMap{case (mem2, vs) =>
      vs.groupBy{v => val id = v._4; muxIndexOf(access,id,mem) }.toSeq.map{case (muxIndex,vs) =>
        UnrollInstance(mem2, vs.map(_._2), vs.map(_._3), muxIndex)
      }
    }
  }

  sealed abstract class UnrolledAccess[T] { def s: Exp[_] }
  case class Read[T](v: Exp[T]) extends UnrolledAccess[T] { def s = v }
  case class Vec[T](v: Exp[VectorN[T]]) extends UnrolledAccess[T] { def s = v }
  case class Write[T](v: Exp[MUnit]) extends UnrolledAccess[T] { def s = v }

  def bankedAccess[T:Type:Bits](
    access: Exp[_],
    mem:    Exp[_],
    data:   Option[Seq[Exp[T]]],
    bank:   Option[Seq[Seq[Exp[Index]]]],
    addr:   Option[Seq[Exp[Index]]],
    ens:    Option[Seq[Exp[Bit]]]
  )(implicit ctx: SrcCtx): UnrolledAccess[T] = access match {
    case Def(_:FIFODeq[_])          => Vec(FIFO.banked_deq(mem.asInstanceOf[Exp[FIFO[T]]], ens.get))
    case Def(_:FILOPop[_])          => Vec(FILO.banked_pop(mem.asInstanceOf[Exp[FILO[T]]], ens.get))
    case Def(_:LineBufferLoad[_])   => Vec(LineBuffer.banked_load(mem.asInstanceOf[Exp[LineBuffer[T]]],bank.get,addr.get,ens.get))
    case Def(_:LUTLoad[_])          => Vec(LUT.banked_load(mem.asInstanceOf[Exp[LUT[T]]], bank.get, addr.get, ens.get))
    case Def(_:RegFileLoad[_])      => Vec(RegFile.banked_load(mem.asInstanceOf[Exp[RegFile[T]]], bank.get, addr.get, ens.get))
    case Def(_:SRAMLoad[_])         => Vec(SRAM.banked_load(mem.asInstanceOf[Exp[SRAM[T]]], bank.get, addr.get, ens.get))
    case Def(_:StreamRead[_])       => Vec(StreamIn.banked_read(mem.asInstanceOf[Exp[StreamIn[T]]], ens.get))

    case Def(_:BufferedOutWrite[_]) => Write[T](BufferedOut.banked_write(mem.asInstanceOf[Exp[BufferedOut[T]]], data.get, bank.get, addr.get, ens.get))
    case Def(_:FIFOEnq[_])          => Write[T](FIFO.banked_enq(mem.asInstanceOf[Exp[FIFO[T]]], data.get, ens.get))
    case Def(_:FILOPush[_])         => Write[T](FILO.banked_push(mem.asInstanceOf[Exp[FILO[T]]], data.get, ens.get))
    case Def(_:LineBufferEnq[_])    => Write[T](LineBuffer.banked_enq(mem.asInstanceOf[Exp[LineBuffer[T]]],data.get,ens.get))
    case Def(_:RegFileStore[_])     => Write[T](RegFile.banked_store(mem.asInstanceOf[Exp[RegFile[T]]], data.get, bank.get, addr.get, ens.get))
    case Def(_:SRAMStore[_])        => Write[T](SRAM.banked_store(mem.asInstanceOf[Exp[SRAM[T]]], data.get, bank.get, addr.get, ens.get))
    case Def(_:StreamWrite[_])      => Write[T](StreamOut.banked_write(mem.asInstanceOf[Exp[StreamOut[T]]], data.get, ens.get))


    case Def(_:RegRead[_])          => Read(Reg.read(mem.asInstanceOf[Exp[Reg[T]]]))
    case Def(_:RegWrite[_])         => Write[T](Reg.write(mem.asInstanceOf[Exp[Reg[T]]],data.get.head, ens.get.head))

    // Special case rotateEnq because I'm lazy and don't want to duplicate unrolling code just for this one node
    case Def(_:LineBufferRotateEnq[_]) =>
      val rows = bank.get.flatten.distinct
      if (rows.length > 1) {
        bug(s"Conflicting rows in banked LineBuffer rotate enqueue: " + rows.mkString(", "))
        bug(access.ctx)
      }
      Write(LineBuffer.banked_rotateEnq(mem.asInstanceOf[Exp[LineBuffer[T]]],data.get,ens.get,rows.head))

    case _ => throw new Exception(s"bankedAccess called on unknown access node ${str(access)}")
  }

  def unrollAccess[T:Type:Bits](
    access: Exp[_],
    mem:    Exp[_],
    data:   Option[Exp[_]],
    addr:   Option[Seq[Exp[Index]]],
    en:     Option[Exp[Bit]],
    lanes:  Unroller
  )(implicit ctx: SrcCtx): List[Exp[_]] = {
    val mems = getInstances(access, mem, lanes, isLoad = data.isEmpty)

    dbgs(s"Unrolling ${str(access)}"); strMeta(access)

    mems.flatMap{case UnrollInstance(mem2,dispatchIds,laneIds,muxIndex) =>
      dbgs(s"  Dispatch: $dispatchIds")
      dbgs(s"  Lane IDs: $laneIds")
      dbgs(s"  Mux Index: $muxIndex")

      val ens   = en.map{e => lanes.inLanes(laneIds){_ => Bit.and(f(e),globalValid()) }}
      val inst  = instanceOf(mem2)
      val addrOpt = addr.map{a =>
        val a2 = lanes.inLanes(laneIds){p => (f(a),p) }               // lanes of ND addresses
        val distinct = a2.groupBy(_._1).mapValues(_.map(_._2)).toSeq  // ND address -> lane IDs
        val addr: Seq[Seq[Exp[Index]]] = distinct.map(_._1)           // Vector of ND addresses
        val take: Seq[Int] = distinct.map(_._2.last)                  // Last index of each distinct address
        val mapping: Map[Int,Int] = distinct.zipWithIndex.flatMap{case (entry,aId) => entry._2.map{laneId => laneId -> aId }}.toMap
        (addr, take, mapping)
      }
      val addr2   = addrOpt.map(_._1)                            // Vector of ND addresses
      val take    = addrOpt.map(_._2).getOrElse(laneIds.indices) // List of lanes which do not require broadcast
      val addrMap = addrOpt.map(_._3)                            // Lane -> vector ID
      def vecLength: Int = addr2.map(_.length).getOrElse(laneIds.length)
      def laneToVecAddr(lane: Int): Int = addrMap.map(_.apply(lane)).getOrElse(laneIds.indexOf(lane))

      dbgs(s"  Take: $take // Non-duplicated lane indices")

      // Writing two different values to the same address currently just writes the last value
      val data2 = data.map{d =>
        val d2 = lanes.inLanes(laneIds){_ => f(d) }
        take.map{t => d2(laneToVecAddr(t)) }
      }

      val bank  = addr2.map{a => bankAddress(access,a,inst,lanes) }
      val ofs   = addr2.map{a => bankOffset(mem,access,a,inst,lanes) }
      val ports = dispatchIds.flatMap{d => portsOf(access, mem, d) }.toSet

      val banked = bankedAccess[T](access, mem2, data2.asInstanceOf[Option[Seq[Exp[T]]]], bank, ofs, ens)

      portsOf(banked.s,mem2,0) = ports
      muxIndexOf(banked.s,Seq(0),mem2) = muxIndex

      dbgs(s"  ${str(banked.s)}"); strMeta(banked.s)

      banked match{
        case Vec(vec) =>
          val elems = take.indices.map{i => Vector.select[T](vec, i) }
          lanes.inLanes(laneIds){p =>
            val elem = elems(laneToVecAddr(p))
            register(access -> elem)
            elem
          }
        case Read(v)      => lanes.unifyLanes(laneIds)(access,v)
        case Write(write) => lanes.unifyLanes(laneIds)(access,write)
      }
    }
  }

  /**
    * Unrolls a status check node on the given memory.
    * Assumes that the memory being checked may have no more than one duplicate.
    * Status checks on duplicates memories are currently undefined.
    */
  def unrollStatus[T](
    lhs:   Sym[T],
    rhs:   Op[T],
    mem:   Exp[_],
    lanes: Unroller
  )(implicit ctx: SrcCtx): List[Exp[_]] = {
    if (duplicatesOf(mem).length > 1) {
      warn(lhs.ctx, "Unrolling a status check node on a duplicated memory. Behavior is undefined.")
      warn(lhs.ctx)
    }
    lanes.map{p => withSubstScope(mem -> memories((mem,0)) ) { cloneOp(lhs, rhs) } }
  }

  def duplicateMemory(mem: Exp[_])(implicit ctx: SrcCtx): Seq[(Exp[_], Int)] = mem match {
    case Op(op) =>
      dbgs(s"Duplicating ${str(mem)}")
      duplicatesOf(mem).zipWithIndex.map{case (inst,d) =>
        dbgs(s"  #$d: $inst")
        val mem2 = cloneOp(mem.asInstanceOf[Sym[Any]],op.asInstanceOf[Op[Any]])
        duplicatesOf(mem2) = Seq(inst)
        mem2.name = mem2.name.map{x => s"${x}_$d"}
        dbgs(s"  ${str(mem2)}")
        dbg(s"${strMeta(mem2,tab+1)}")
        (mem2,d)
      }
    case _ => throw new Exception("Could not duplicate memory with no def")
  }

  /**
    * Unrolls a local memory, both across lanes and duplicating within each lane.
    * Duplicates are registered internally for each lane (orig, dispatch#) -> dup.
    */
  def unrollMemory(mem: Exp[_], lanes: Unroller)(implicit ctx: SrcCtx): List[Exp[_]] = {
    lanes.duplicateMem(mem){_ => duplicateMemory(mem)}
    Nil // No correct default substitution for mem - have to know dispatch number of the access
  }

  /**
    * Unrolls a global memory
    * Assumption: Global memories are never duplicated, since they correspond to external pins / memory spaces
    */
  def unrollGlobalMemory[A](mem: Sym[A], rhs: Op[A])(implicit ctx: SrcCtx): Exp[A] = {
    val mem2 = cloneOp(mem, rhs)
    duplicatesOf(mem2) = duplicatesOf(mem)
    memories += (mem,0) -> mem2
    mem2
  }

  /**
    * Unrolls a reset node. Assumes this unrolls to resetting ALL duplicates
    */
  def unrollResetMemory[A](lhs: Sym[A], rhs: Op[A], mem: Exp[_], lanes: Unroller)(implicit ctx: SrcCtx): List[Exp[_]] = {
    val duplicates = memories.keys.filter(_._1 == mem)
    val lhs2 = duplicates.map{dup => withSubstScope(mem -> memories(dup)){ cloneOp(lhs, rhs) } }
    lanes.unify(lhs,lhs2.head)
  }

  override def transform[A:Type](lhs: Sym[A], rhs: Op[A])(implicit ctx: SrcCtx): Exp[A] = rhs match {
    case _:StreamInNew[_]    => unrollGlobalMemory(lhs,rhs)
    case _:StreamOutNew[_]   => unrollGlobalMemory(lhs,rhs)
    case _:BufferedOutNew[_] => unrollGlobalMemory(lhs,rhs)
    case _ => super.transform(lhs, rhs)
  }

  override def unroll[T](lhs: Sym[T], rhs: Op[T], lanes: Unroller)(implicit ctx: SrcCtx): List[Exp[_]] = rhs match {
    case _:FIFONew[_]       => unrollMemory(lhs,lanes)
    case _:FILONew[_]       => unrollMemory(lhs,lanes)
    case _:LUTNew[_,_]      => unrollMemory(lhs,lanes) // TODO: Duplication of LUTs?
    case _:LineBufferNew[_] => unrollMemory(lhs,lanes)
    case _:RegNew[_]        => unrollMemory(lhs,lanes) // TODO: Should we explicitly duplicate Regs?
    case _:RegFileNew[_,_]  => unrollMemory(lhs,lanes)
    case _:SRAMNew[_,_]     => unrollMemory(lhs,lanes)
    case _:StreamInNew[_]    => lanes.unify(lhs,unrollGlobalMemory(lhs,rhs))
    case _:StreamOutNew[_]   => lanes.unify(lhs,unrollGlobalMemory(lhs,rhs))
    case _:BufferedOutNew[_] => lanes.unify(lhs,unrollGlobalMemory(lhs,rhs))

    // LineBuffer RotateEnq is special
    case op@LineBufferRotateEnq(lb,data,en,row) => unrollAccess(lhs,lb,Some(data),Some(Seq(row)),Some(en),lanes)(op.mT,op.bT,ctx)

    case StatusReader(mem) => unrollStatus(lhs,rhs,mem,lanes)
    case Resetter((mem,_)) => unrollResetMemory(lhs,rhs,mem,lanes)

    case op:Reader[_,_] if op.localReads.length == 1 =>
      val (mem,addr,en) = op.localReads.head
      unrollAccess(lhs,mem,None,addr,en,lanes)(op.mT,op.bT,ctx)

    case op:Writer[_,_] if op.localWrites.length == 1 =>
      val (mem,data,addr,en) = op.localWrites.head
      unrollAccess(lhs,mem,data,addr,en,lanes)(mtyp(op.mT),mbits(op.bT),ctx)

    //case op@FIFODeq(fifo,en)       => unrollAccess(lhs,fifo,None,None,Some(en),lanes)(op.mT,op.bT,ctx)
    //case op@FIFOEnq(fifo,data,en)  => unrollAccess(lhs,fifo,Some(data),None,Some(en),lanes)(op.mT,op.bT,ctx)

    //case op@FILOPop(filo,en)       => unrollAccess(lhs,filo,None,None,Some(en),lanes)(op.mT,op.bT,ctx)
    //case op@FILOPush(filo,data,en) => unrollAccess(lhs,filo,Some(data),None,Some(en),lanes)(op.mT,op.bT,ctx)

    //case op@LineBufferEnq(lb,data,en)           => unrollAccess(lhs,lb,Some(data),None,Some(en),lanes)(op.mT,op.bT,ctx)
    //case op@LineBufferLoad(lb,row,col,en)       => unrollAccess(lhs,lb,None,Some(Seq(row,col)),Some(en),lanes)(op.mT,op.bT,ctx)

    //case op@LUTLoad(lut,addr,en)                => unrollAccess(lhs,lut,None, Some(addr),Some(en),lanes)(op.mT,op.bT,ctx)

    //case op@RegRead(reg)                        => unrollAccess(lhs,reg,None,None,None,lanes)(op.mT, op.bT, ctx)
    //case op@RegWrite(reg,data,en)               => unrollAccess(lhs,reg,Some(data),None,Some(en),lanes)(op.mT, op.bT, ctx)

    //case op@RegFileLoad(reg,addr,en)            => unrollAccess(lhs,reg,None, Some(addr),Some(en),lanes)(op.mT,op.bT,ctx)
    //case op@RegFileStore(reg,addr,data,en)      => unrollAccess(lhs,reg,Some(data),Some(addr),Some(en),lanes)(op.mT,op.bT,ctx)

    //case op@SRAMLoad(sram,addr,en)              => unrollAccess(lhs,sram,None,Some(addr),Some(en),lanes)(op.mT,op.bT,ctx)
    //case op@SRAMStore(sram,data,addr,en)        => unrollAccess(lhs,sram,Some(data),Some(addr),Some(en),lanes)(op.mT,op.bT,ctx)

    //case op@StreamRead(stream,en)               => unrollAccess(lhs,stream,None,None,Some(en),lanes)(op.mT,op.bT,ctx)
    //case op@StreamWrite(stream,data,en)         => unrollAccess(lhs,stream,Some(data),None,Some(en),lanes)(op.mT,op.bT,ctx)
    //case op@BufferedOutWrite(buff,data,addr,en) => unrollAccess(lhs,buff,Some(data),Some(addr),Some(en),lanes)(op.mT,op.bT,ctx)


    case _ => super.unroll(lhs,rhs,lanes)
  }

}
