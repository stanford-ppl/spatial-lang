
Vector Inner Product
====================

Inner product (also called dot product) is an extremely simple linear algebra kernel, defined as the
sum of the element-wise products between two vectors of data. For this example, we'll assume that the
data in this case are scalar Floats. You could, however, also do the same operations with custom struct types.

Let's look at how to write it in Spatial. Let's start with the application's template::

    import spatial._
    import org.virtualized._

    object MyDotProduct extends SpatialApp {

        @virtualize def dotproduct(a: Array[Float], b: Array[Float]): Array[Float] = {
            ???
        }

        @virtualize
        def main(): Unit = {
            ???
        }
    }

We first need some local memory to hold our vectors. Let's suppose for now each one has a size of 100::


So far so good. Now we need to get some data into the SRAMs.

