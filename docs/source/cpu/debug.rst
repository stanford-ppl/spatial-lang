
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

.. _Debug:

Debugging Operations
====================

These operations are available for use on the CPU and during simulation to aid runtime debugging.


**Methods**

+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **assert**\(cond: :doc:`../common/boolean`): Unit                                                                    |
| |                       Checks that the given condition **cond** is true. If not, exits the program with an exception.                     |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **assert**\(cond: :doc:`../common/boolean`, message: :doc:`string`): Unit                                            |
| |                     Checks that the given condition **cond** is true. If not, exits the program with the given **message**.              |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **print**\(message: :doc:`string`): Unit                                                                             |
| |                       Prints the given **message** to the console (without a linebreak)                                                  |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **println**\(): Unit                                                                                                 |
| |                       Prints a blank line to the console                                                                                 |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
| |               def   **println**\(message: :doc:`string`): Unit                                                                           |
| |                       Prints the given **message** to the console, followed by a linebreak                                               |
+---------------------+----------------------------------------------------------------------------------------------------------------------+
