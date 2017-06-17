
Spatial 0.1 Documentation
=========================

About Spatial
-------------
 Spatial is a domain-specific language for describing hardware accelerators for use on FPGAs and other supported spatial architectures.
 The language is intended to be both higher level than hardware description languages (HDLs) like Verilog, VHDL, and `Chisel <https://chisel.eecs.berkeley.edu/>`_,
 while also being easier to use than Altera's OpenCL or high level synthesis (HLS) languages like Xilinx's Vivado.

 Language features of Spatial include:
   * Tunable, hardware specific templates
   * User specified and implicitly created design parameters
   * Design runtime and area analysis
   * Automatic parameter tuning
   * Automatic memory banking and buffering

Getting Started
===============

Check out our :doc:`tutorial <tutorial/starting>` to get started!

If you prefer to learn by example, take a look at our :doc:`Spatial code examples <examples>`

.. To run Spatial on the new Amazon EC2 FPGA instances, see :doc:`the AWS Tutorial <aws/intro>`.


Contents
--------

.. toctree::
   :maxdepth: 2

   common
   software
   hardware
   tutorial
   examples
   .. aws
   theory