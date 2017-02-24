
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

.. _Tile:

Tile
====


A **Tile** describes a continguous slice of a DRAM memory which can be loaded onto the accelerator for processing or which can be updated
with results once computation is complete.

----------------

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`         **Tile**\[T\]                                                                                                         |
+=====================+======================================================================================================================+
| |               def   **store**\(data: :doc:`sram`\[T\]): Unit                                                                             |
| |                       Creates a burst store from **data** to the section of DRAM described by this Tile.                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **store**\(data: :doc:`fifo`\[T\]): Unit                                                                             |
| |                       Creates a burst store from **data** to the section of DRAM described by this Tile.                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+


