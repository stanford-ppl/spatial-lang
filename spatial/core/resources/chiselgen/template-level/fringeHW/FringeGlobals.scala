package fringe

// Some global constants
object FringeGlobals {
  private var _target: String = ""
  def target = _target
  def target_= (value: String): Unit = _target = value
}
