
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

.. _FIFO:

FIFO
====


FIFOs are on-chip scratchpads with additional control logic for address-less push/pop operations.


Direct methods
--------------

.. parsed-literal::

  :maroon:`def` FIFO\[T:Staged::doc:`bits`\](size: :doc:`Index <fixpt>`): :doc:`fifo`\[T\]

Creates a FIFO with given size. Size must be a statically determinable signed integer.


Infix methods
-------------

.. parsed-literal::

  :maroon:`def` load(tile: :doc:`tile`\[T\]): Unit

Creates a burst load of a :doc:`tile` of a :doc:`dram` memory to this :doc:`fifo`.

*********

.. parsed-literal::

  :maroon:`def` deq(): T

Creates a dequeue (read) port to this FIFO


*********

.. parsed-literal::

  :maroon:`def` deq(rden: :doc:`boolean`): T

Creates a dequeue (read) port to this FIFO with specified enable *rden*.


*********

.. parsed-literal::

  :maroon:`def` enq(data: T): Unit

Creates a enqueue (write) port to this FIFO

	* **value** \- the value to be pushed into the FIFO

*********

.. parsed-literal::

  :maroon:`def` enq(data: T, wren: :doc:`bit`): Unit

Creates a enqueue (write) port to this FIFO with specified write enable

	* **data** \- the value to be pushed into the FIFO
	* **wren** \- write enable

