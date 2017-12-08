import scala.reflect.ClassTag

class BankedMemory[T:ClassTag](
  name:     String,
  dims:     Seq[Int],
  banks:    Seq[Int],
  data:     Array[Array[T]],
  invalid:  T,
  saveInit: Boolean
) {
  private val resetValue: Option[Array[Array[T]]] = if (saveInit) Some(data.map(_.clone())) else None

  def reset(): Unit = resetValue match {
    case Some(rst) =>
      data.indices.foreach{i => Array.copy(rst(i),0,data(i),0,rst(i).length) }
    case None =>
      data.indices.foreach{i => data(i).indices.foreach{j => data.apply(i).update(j,invalid) }}
  }

  def flattenAddress(dims: Seq[Int], indices: Seq[FixedPoint]): Int = {
    val strides = List.tabulate(dims.length){i => dims.drop(i+1).product }
    indices.zip(strides).map{case (i,s) => i.toInt*s}.sum
  }

  def apply(ctx: String, bank: Seq[Seq[FixedPoint]], ofs: Seq[FixedPoint], ens: Seq[Bool]): Array[T] = {
    Array.tabulate(bank.length){i =>
      OOB.readOrElse({
        if (ens(i).value) data.apply(flattenAddress(banks,bank(i))).apply(ofs(i).toInt) else invalid
      },{ err =>
        Warn(s"""[warn] $ctx: Memory load from $name out of bounds at bank (${bank(i).mkString(", ")}), address ${ofs(i)}""")
        invalid
      })
    }
  }

  def update(ctx: String, bank: Seq[Seq[FixedPoint]], ofs: Seq[FixedPoint], ens: Seq[Bool], elems: Seq[T]): Unit = {
    bank.indices.foreach{i =>
      OOB.writeOrElse({
        if (ens(i).value) data.apply(flattenAddress(banks,bank(i))).update(ofs(i).toInt,elems(i))
      },{ err =>
        Warn(s"""[warn] $ctx: Memory load from $name out of bounds at bank (${bank(i).mkString(", ")}), address ${ofs(i)}""")
      })
    }
  }
}


class ShiftableMemory[T:ClassTag](
  name:     String,
  dims:     Seq[Int],
  banks:    Seq[Int],
  data:     Array[Array[T]],
  invalid:  T,
  saveInit: Boolean,
  bankedAddr: Seq[FixedPoint] => Seq[FixedPoint],
  bankedOfs:  Seq[FixedPoint] => FixedPoint
) extends BankedMemory[T](name,dims,banks,data,invalid,saveInit){

  def unbankedLoad(ctx: String, addr: Seq[FixedPoint]): T = {
    val bank = bankedAddr(addr)
    val ofs  = bankedOfs(addr)
    this.apply(ctx,Seq(bank),Seq(ofs),Seq(Bool(true))).head
  }
  def unbankedStore(ctx: String, addr: Seq[FixedPoint], data: T): Unit = {
    val bank = bankedAddr(addr)
    val ofs = bankedOfs(addr)
    this.update(ctx,Seq(bank),Seq(ofs),Seq(Bool(true)),Seq(data))
  }

  def shiftInVec(ctx: String, inds: Seq[FixedPoint], axis: Int, elems: Array[T]): Unit = {
    val len = elems.length
    (dims(axis)-1 to 0 by -1).foreach{j =>
      val addrFrom = Seq.tabulate(dims.length){d => if (d != axis) inds(d) else FixedPoint(j - len) }
      val addrTo   = Seq.tabulate(dims.length){d => if (d != axis) inds(d) else FixedPoint(j) }
      val dat = if (j < len) elems(len-1 - j) else unbankedLoad(ctx,addrFrom)
      unbankedStore(ctx,addrTo,dat)
    }
  }

  def shiftIn(ctx: String, inds: Seq[FixedPoint], axis: Int, data: T): Unit = shiftInVec(ctx,inds,axis,Array(data))
}

