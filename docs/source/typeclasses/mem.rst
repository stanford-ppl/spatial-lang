
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


**Abstract Methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `trait`         **Mem**\[T,C\]                                                                                                        |
+=====================+======================================================================================================================+
| |      abstract def   **load**\(mem: C\[T\], indices: Seq\[:doc:`Int <../common/fixpt>`\], en: :doc:`../common/boolean`): T                |
| |                       Loads an element from mem at address given by indices and with enable signal en                                    |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **store**\(mem: C\[T\], indices: Seq\[:doc:`Int <../common/fixpt>`\], data: T, en: :doc:`../common/boolean`): Unit   |
| |                       Stores the element data into mem at address given by indices with enable signal en                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |      abstract def   **iterator**\(mem: C\[T\]): Seq\[:doc:`../accel/memories/counterchain`\]                                             |
| |                       Creates counters which iterate over the (optionally multi-dimensional) memory mem                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

