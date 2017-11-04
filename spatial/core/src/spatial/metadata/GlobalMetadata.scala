package spatial.metadata

import argon.core._

case class LocalMemories(mems: List[Exp[_]]) extends Globaldata[LocalMemories]