package spatial

package object metadata {
  type AccessPattern = argon.analysis.AccessPattern
  val AccessPattern = argon.analysis.AccessPattern

  type GeneralAffine = argon.analysis.GeneralAffine
  val GeneralAffine = argon.analysis.GeneralAffine

  type IndexPattern = argon.analysis.IndexPattern
  type AffineAccess = argon.analysis.AffineAccess
  val AffineAccess = argon.analysis.AffineAccess
  type OffsetAccess = argon.analysis.OffsetAccess
  val OffsetAccess = argon.analysis.OffsetAccess
  type StridedAccess = argon.analysis.StridedAccess
  val StridedAccess = argon.analysis.StridedAccess
  type LinearAccess = argon.analysis.LinearAccess
  val LinearAccess = argon.analysis.LinearAccess
  type InvariantAccess = argon.analysis.InvariantAccess
  val InvariantAccess = argon.analysis.InvariantAccess
  val RandomAccess = argon.analysis.RandomAccess

  val accessPatternOf = argon.analysis.accessPatternOf
}
