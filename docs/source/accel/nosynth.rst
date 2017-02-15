
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

.. _Nosynth:

Unsynthesizable
===============

Operations commonly used for testing/computation on the host


Direct methods
--------------


*********

.. parsed-literal::

  :maroon:`def` assert(x: :doc:`boolean`): Unit




*********

.. parsed-literal::

  :maroon:`def` getArg\[T::doc:`bits`\](x: :doc:`reg`\[T\]): T




*********

.. parsed-literal::

  :maroon:`def` getMem(x: :doc:`dram`\[T\]): :doc:`array`\[T\]




*********

.. parsed-literal::

  :maroon:`def` getSRAM(sram: :doc:`sram`\[T\]): :doc:`array`\[T\]

Get content of SRAM in an array format (debugging purpose only) 


*********

.. parsed-literal::

  :maroon:`def` println(x: Any): Unit




*********

.. parsed-literal::

  :maroon:`def` println(): Unit




*********

.. parsed-literal::

  :maroon:`def` setArg(x: :doc:`reg`\[T\], y: T): Unit




*********

.. parsed-literal::

  :maroon:`def` setArg(x: :doc:`reg`\[T\], y: Int): Unit




*********

.. parsed-literal::

  :maroon:`def` setArg(x: :doc:`reg`\[T\], y: Long): Unit




*********

.. parsed-literal::

  :maroon:`def` setArg(x: :doc:`reg`\[T\], y: Float): Unit




*********

.. parsed-literal::

  :maroon:`def` setArg(x: :doc:`reg`\[T\], y: Double): Unit




*********

.. parsed-literal::

  :maroon:`def` setMem(x: :doc:`dram`\[T\], y: :doc:`forgearray`\[T\]): Unit




*********

.. parsed-literal::

  :maroon:`def` setSRAM(sram: :doc:`sram`\[T\], array: :doc:`forgearray`\[T\]): Unit

Set content of SRAM to an array (debugging purpose only) 


