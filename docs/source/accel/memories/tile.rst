
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


A Tile describes a continguous slice of a DRAM memory which can be loaded onto the accelerator for processing or which can be updated
with results once computation is complete.


Infix methods
-------------

.. parsed-literal::

  :maroon:`def` :=(sram: :doc:`sram`\[T\])(:maroon:`implicit` ev0: Num[T]): Unit

Creates a store from the given on-chip SRAM to this Tile of off-chip memory 


*********

.. parsed-literal::

  :maroon:`def` :=(y: :doc:`fifo`\[T\])(:maroon:`implicit` ev0: Num[T]): Unit




