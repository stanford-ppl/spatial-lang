1. Simulation
=============

Why Run Simulation
------------------

We intend for Spatial applications to work in hardware without requiring simulation. But for the alpha release
of Spatial, the user may prefer to first simulate the design before paying to open an F1 FPGA instance. This 
tutorial describes how to simulate your Spatial application using the simulation environment provided by
Amazon in their Development Kit.

These steps can be done on any machine with the Vivado XSIM simulator.
We did this both on a local machine as well as on an EC2 machine with the Amazon FPGA Developer AMI.
An FPGA instance is not needed for this simulation tutorial.

To skip simulation and run directly in hardware, see the :doc:`F1 tutorial<F1>`.


Generating the Application for Simulation
-----------------------------------------

.. highlight:: bash

The first step is the same as compiling a Spatial application for any other target, shown here for the ``SimpleTileLoadStore`` example::

    cd ${SPATIAL_HOME}
    bin/spatial SimpleTileLoadStore --synth

You can replace ``SimpleTileLoadStore`` with any application, as described in :doc:`the previous tutorial<../tutorial/helloworld>`.
Note however that the XSIM simulation seems to have a default timeout and that applications which require too many cycles will not finish.
We recommend trying a smaller version of your application for now.

Now generate the simulation binary ``Top``::

    cd ${SPATIAL_HOME}/gen/SimpleTileLoadStore
    make aws-sim
    ./Top 100

Notice that the final two steps above both need the Vivado XSim simulator. Other simulators can be used with the Amazon Development Kit but this has not been tested with Spatial.

The simulation typically takes ten minutes or longer to complete.

If the simulation is successful, the following output will be seen::

    PASS: 1 (App Name)

If your application completed successfully, next you can run it in :doc:`hardware<F1>`.

