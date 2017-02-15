
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

.. _Mem:

Mem
====

Type class used to supply evidence that type T is a local memory, potentially with multiple dimensions.


Infix methods
-------------

.. parsed-literal::

  :maroon:`def` load(mem: C\[T\], indices: Seq\[:doc:`Index <fixpt>`\], en: :doc:`boolean`): T

Loads an element from mem at address given by indices and with enable signal en


*********

.. parsed-literal::

  :maroon:`def` store(mem: C\[T\], indices: Seq\[:doc:`Index <fixpt>`\], data: T, en: :doc:`boolean`): Unit

Stores the element data into mem at address given by indices with enable signal en


*********

.. parsed-literal::

  :maroon:`def` iterator(mem: C\[T\]): Seq\[:doc:`counterchain`\]

Creates counters which iterate over the (optionally multi-dimensional) memory mem
