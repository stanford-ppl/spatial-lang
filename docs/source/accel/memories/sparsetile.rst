
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

.. _SparseTile:

SparseTile
==========


A SparseTile describes a sparse section of a DRAM memory which can be loaded onto the accelerator using a gather operation, or which can
be updated using a scatter operation.


Infix methods
-------------

.. parsed-literal::

  :maroon:`def` :=(sram: :doc:`sram`\[T\]): Unit

Creates a store from the given on-chip SRAM to this SparseTile of off-chip memory 


Related methods
---------------

.. parsed-literal::

  :maroon:`def` copySparse(tile: :doc:`sparsetile`\[T\], local: :doc:`sram`\[T\], isScatter: Boolean): Unit




