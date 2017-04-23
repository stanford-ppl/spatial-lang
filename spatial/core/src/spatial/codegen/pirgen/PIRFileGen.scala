package spatial.codegen.pirgen

import argon.Config
import argon.codegen.FileGen

import scala.language.postfixOps
import scala.sys.process._

trait PIRFileGen extends FileGen {
  import IR._

  override protected def emitMain[S:Type](b: Block[S]): Unit = emitBlock(b)

  override protected def emitFileHeader() {
    emit("import pir.graph")
    emit("import pir.graph._")
    emit("import pir.codegen._")
    emit("import pir.plasticine.config._")
    emit("import pir.Design")
    emit("import pir.util.enums._")
    emit("import pir.util._")
    emit("import pir.PIRApp")
    emit("")
    open(s"""object ${Config.name} extends PIRApp {""")
    //emit(s"""override val arch = SN_4x4""")
    open(s"""def main(args: String*)(top:Top) = {""")

    super.emitFileHeader()
  }

  override protected def emitFileFooter() {
    emit(s"")
    close("}")
    close("}")

    super.emitFileFooter()
  }

  override protected def process[S:Type](b: Block[S]): Block[S] = {
    super.process(b)
    //TODO: Cannot treat this as a dependency because postprocess is called before stream is closed
    if (sys.env("PIR_HOME") != "") {
      // what should be the cleaner way of doing this?
      val cmd = s"cp ${Config.genDir}/pir/main.scala ${sys.env("PIR_HOME")}/apps/src/${Config.name}.scala"
      println(cmd)
      cmd.!
    }
    else {
      warn("Set PIR_HOME environment variable to automatically copy app")
    }
    b
  }

}
