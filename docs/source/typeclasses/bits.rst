
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

.. _Bits:

Bits
====

Type class used to supply evidence that type T is representable by a statically known number of bits.

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


Related methods
---------------

.. parsed-literal::

  :maroon:`def` zero[T:Bits]

Creates the zero value for the type T

*********

.. parsed-literal::

  :maroon:`def` one[T:Bits]: T

Creates the one value for the type T

*********

.. parsed-literal::

  :maroon:`def` random[T:Bits]: T

Generates a pseudorandom value uniformly distributed between 0 and 1.

[:blue:`NOTE`] This method is currently unsynthesizable, and should be used only on the CPU host or in simulation.

*********

.. parsed-literal::

  :maroon:`def` random[T:Bits](max: T): T

Generates a pseudorandom value uniformly distributed between 0 and max.

[:blue:`NOTE`] This method is currently unsynthesizable, and should be used only on the CPU host or in simulation.






