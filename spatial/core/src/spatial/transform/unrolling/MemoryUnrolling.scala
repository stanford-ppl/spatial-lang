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
    case Def(_:LineBufferRotateEnq[_])  => addr
    // The unrolled version of register file shifting currently doesn't take a banked address
    case Def(_:RegFileVectorShiftIn[_]) => addr
    case Def(_:RegFileShiftIn[_])       => addr
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

  case class UnrollInstance(mem: Exp[_], dispatchIds: Seq[Int], laneIds: Seq[Int], muxIndex: Int, vecOfs: Seq[Int])

  /**
    * Returns the instances required to unroll the given access
    * len: width of vector access for vectorized accesses, e.g. linebuffer(0, c::c+3)
    */
  def getInstances(access: Exp[_], mem: Exp[_], lanes: Unroller, isLoad: Boolean, len: Option[Int]): List[UnrollInstance] = {
    // First, group by the instance each unrolled access is being dispatched to
    val cs = blksBetween(blkOf(access), blkOf(mem))
    val is = accessIterators(access, mem)
    dbgs(s"Access: $access [${blkOf(access)}]")
    dbgs(s"Memory: $mem [${blkOf(mem)}]")
    dbgs(s"Controllers between $access and $mem: ")
    cs.foreach{c => dbgs(s"  ${str(c.node)} [${c.block}]: " + blkIterators(c).mkString(", ")) }
    dbgs(s"Iterators between $access and $mem: " + is.mkString(", "))

    val words = len.map{l => 0 until l}.getOrElse(0 until 1) // For vectors

    val mems = lanes.map{laneId =>
      words.flatMap{w =>
        val uid = is.map{i => unrollNum(i) }
        val id = if (len.isDefined) uid :+ w else uid
        val dispatches = dispatchOf((access, id), mem)
        if (isLoad && dispatches.size > 1) {
          bug(c"Readers should have exactly one dispatch, $access had ${dispatches.size}.")
          bug(access.ctx)
        }
        dispatches.map{dispatchId => (memories((mem, dispatchId)), dispatchId, laneId, id) }
      }
    }.flatten

    val instances = mems.groupBy(_._1).toList

    // Then, group by each time multiplex port each access is being dispatched to
    instances.flatMap{case (mem2, vs) =>
      vs.groupBy{v => val id = v._4; muxIndexOf(access,id,mem) }.toSeq.map{case (muxIndex,vs) =>
        val vecOfs = if (len.isEmpty) Seq(0) else vs.map(_._4).map(_.last)
        UnrollInstance(mem2, vs.map(_._2), vs.map(_._3), muxIndex, vecOfs)
      }
    }
  }

  sealed abstract class UnrolledAccess[T] { def s: Seq[Exp[_]] }
  case class Read[T](v: Exp[T]) extends UnrolledAccess[T] { def s = Seq(v) }
  case class Vec[T](v: Exp[VectorN[T]]) extends UnrolledAccess[T] { def s = Seq(v) }
  case class Write[T](v: Exp[MUnit]) extends UnrolledAccess[T] { def s = Seq(v) }
  case class MultiWrite[T](vs: Seq[Write[T]]) extends UnrolledAccess[T] { def s = vs.flatMap(_.s) }

  sealed abstract class DataOption { def s: Option[Exp[_]] }
  case object NoData extends DataOption { def s = None }
  case class VecData(v: Exp[Vector[_]]) extends DataOption { def s = Some(v) }
  case class Data(v: Exp[_]) extends DataOption { def s = Some(v) }
  object DataOption {
    def apply(data: Option[Exp[_]]): DataOption = data match {
      case Some(d) if isVector(d) => VecData(d.asInstanceOf[Exp[Vector[_]]])
      case Some(d) => Data(d)
      case None => NoData
    }
  }

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

    case Def(_:LineBufferLoad[_])     => Vec(LineBuffer.banked_load(mem.asInstanceOf[Exp[LineBuffer[T]]], bank.get, addr.get, ens.get))
    case Def(_:LineBufferColSlice[_]) => Vec(LineBuffer.banked_load(mem.asInstanceOf[Exp[LineBuffer[T]]], bank.get, addr.get, ens.get))
    case Def(_:LineBufferRowSlice[_]) => Vec(LineBuffer.banked_load(mem.asInstanceOf[Exp[LineBuffer[T]]], bank.get, addr.get, ens.get))

    case Def(op:RegFileVectorShiftIn[_]) =>
      MultiWrite(data.map{d => d.zipWithIndex.map{case (vec,i) =>
        val addr = bank.get.apply(i)
        val en = ens.get.apply(i)
        Write[T](RegFile.vector_shift_in(mem.asInstanceOf[Exp[RegFile[T]]],vec.asInstanceOf[Exp[Vector[T]]], addr, en, op.ax))
      }}.get)

    case Def(op:RegFileShiftIn[_]) =>
      MultiWrite(data.map{d => d.zipWithIndex.map{case (d,i) =>
        val addr = bank.get.apply(i)
        val en = ens.get.apply(i)
        Write[T](RegFile.shift_in(mem.asInstanceOf[Exp[RegFile[T]]], d, addr, en, op.ax))
      }}.get)

    case Def(_:LineBufferRotateEnq[_]) =>
      val rows = bank.get.flatten.distinct
      if (rows.length > 1) {
        bug(s"Conflicting rows in banked LineBuffer rotate enqueue: " + rows.mkString(", "))
        bug(access.ctx)
      }
      Write(LineBuffer.banked_rotateEnq(mem.asInstanceOf[Exp[LineBuffer[T]]],data.get,ens.get,rows.head))

    case _ => throw new Exception(s"bankedAccess called on unknown access node ${str(access)}")
  }

  def unrollVectorAccess[T:Type:Bits](
    access:  Exp[_],
    mem:     Exp[_],
    dataExp: DataOption,
    addr:    Option[Seq[Exp[Index]]],
    en:      Option[Exp[Bit]],
    lanes:   Unroller,
    len:     Int,                      // Vector length
    axis:    Option[Int]               // Vector axis
  )(implicit ctx: SrcCtx): List[Exp[_]] = {
    val isLoad = dataExp.s.isEmpty
    val length = if (axis.isDefined) Some(len) else None
    val mems = getInstances(access, mem, lanes, isLoad, length)

    dbgs(s"Unrolling ${str(access)}"); strMeta(access, tab = tab)

    // Writing two different values to the same address currently just writes the last value
    val dataMap = dataExp match {
      case NoData => Map.empty[(Int,Int),Exp[T]]
      // Expand the data to lanes of vectors of data
      case VecData(v) =>
        lanes.map{p =>
          val vec = f(v).asInstanceOf[Exp[Vector[T]]]
          (0 until len).map{w => (p,w) -> Vector.select[T](vec,w) }
        }.flatten.toMap

      case Data(v) =>
        lanes.map{p =>
          val d = f(v).asInstanceOf[Exp[T]]
          (0 until len).map{w => (p,w) -> d }
        }.flatten.toMap
    }

    val unrolled = mems.flatMap{ case UnrollInstance(mem2, dispatchIds, laneIds, muxIndex, vecIds) =>
      dbgs(s"  Dispatch: $dispatchIds")
      dbgs(s"  Lane IDs: $laneIds")
      dbgs(s"  Vec IDs:  $vecIds")
      dbgs(s"  Mux Index: $muxIndex")
      val inst  = instanceOf(mem2)

      val allWords:    Seq[(Int,Int)] = laneIds.indices.flatMap{laneId => (0 until len).map{vecId => (laneId, vecId) }}
      val allWordsMap: Map[(Int,Int), Int] = allWords.zipWithIndex.toMap

      val opt = addr.map{add =>
        // Lanes of vector ND addresses and word ids
        val a2 = lanes.inLanes(laneIds) { p =>
          val base = f(add).zipWithIndex
          vecIds.map{ofs =>
            val vectorAddr = base.map { case (a, d) => if (axis.contains(d)) FixPt.add(a, FixPt.int32s(ofs)) else a }
            (vectorAddr, (p, ofs))
          }
        }.flatten

        // ND Address + word Id pairs
        val distinct = a2.groupBy(_._1).mapValues(_.map(_._2)).toSeq

        // Sequence of distinct ND addresses
        val addr_vector = distinct.map(_._1)
        // Sequence of word ID (lane, ofs) pairs
        val take = distinct.map(_._2.last)
        // Broadcast mapping (mapping from distinct address to vector address
        val map  = distinct.zipWithIndex.flatMap{case (entry,id) => entry._2.map{wordId => wordId -> id}}.toMap

        (addr_vector, take, map)
      }

      // Optional sequence of distinct ND addresses
      val addr2: Option[Seq[Seq[Exp[Index]]]] = opt.map(_._1)
      // Sequence of lane, word ID pairs for the distinct addresses
      val take:  Seq[(Int,Int)]               = opt.map(_._2).getOrElse(allWords)
      // Mapping from (lane,word) pair to vector address
      val map:   Map[(Int,Int), Int]          = opt.map(_._3).getOrElse(allWordsMap)

      // Per-lane enable
      val ens   = en.map{e => lanes.inLanes(laneIds){_ => Bit.and(f(e),globalValid()) }}

      addr2.foreach{a =>
        a.zipWithIndex.foreach{case (va,id) =>
          dbgs(s"  Word #$id: Address: " + va.mkString(", "))
        }
      }
      dbgs(s"  Take: $take // Non-duplicated lane indices")

      // Turn it into a 1D vector of data by taking the (last) element for each address to be written
      val data2: Option[Seq[Exp[T]]] = if (isLoad) None else Some(take.map{t => dataMap(t) })

      val bank  = addr2.map{a => bankAddress(access,a,inst,lanes) }
      val ofs   = addr2.map{a => bankOffset(mem,access,a,inst,lanes) }
      val ports = dispatchIds.flatMap{d => portsOf(access, mem, d) }.toSet

      val banked = bankedAccess[T](access, mem2, data2, bank, ofs, ens)
      banked.s.foreach{s =>
        portsOf(s,mem2,0) = ports
        muxIndexOf(s,Seq(0),mem2) = muxIndex
        dbgs(s"  ${str(s)}"); strMeta(s, tab+1)
      }

      if (config.verbosity > 0) banked match {
        case Vec(vec) => map.foreach{case (id,vecId) => dbgs(s"    $id -> $vec($vecId)") }
        case Read(v)  => map.foreach{case (id,vecId) => dbgs(s"    $id -> $v") }
        case Write(w) => map.foreach{case (id,vecId) => dbgs(s"    $id -> $w") }
        case MultiWrite(w) => map.foreach{case (id,vecId) => w.foreach{wr => dbgs(s"    $id -> $w") }}
      }

      banked match {
        case Vec(vec) => map.map{case (id,vecId) => id -> Read(Vector.select[T](vec,vecId)) }
        case Read(v)  => map.map{case (id,vecId) => id -> Read(v) }
        case Write(w) => map.map{case (id,vecId) => id -> Write(w) }
        case MultiWrite(w) => map.map{case (id,vecId) => id -> w.head }
      }
    }.toMap

    if (isLoad) {
      lanes.map{p =>
        val laneElems: Seq[Exp[T]] = (0 until len).map{w => unrolled((p,w)) match {
          case Read(r) => r
          case _ => throw new Exception("Unreachable")
        }}
        val result = if (axis.isDefined) {
          implicit val vT: Type[VectorN[T]] = VectorN.typeFromLen[T](len)
          Vector.fromseq(laneElems)
        }
        else {
          laneElems.head
        }
        register(access -> result)
        result
      }
    }
    else {
      // HACK: If the write unrolls to multiple writes per lane, just use the first write for substitution
      // (it isn't used anyway)
      lanes.map{p =>
        val firstWrite = unrolled((p,0)) match {
          case Write(w) => w
          case _ => throw new Exception("Unreachable")
        }
        register(access -> firstWrite)
        firstWrite
      }
    }
  }

  def unrollAccess[T:Type:Bits](
    access: Exp[_],
    mem:    Exp[_],
    data:   Option[Exp[_]],
    addr:   Option[Seq[Exp[Index]]],
    en:     Option[Exp[Bit]],
    lanes:  Unroller
  )(implicit ctx: SrcCtx): List[Exp[_]] = {
    val mems = getInstances(access, mem, lanes, isLoad = data.isEmpty, None)

    dbgs(s"Unrolling ${str(access)}"); strMeta(access, tab)

    mems.flatMap{case UnrollInstance(mem2,dispatchIds,laneIds,muxIndex,_) =>
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

      banked.s.foreach{s =>
        portsOf(s,mem2,0) = ports
        muxIndexOf(s,Seq(0),mem2) = muxIndex
        dbgs(s"  ${str(s)}"); strMeta(s, tab)
      }

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
        case MultiWrite(vs) => lanes.unifyLanes(laneIds)(access, vs.head.s.head)
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
    lanes.map{_ => withSubstScope(mem -> memories((mem,0)) ) { cloneOp(lhs, rhs) } }
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
        strMeta(mem2,tab+1)
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
    case op@LineBufferRotateEnq(lb,data,en,row) =>
      unrollAccess(lhs,lb,Some(data),Some(Seq(row)),Some(en),lanes)(op.mT,op.bT,ctx)

    case StatusReader(mem) => unrollStatus(lhs,rhs,mem,lanes)
    case Resetter((mem,_)) => unrollResetMemory(lhs,rhs,mem,lanes)

    // TODO: treat vector enqueues like single operations for now
    case op:VectorEnqueueLikeOp[_] if op.localWrites.length == 1 =>
      val (mem,data,addr,en) = op.localWrites.head
      unrollAccess(lhs,mem,data,addr,en,lanes)(op.mT,op.bT,ctx)

    case op:VectorReader[_,_] if op.localReads.length == 1 =>
      val (mem,addr,en) = op.localReads.head
      unrollVectorAccess(lhs,mem,NoData,addr,en,lanes, len=op.accessWidth, axis=Some(op.axis))(op.mT,op.bT,ctx)

    case op:VectorWriter[_,_] if op.localWrites.length == 1 =>
      val (mem,data,addr,en) = op.localWrites.head
      unrollVectorAccess(lhs,mem,DataOption(data),addr,en,lanes, len=op.accessWidth, axis=Some(op.axis))(op.mT,op.bT,ctx)

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
