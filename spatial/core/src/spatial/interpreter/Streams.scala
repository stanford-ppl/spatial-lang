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

object Streams {
  var streamsIn = Map[Bus, Queue[Exp[_]]]()
  var streamsOut = Map[Bus, Queue[Exp[_]]]()

  def addStreamIn(bus: Bus) =
    streamsIn += ((bus, new Queue[Exp[_]]()))

  def addStreamOut(bus: Bus) =
    streamsOut += ((bus, new Queue[Exp[_]]()))
}

trait Streams extends AInterpreter {


  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case StreamInNew(bus) =>
      if (!Streams.streamsIn.contains(bus))
        Streams.addStreamIn(bus)

      Streams.streamsIn(bus)

    case StreamOutNew(bus) =>
      if (!Streams.streamsOut.contains(bus))
        Streams.addStreamOut(bus)

      Streams.streamsOut(bus)
      

    case StreamRead(a: Sym[_], b) =>
      val q = eval[Queue[Exp[_]]](a)
      var v: Exp[_] = null

      while (v == null) {
        if (Config.debug)
          println("Waiting for new input in " + a + "...")

        v = q.poll(1000, TimeUnit.MILLISECONDS)

        if (v == null && Config.debug) {
          println("No new input after 1s. q to quit or any key to continue waiting")
          if (io.StdIn.readLine() == "q") 
            System.exit(0)
        }
          
      }

      eval[Any](v)

    case StreamWrite(a, EAny(b), EBoolean(cond)) =>

      if (cond) {

        val q = eval[Queue[Any]](a)

        if (Config.debug)
          println("Push " + b + " to " + a)

        q.put(b)

      }
      

  }

}


