
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

.. _String:

String
======

Staged type representing arbitrary length Strings.
Note that this type shadows the respective unstaged Scala type.
In the case where an unstaged type is required, use the full name `java.lang.String`.

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`          **String**                                                                                                           |
+=====================+======================================================================================================================+
| |               def   **to**\[T\]: T                                                                                                       |
| |                       Converts this String to the specified type                                                                         |
| |                                                                                                                                          |
| |                       \[**NOTE**\] This method is currently defined for:                                                                 |
| |                          - :doc:`FixPt[_,_,_] <../common/fixpt>`                                                                         |
| |                          - :doc:`FltPt[_,_] <../common/fltpt>`                                                                           |
| |                          - :doc:`Boolean <../common/boolean>`                                                                            |
+---------------------+----------------------------------------------------------------------------------------------------------------------+

