package spatial.codegen

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext

import scala.collection.mutable
import scala.collection.mutable.WrappedArray
import scala.reflect.runtime.universe.{Block => _, Type => _, _}
import scala.reflect.ClassTag

package object pirgen {

  val metadatas = scala.collection.mutable.ListBuffer[MetadataMaps]()

  type Expr = Exp[_]

}
