package spatial.lang.static

import argon.core._
import forge._
import spatial.lang.HostTransferOps._
import spatial.lang.Math
import spatial.utils._

trait HostTransferApi { this: SpatialApi =>

  /**
    * Transfer a scalar value from the host to the accelerator through the register `reg`.
    * `reg` should be allocated as the HostIO or ArgIn methods.
    */
  @api def setArg[A,T:Bits](reg: Reg[T], value: A)(implicit lift: Lift[A,T]): MUnit = {
    implicit val mT: Type[T] = lift.staged
    wrap( set_arg(reg.s, lift.staged.unwrapped(lift(value))) )
  }

  /**
    * Transfer a scalar value from the accelerator to the host through the register `reg`.
    * `reg` should be allocated using the HostIO or ArgIn methods.
    */
  @api def getArg[T:Type:Bits](reg: Reg[T]): T = wrap(get_arg(reg.s))

  /** Transfers the given @Array of `data` from the host's memory to `dram`'s region of accelerator DRAM. **/
  @api def setMem[T:Type:Bits](dram: DRAM[T], data: MArray[T]): MUnit = wrap(set_mem(dram.s, data.s))
  /** Transfers `dram`'s region of accelerator DRAM to the host's memory and returns the result as an @Array. **/
  @api def getMem[T:Type:Bits](dram: DRAM[T]): MArray[T] = {
    val array = MArray.empty[T](Math.productTree(wrap(stagedDimsOf(dram.s))))
    get_mem(dram.s, array.s)
    array
  }

  /** Transfers the given @Matrix of `data` from the host's memory to `dram`'s region of accelerator DRAM. **/
  @api def setMem[T:Type:Bits](dram: DRAM[T], data: Matrix[T]): MUnit = setMem(dram, data.data)
  /** Transfers `dram`'s region of accelerator DRAM to the host's memory and returns the result as a @Matrix. **/
  @api def getMatrix[T:Type:Bits](dram: DRAM2[T])(implicit ctx: SrcCtx): Matrix[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    matrix(data, wrap(dims(0)), wrap(dims(1)))
  }

  /** Transfers the given Tensor3 of `data` from the host's memory to `dram`'s region of accelerator DRAM. **/
  @api def setMem[T:Type:Bits](dram: DRAM[T], tensor3: Tensor3[T]): MUnit = setMem(dram, tensor3.data)
  /** Transfers `dram`'s region of accelerator DRAM to the host's memory and returns the result as a Tensor3. **/
  @api def getTensor3[T:Type:Bits](dram: DRAM3[T])(implicit ctx: SrcCtx): Tensor3[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    tensor3(data, wrap(dims(0)), wrap(dims(1)), wrap(dims(2)))
  }

  /** Transfers the given Tensor4 of `data` from the host's memory to `dram`'s region of accelerator DRAM. **/
  @api def setMem[T:Type:Bits](dram: DRAM[T], tensor4: Tensor4[T]): MUnit = setMem(dram, tensor4.data)
  /** Transfers `dram`'s region of accelerator DRAM to the host's memory and returns the result as a Tensor4. **/
  @api def getTensor4[T:Type:Bits](dram: DRAM4[T])(implicit ctx: SrcCtx): Tensor4[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    tensor4(data, wrap(dims(0)), wrap(dims(1)), wrap(dims(2)), wrap(dims(3)))
  }

  /** Transfers the given Tensor5 of `data` from the host's memory to `dram`'s region of accelerator DRAM. **/
  @api def setMem[T:Type:Bits](dram: DRAM[T], tensor5: Tensor5[T]): MUnit = setMem(dram, tensor5.data)
  /** Transfers `dram`'s region of accelerator DRAM to the host's memory and returns the result as a Tensor5. **/
  @api def getTensor5[T:Type:Bits](dram: DRAM5[T])(implicit ctx: SrcCtx): Tensor5[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    tensor5(data, wrap(dims(0)), wrap(dims(1)), wrap(dims(2)), wrap(dims(3)), wrap(dims(4)))
  }
}
