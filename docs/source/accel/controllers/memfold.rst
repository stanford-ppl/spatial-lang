
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

.. _MemFold:

MemFold
=========

**MemFold** describes the reduction *across* multiple local memories. It functions essentially the same way as a
:doc:`memreduce`, except that it uses the existing value of the accumulator at the start of the reduction as the
initial value of the accumulator, rather than resetting.
Like MemReduce, unless otherwise disabled, the compiler will try to parallelize both the creation of multiple memories and the reduction
of each of these memories into a single accumulator.

--------------

**Static methods**

+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
|      `object`         **MemFold**                                                                                                                                                                                                                                                                                                                        |
+=====================+====================================================================================================================================================================================================================================================================================================================================+
| |               def   **apply**\[T,C\[T\]\](accum: C\[T\])(ctr: :doc:`../memories/counter`)(map: :doc:`Int <../../common/fixpt>` => C\[T\])(reduce: (T,T) => T): C\[T\]                                                                                                                                                                                  |
| |                       Memory reduction over a one dimensional space                                                                                                                                                                                                                                                                                    |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T,C\[T\]\](accum: C\[T\])(ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`)(map: (:doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`) => C\[T\])(reduce: (T,T) => T): C\[T\]                                                                                                            |
| |                       Memory reduction over a two dimensional space                                                                                                                                                                                                                                                                                    |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T,C\[T\]\](accum: C\[T\])(ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`, ctr3: :doc:`../memories/counter`)(map: (:doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`) => C\[T\])(reduce: (T,T) => T): C\[T\]                                         |
| |                       Memory reduction over a three dimensional space                                                                                                                                                                                                                                                                                  |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T,C\[T\]\](accum: C\[T\])(ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`, ctr3: :doc:`../memories/counter`, ctr4: :doc:`../memories/counter`, ctr5: :doc:`../memories/counter`\*)(map: List\[:doc:`Int <../../common/fixpt>`\] => C\[T\])(reduce: (T,T) => T): C\[T\]                               |
| |                       Memory reduction over an N-dimensional space                                                                                                                                                                                                                                                                                     |
| |                       Note that the **map** function is on a List of iterators.                                                                                                                                                                                                                                                                        |
| |                       The number of iterators will be the same as the number of counters supplied.                                                                                                                                                                                                                                                     |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
