case class Struct1(
  var offset: Number,
  var size: Number,
  var isLoad: Bit
) {

  override def productPrefix = "BurstCmd"
}

