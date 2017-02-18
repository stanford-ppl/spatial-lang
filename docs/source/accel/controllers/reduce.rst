
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

.. _Reduce:

Reduce
======

**Reduce** is a control node which takes many scalar values and combines them into one value using some associative operator.
Unless otherwise disabled, the compiler will attempt to automatically pipeline and parallelize *Reduce* nodes.
A Reduce consists of a *map* function, which is responsible for producing scalar values, and
a *reduction* function to describe how the values should be combined.

Calling any form of Reduce returns the accumulator which, at the end of the loop, will contain the final result of the reduction.



--------------

**Static methods**

+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
|      `object`         **Reduce**                                                                                                                                                                                                                                                                                                                         |
+=====================+====================================================================================================================================================================================================================================================================================================================================+
| |               def   **apply**\[T\](ctr: :doc:`../memories/counter`)(map: :doc:`Int <../../common/fixpt>` => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]                                                                                                                                                                                        |
| |                       Reduction over a one dimensional space with implicit accumulator                                                                                                                                                                                                                                                                 |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`)(map: (:doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`) => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]                                                                                                                  |
| |                       Reduction over a two dimensional space with implicit accumulator                                                                                                                                                                                                                                                                 |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`, ctr3: :doc:`../memories/counter`)(map: (:doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`) => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]                                               |
| |                       Reduction over a three dimensional space with implicit accumulator                                                                                                                                                                                                                                                               |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`, ctr3: :doc:`../memories/counter`, ctr4: :doc:`../memories/counter`, ctr5: :doc:`../memories/counter`\*)(map: List\[:doc:`Int <../../common/fixpt>`\] => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]                                     |
| |                       Reduction over an N-dimensional space with implicit accumulator                                                                                                                                                                                                                                                                  |
| |                       Note that the **map** function is on a List of iterators.                                                                                                                                                                                                                                                                        |
| |                       The number of iterators will be the same as the number of counters supplied.                                                                                                                                                                                                                                                     |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](identity: T)(ctr: :doc:`../memories/counter`)(map: :doc:`Int <../../common/fixpt>` => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]                                                                                                                                                                           |
| |                       Reduction over a one dimensional space with implicit accumulator but explicit identity value                                                                                                                                                                                                                                     |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](identity: T)(ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`)(map: (:doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`) => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]                                                                                                     |
| |                       Reduction over a two dimensional space with implicit accumulator but explicit identity value                                                                                                                                                                                                                                     |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](identity: T)(ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`, ctr3: :doc:`../memories/counter`)(map: (:doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`) => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]                                  |
| |                       Reduction over a three dimensional space with implicit accumulator but explicit identity value                                                                                                                                                                                                                                   |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](identity: T)(ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`, ctr3: :doc:`../memories/counter`, ctr4: :doc:`../memories/counter`, ctr5: :doc:`../memories/counter`\*)(map: List\[:doc:`Int <../../common/fixpt>`\] => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]                        |
| |                       Reduction over an N-dimensional space with implicit accumulator but explicit identity value                                                                                                                                                                                                                                      |
| |                       Note that the **map** function is on a List of iterators.                                                                                                                                                                                                                                                                        |
| |                       The number of iterators will be the same as the number of counters supplied.                                                                                                                                                                                                                                                     |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](accum: :doc:`../memories/reg`\[T\])(ctr: :doc:`../memories/counter`)(map: :doc:`Int <../../common/fixpt>` => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]                                                                                                                                                    |
| |                       Reduction over a one dimensional space with explicit accumulator                                                                                                                                                                                                                                                                 |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](accum: :doc:`../memories/reg`\[T\])(ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`)(map: (:doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`) => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]                                                                              |
| |                       Reduction over a two dimensional space with explicit accumulator                                                                                                                                                                                                                                                                 |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](accum: :doc:`../memories/reg`\[T\])(ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`, ctr3: :doc:`../memories/counter`)(map: (:doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`, :doc:`Int <../../common/fixpt>`) => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\]           |
| |                       Reduction over a three dimensional space with explicit accumulator                                                                                                                                                                                                                                                               |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\[T\](accum: :doc:`../memories/reg`\[T\])(ctr1: :doc:`../memories/counter`, ctr2: :doc:`../memories/counter`, ctr3: :doc:`../memories/counter`, ctr4: :doc:`../memories/counter`, ctr5: :doc:`../memories/counter`\*)(map: List\[:doc:`Int <../../common/fixpt>`\] => T)(reduce: (T,T) => T): :doc:`../memories/reg`\[T\] |
| |                       Reduction over an N-dimensional space with explicit accumulator                                                                                                                                                                                                                                                                  |
| |                       Note that the **map** function is on a List of iterators.                                                                                                                                                                                                                                                                        |
| |                       The number of iterators will be the same as the number of counters supplied.                                                                                                                                                                                                                                                     |
+---------------------+------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+


