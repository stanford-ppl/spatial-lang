package spatial.lang.static

import argon.core._
import argon.lang.direct.{ArgonExp, ArgonApi}
import forge._
import spatial.SpatialConfig
import spatial.lang.cake.SpatialLangAliases

trait SpatialExp extends ArgonExp with SpatialLangAliases {

  def spatialConfig(implicit state: State): SpatialConfig = state.config.asInstanceOf[SpatialConfig]

}

trait SpatialImplicits { this: SpatialApi =>
   // Hacks required to allow .to[T] syntax on various primitive types
  // Naming is apparently important here (has to have same names as in Predef)
  implicit class longWrapper(x: scala.Long) {
    @api def to[B:Type](implicit cast: Cast[scala.Long,B]): B = cast(x)
  }
  implicit class floatWrapper(x: scala.Float) {
    @api def to[B:Type](implicit cast: Cast[scala.Float,B]): B = cast(x)
  }
  implicit class doubleWrapper(x: scala.Double) {
    @api def to[B:Type](implicit cast: Cast[scala.Double,B]): B = cast(x)
  }
}

trait SpatialApi extends ArgonApi with SpatialExp
  with SpatialImplicits
  with BitOpsApi
  with RangeApi
  with DebuggingApi
  with FileIOApi
  with HostTransferApi
  with MathApi
  with MatrixApi
  with ParametersApi
  with PrintingApi
  with RegApi
  with StreamApi
  with VectorApi
