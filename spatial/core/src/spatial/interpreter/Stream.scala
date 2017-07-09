package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import math.BigDecimal
import java.util.concurrent.{ LinkedBlockingQueue => Queue }
import java.util.concurrent.TimeUnit
import spatial.SpatialConfig
import spatial.targets.Bus

object IStream {
  var streamsIn = Map[Bus, Queue[Any]]()
  var streamsOut = Map[Bus, Queue[Any]]()

  def addStreamIn(bus: Bus) =
    streamsIn += ((bus, new Queue[Any]()))

  def addStreamOut(bus: Bus) =
    streamsOut += ((bus, new Queue[Any]()))
}

trait IStream extends AInterpreter {


  override def matchNode  = super.matchNode.orElse {

    case StreamInNew(bus) =>
      if (!IStream.streamsIn.contains(bus))
        IStream.addStreamIn(bus)

      IStream.streamsIn(bus)

    case StreamOutNew(bus) =>
      if (!IStream.streamsOut.contains(bus))
        IStream.addStreamOut(bus)

      IStream.streamsOut(bus)
      

    case StreamRead(a: Sym[_], b) =>
      val q = eval[Queue[Any]](a)
      var v: Any = null

      while (v == null && !Interpreter.closed) {
        if (SpatialConfig.debug)
          println("Waiting for new input in " + a + "...")

        v = q.poll(1000, TimeUnit.MILLISECONDS)

        if (v == null && SpatialConfig.debug) {
          println("No new input after 1s. q to quit or any key to continue waiting")
          if (io.StdIn.readLine() == "q") 
            System.exit(0)
        }
          
      }

      eval[Any](v)

    case StreamWrite(a, EAny(b), EBoolean(cond)) =>

      if (cond) {

        val q = eval[Queue[Any]](a)

        if (SpatialConfig.debug)
          println("Push " + b + " to " + a)

        q.put(b)

      }
      

  }

}


