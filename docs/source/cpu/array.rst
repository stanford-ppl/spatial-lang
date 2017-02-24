
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

.. _Array:

Array
=====

Class and companion object for managing one dimensional arrays on the CPU.

**Static methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `object`         **Array**                                                                                                            |
+=====================+======================================================================================================================+
| |               def   **apply**\[T\](length: :doc:`Int <../common/fixpt>`): :doc:`array`\[T\]                                              |
| |                       Creates an empty array of type T with given length                                                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **empty**\[T\](length: :doc:`Int <../common/fixpt>`): :doc:`array`\[T\]                                              |
| |                       Creates an empty array of type T with given length                                                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **fill**\[T\](length: :doc:`Int <../common/fixpt>`)(f:  => T): :doc:`array`\[T\]                                     |
| |                       Creates an array with given length whose elements are determined by the supplied function                          |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **tabulate**\[T\](length: :doc:`Int <../common/fixpt>`)(f: :doc:`Int <../common/fixpt>` => T): :doc:`array`\[T\]     |
| |                       Creates an array with the given length whose elements are determined by the supplied                               |
| |                       indexed function                                                                                                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+



**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`          **Array**\[T\]                                                                                                       |
+=====================+======================================================================================================================+
| |               def   **length**: :doc:`Int <../common/fixpt>`                                                                             |
| |                       Returns the size of this Array                                                                                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **apply**\(index: :doc:`Int <../common/fixpt>`): T                                                                   |
| |                       Returns the element at **index**                                                                                   |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **update**\[T\](index: :doc:`Int <../common/fixpt>`, value: T)(f:  => T): Unit                                       |
| |                       Updates the element at **index** to **value**                                                                      |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **foreach**\(func: T => Unit): Unit                                                                                  |
| |                       Applies the function **func** on each element in the Array                                                         |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **map**\[R\](func: T => R): :doc:`array`\[R\]                                                                        |
| |                       Creates a new Array using the mapping **func** over each element in this Array                                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **zip**\[S, R\](that: :doc:`array`\[S\])(func: (T,S) => R): :doc:`array`\[R\]                                        |
| |                       Create a new Array using the pairwise mapping *f* over each element in this Array and                              |
| |                       corresponding element in *that*                                                                                    |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **reduce**\(func: (T,T) => T): T                                                                                     |
| |                       Reduces the elements in this Array into a single element using associative function **func**                       |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **filter**\(predicate: T => :doc:`../common/boolean`): :doc:`array`\[T\]                                             |
| |                       Creates a new Array with all elements in this Array which satisfy the given **predicate**                          |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **flatMap**\[R\](func: T => :doc:`array`\[R\]): :doc:`array`\[R\]                                                    |
| |                       Creates a new Array by concatenating the results of **func** applied to all elements in this Array                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **groupByReduce**\[K,V\](key: T => K)(value: T => V)(reduce: (V,V) => V): HashMap[K,V]                               |
| |                       Partitions this array using the **key** function, then maps each element using **value**, and                      |
| |                       finally combines values in each bin using **reduce**                                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+



