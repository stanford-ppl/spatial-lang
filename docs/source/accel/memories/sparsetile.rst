
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


A **SparseTile** describes a sparse section of a DRAM memory which can be loaded onto the accelerator using a gather operation, or which can
be updated using a scatter operation.

--------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`         **SparseTile**\[T\]                                                                                                   |
+=====================+======================================================================================================================+
| |               def   **scatter**\(data: :doc:`sram`\[T\]): Unit                                                                           |
| |                       Creates a scatter store to the addresses of DRAM described by this SparseTile.                                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


