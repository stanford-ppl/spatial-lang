
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

.. _Muxes:

Muxes
=====

Muxes are used to select between several values

Methods
-------

.. parsed-literal::

  :maroon:`def` max[T:Bits:Order](a: T, b: T): T

Selects the maximum of two given values. Implemented as a mux with a greater-than comparison


*********

.. parsed-literal::

  :maroon:`def` min[T:Bits:Order](a: T, b: T): T

Selects the minimum of two given values. Implemented as a mux with a less-than comparison


*********

.. parsed-literal::

  :maroon:`def` mux[T:Bits](sel: :doc:`boolean`, a: T, b: T): T

Creates a 2 input multiplexer.


