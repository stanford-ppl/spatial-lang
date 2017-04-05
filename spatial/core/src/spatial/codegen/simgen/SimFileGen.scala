package spatial.codegen.simgen

import argon.codegen.FileGen

trait SimFileGen extends FileGen {
  import IR._

  override protected def emitMain[S:Type](b: Block[S]): Unit = {
    open(src"object Main {")
    open(src"def main(args: Array[String]): Unit = {")
    emitBlock(b)
    close(src"}")
    close(src"}")
  }
}
