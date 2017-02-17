
.. role:: black
.. role:: gray
.. role:: silver
.. role:: white
.. role:: maroon
.. role:: red
.. role:: fuchsia
.. role:: pink
.. role:: orange
.. role:: yellow
.. role:: lime
.. role:: green
.. role:: olive
.. role:: teal
.. role:: cyan
.. role:: aqua
.. role:: blue
.. role:: navy
.. role:: purple

.. _Reg:

Reg
===


Reg defines a hardware register used to hold a scalar value. Regs have an optional name (primarily used for debugging) and reset value.
The default reset value for a Reg is the numeric zero value for it's specified type.
Regs can have an arbitrary number of readers but can only have one writer. By default, Regs are reset based upon the controller that they
are defined within. A Reg defined within a Pipe, for example, is reset at the beginning of each iteration of that Pipe.


Static methods
--------------

.. parsed-literal::

  :maroon:`def` apply()(:maroon:`implicit` ev0: Num[T]): :doc:`reg`\[T\]

Creates a register with type T


*********

.. parsed-literal::

  :maroon:`def` apply(reset: Int)(:maroon:`implicit` ev0: Num[T]): :doc:`reg`\[T\]

Creates an unnamed register with type T and given reset value


*********

.. parsed-literal::

  :maroon:`def` apply(reset: Long)(:maroon:`implicit` ev0: Num[T]): :doc:`reg`\[T\]

Creates an unnamed register with type T and given reset value


*********

.. parsed-literal::

  :maroon:`def` apply(reset: Float)(:maroon:`implicit` ev0: Num[T]): :doc:`reg`\[T\]

Creates an unnamed register with type T and given reset value


*********

.. parsed-literal::

  :maroon:`def` apply(reset: Double)(:maroon:`implicit` ev0: Num[T]): :doc:`reg`\[T\]

Creates an unnamed register with type T and given reset value


*********

.. parsed-literal::

  :maroon:`def` apply(reset: T)(:maroon:`implicit` ev0: Num[T]): :doc:`reg`\[T\]

Allow regs to be initialized to tuples


Infix methods
-------------

.. parsed-literal::

  :maroon:`def` :=(x: T): Unit

Creates a writer to this Reg. Note that Regs and ArgOuts can only have one writer, while ArgIns cannot have any


*********

.. parsed-literal::

  :maroon:`def` value(): T

Reads the current value of this register


Implicit methods
----------------

.. parsed-literal::

  :maroon:`def` regBitToBit(x: :doc:`reg`\[:doc:`bit`\]): :doc:`bit`

Enables implicit reading from bit type Regs


*********

.. parsed-literal::

  :maroon:`def` regFixToFix(x: :doc:`reg`\[:doc:`fixpt`\[S,I,F\]\]): :doc:`fixpt`\[S,I,F\]

Enables implicit reading from fixed point type Regs


*********

.. parsed-literal::

  :maroon:`def` regFltToFlt(x: :doc:`reg`\[:doc:`fltpt`\[G,E\]\]): :doc:`fltpt`\[G,E\]

Enables implicit reading from floating point type Regs


Related methods
---------------

.. parsed-literal::

  :maroon:`def` ArgIn()(:maroon:`implicit` ev0: Num[T]): :doc:`reg`\[T\]

Creates an unnamed input argument from the host CPU


*********

.. parsed-literal::

  :maroon:`def` ArgOut()(:maroon:`implicit` ev0: Num[T]): :doc:`reg`\[T\]

Creats an unnamed output argument to the host CPU


