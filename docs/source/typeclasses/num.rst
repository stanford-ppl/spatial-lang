
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

.. _Num:

Num
====

Combination of Arith, Bits, and Order type classes

Abstract Methods
----------------

.. parsed-literal::

  :maroon:`def` zero: T

Creates the zero value for the type T

*********

.. parsed-literal::

  :maroon:`def` one: T

Creates the one value for the type T

*********

.. parsed-literal::

  :maroon:`def` random(max: Option[T]): T

Generates a pseudorandom value uniformly distributed between 0 and max, or 0 and 1 if max is unspecified.

[:blue:`NOTE`] This method is currently unsynthesizable, and should be used only on the CPU host or in simulation.

*********

.. parsed-literal::

  :maroon:`def` length: T

Returns the number of bits required to represent this type.

*********

.. parsed-literal::

  :maroon:`def` negate(x: T): T

Negate x

*********

.. parsed-literal::

  :maroon:`def` plus(x: T, y: T): T

Add x and y

*********

.. parsed-literal::

  :maroon:`def` minus(x: T, y: T): T

Subtract y from x

*********

.. parsed-literal::

  :maroon:`def` times(x: T, y: T): T

Multiply x and y

*********

.. parsed-literal::

  :maroon:`def` divide(x: T, y: T): T

Divide x by y

*********

.. parsed-literal::

  :maroon:`def` lessThan(x: T, y: T): :doc:`boolean`

Returns `true` if x is less than y, `false` otherwise

*********

.. parsed-literal::

  :maroon:`def` lessThanOrEqual(x: T, y: T): :doc:`bit`

Returns `true` if x is less than or equal to y, `false` otherwise

*********

.. parsed-literal::

  :maroon:`def` equal(x: T, y: T): :doc:`boolean`

Returns `true` if x and y are equal, `false` otherwise


