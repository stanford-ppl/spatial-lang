
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

.. _Boolean:

Boolean
=======

Boolean represents a staged single boolean value.
Note that this type shadows the unstaged Scala Boolean.
In the case where an unstaged Boolean type is required, use the full `scala.Boolean` name.

**Infix methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
|      `class`          **Boolean**                                                                                                          |
+=====================+======================================================================================================================+
| |               def   **unary_!**\: :doc:`boolean`                                                                                         |
| |                       Negates the given boolean expression                                                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **&&**\(y: :doc:`boolean`): :doc:`boolean`                                                                           |
| |                       Boolean AND.                                                                                                       |
| |                       Compares two booleans and returns `true` if both are `true`                                                        |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **||**\(y: :doc:`boolean`): :doc:`boolean`                                                                           |
| |                       Boolean OR.                                                                                                        |
| |                       Compares two booleans and returns `true` if at least one is `true`                                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **^**\(y: :doc:`boolean`): :doc:`boolean`                                                                            |
| |                       Boolean exclusive-or (XOR).                                                                                        |
| |                       Compares two booleans and returns `true` if exactly one is `true`                                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **!=**\(y: :doc:`boolean`): :doc:`boolean`                                                                           |
| |                       Value inequality, equivalent to exclusive-or (XOR).                                                                |
| |                       Compares two booleans and returns `true` if exactly one is `true`                                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **==**\(y: :doc:`boolean`): :doc:`boolean`                                                                           |
| |                       Value equality, equivalent to exclusive-nor (XNOR).                                                                |
| |                       Compares two booleans and returns `true` if both are `true` or both are `false`                                    |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **toString**\: :doc:`../cpu/string`                                                                                  |
| |                       Creates a printable String from this value                                                                         |
| |                                                                                                                                          |
| |                       \[**NOTE**\] This method is unsynthesizable, and can be used only on the CPU or in simulation.                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
