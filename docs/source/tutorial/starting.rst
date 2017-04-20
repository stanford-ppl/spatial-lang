0. Getting Started
==================

Prerequisites
-------------

First, make sure to download and install the following prerequisites:

- `Python (2 or 3) <https://www.python.org/downloads/>`_
- `Scala SBT <http://www.scala-sbt.org>`_
- `Java JDK <http://www.oracle.com/technetwork/java/javase/downloads/index.html>`_

While it's not necessarily required, it may be easier to learn to use Spatial if you've had experience with Scala
or a similar functional programming language in the past.

If you'd like, check out a `Scala tutorial <https://www.tutorialspoint.com/scala/>`_ like the one linked here for general info on programming in Scala.

Finally, please sign up for the `Spatial users google group <https://groups.google.com/forum/#!forum/spatial-lang-users>`_ if you have any questions. 


Installation (From Binary)
--------------------------

Run the following command to clone the quickstart repository::

    git clone https://github.com/stanford-ppl/spatial-quickstart.git
    
To test to make sure it's working::

    bin/spatial HelloSpatial
    ./HelloSpatial.sim 32

That's it! You're ready to create and run Spatial programs!



Installation (From Source)
--------------------------

Clone two repositories: `argon` and `spatial-lang`.
You can place these anywhere as long as you point your environment variables correctly.
This tutorial will assume you place all three in ${HOME}/spatial.

.. highlight:: bash

Run the following (bash) commands to clone::

    mkdir ${HOME}/spatial
    cd ${HOME}/spatial
    git clone https://github.com/stanford-ppl/spatial-lang.git
    git clone https://github.com/stanford-ppl/argon.git

Note that the current setup assumes spatial-lang and argon are on the ``master`` branch.

Next, make sure the following environment variables are set.  If you are using the recommended
directory structure in this tutorial, then you can simply run the following command::

    cd ${HOME}/spatial/spatial-lang
    source ./init-env.sh

If you have some other structure, you need to set the following variables manually.
It may be easiest to set them in your terminal startup script (e.g. bashrc) so all future sessions have them::

    export JAVA_HOME = ### Directory Java is installed, usually /usr/bin
    export ARGON_HOME = ### Top directory of argon
    export SPATIAL_HOME = ### Top directory of spatial-lang
    export VIRTUALIZED_HOME = ### Top directory of scala-virtualized

Once these are all set, you are ready to compile the language.  Run the following::

    cd ${SPATIAL_HOME}
    make full

A good habit would be to pull from these repositories often and run ``make full`` in ``SPATIAL_HOME``.


That's it! Up next, :doc:`learn how to build a basic Spatial program <helloworld>`.
