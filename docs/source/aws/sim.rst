1. Simulation
=============

Why Run Simulation
------------------

We intend for Spatial applications to work in hardware without requiring simulation. But for the alpha release
of Spatial, the user may prefer to first simulate the design before paying to open an F1 FPGA instance. This 
tutorial describes how to simulate your Spatial application using the simulation environment provided by
Amazon in their Development Kit.

These steps can be done on any machine with the Vivado XSIM simulator (provided in the Amazon FPGA Developer AMI).
An FPGA instance is not needed for this simulation tutorial.

To skip simulation and run directly in hardware, see the :doc:`F1 tutorial<F1>`.


Generating the Application for Simulation
-----------------------------------------

.. highlight:: bash

The first step is the same as compiling a Spatial application for any other target, shown here for the ``MatMult_outer`` example::

    cd ${SPATIAL_HOME}
    bin/spatial MatMult_outer --chisel

You can replace ``MatMult_outer`` with any application, as described in :doc:`the previous tutorial<../tutorial/helloworld>`.

Now generate the simulation binary ``Top``::

    cd ${SPATIAL_HOME}/gen/MatMult_outer
    make aws-sim
    ./Top <arguments>

Notice that the final two steps above both need the Vivado XSim simulator. Other simulators can be used with the Amazon Development Kit but this has not been tested with Spatial.

The simulation typically takes ten minutes or longer to complete.

If the simulation is successful, the following output will be seen::

    PASS: 1 (App Name)

If your application completed successfully, next you can run it in :doc:`hardware<F1>`.

