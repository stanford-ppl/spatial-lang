package argon.codegen.chiselgen

import argon.core._
import argon.codegen.{Codegen, FileDependencies}
import argon.NoBitWidthException

import scala.collection.mutable
import scala.language.postfixOps
import sys.process._

trait ChiselCodegen extends Codegen with FileDependencies { // FileDependencies extends Codegen already
  override val name = "Chisel Codegen"
  override val lang: String = "chisel"
  override val ext: String = "scala"
  var controllerStack = scala.collection.mutable.Stack[Exp[_]]()

  var alphaconv = mutable.HashMap[String, String]() // Map for tracking defs of nodes and if they get redeffed anywhere, we map it to a suffix
  var maxretime: Int = 0 // Look for the biggest retime in the app and have the app wait this many cycles before enabling root controllerStack
  var disableSplit: Boolean = false // Temporary hack to avoid splitting files from overflow while emitting code that belongs together

  final def alphaconv_register(xx: String): Unit = {
    val x = "_reuse[0-9]+".r.replaceAllIn(xx, "")
    if (alphaconv.contains(x)) {
      val suf = alphaconv(x).replace("_reuse","")
      if (suf == "") {
        alphaconv += (x -> "_reuse1") // If already used, increment suffix
      } else {
        val newsuf = suf.toInt + 1
        alphaconv += (x -> s"_reuse$newsuf")
      }
    } else {
      alphaconv += (x -> "") // Otherwise don't suffix it
    }
  }

  override protected def quoteOrRemap(arg: Any): String = arg match {
    case e: Exp[_] => wireMap(quote(e) + alphaconv.getOrElse(quote(e), ""))
    case _ => super.quoteOrRemap(arg)
  }

  override protected def emitBlock(b: Block[_]): Unit = {
    visitBlock(b)
    emit(src"// results in ${b.result}")
  }

  final protected def emitController(b: Block[_]): Unit = {
    visitBlock(b)
    emit(src"// results in ${b.result}")
  }

  final protected def emitGlobalWire(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("GlobalWires")) {
      emit(x, forceful)
    }
  }

  final protected def emitGlobalWireMap(lhs: String, rhs: String, forceful: Boolean = false): Unit = {
    val stripped = rhs.replace("new ", "newnbsp").replace(" ", "").replace("nbsp", " ")
    if (config.multifile == 5 | config.multifile == 6) {
      if (!compressorMap.contains(lhs)) {
        val id = compressorMap.values.map(_._1).filter(_ == stripped).size
        compressorMap += (lhs -> (stripped, id))
      }
    } else {
      if (compressorMap.contains(lhs)) {
        emitGlobalWire(src"// val $lhs = $rhs already emitted", forceful)
      } else {
        compressorMap += (lhs -> (stripped, 0))
        emitGlobalWire(src"val $lhs = $rhs", forceful)
      }
    }
  }

  final protected def emitGlobalRetimeMap(lhs: String, rhs: String, forceful: Boolean = false): Unit = {
    val stripped = rhs.replace(" ", "")
    if (config.multifile == 5 | config.multifile == 6) {
      // Assume _retime values only emitted once
      val id = compressorMap.values.map(_._1).filter(_ == "_retime").size
      compressorMap += (lhs -> ("_retime", id))
      retimeList += rhs
    } else {
      emitGlobalWire(src"val $lhs = $rhs", forceful)
    }
  }

  final protected def emitGlobalModuleMap(lhs: String, rhs: String, forceful: Boolean = false): Unit = {
    val stripped_white = rhs.replace("new ", "newnbsp").replace(" ", "").replace("nbsp", " ")
    var rtid = "na"
    if (config.multifile == 5 | config.multifile == 6) {
      val stripped = if (stripped_white.contains("retime=")) {
        val extract = ".*retime=rt\\(([0-9]+)\\),.*".r
        val extract(x) = stripped_white
        rtid = x
        stripped_white.replace(s"retime=rt(${rtid}),","")
      } else {
        stripped_white
      }
      if (!compressorMap.contains(lhs)) {
        val id = compressorMap.values.map(_._1).filter(_ == stripped).size
        compressorMap += (lhs -> (stripped, id))
        if (rtid != "na") {
          pipeRtMap += ((stripped, id) -> rtid)
        }
      }
    } else {
      val stripped = stripped_white
      if (compressorMap.contains(lhs)) {
        emitGlobalModule(src"// val $lhs = $rhs already emitted", forceful)
      } else {
        compressorMap += (lhs -> (stripped, 0))
        emitGlobalModule(src"val $lhs = $rhs", forceful)
      }
    }
  }

  final protected def emitInstrumentation(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("Instrumentation")) {
      emit(x, forceful)
    }
  }

  final protected def emitGlobalModule(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("GlobalModules")) {
      emit(x, forceful)
    }
  }

  final protected def emitGlobalRetiming(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("GlobalRetiming")) {
      emit(x, forceful)
    }
  }

  final protected def openGlobalWire(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("GlobalWires")) {
      open(x, forceful)
    }
  }

  final protected def openInstrumentation(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("Instrumentation")) {
      open(x, forceful)
    }
  }

  final protected def openGlobalModule(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("GlobalModules")) {
      open(x, forceful)
    }
  }

  final protected def openGlobalRetiming(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("GlobalRetiming")) {
      open(x, forceful)
    }
  }

  final protected def closeGlobalWire(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("GlobalWires")) {
      close(x, forceful)
    }
  }

  final protected def closeInstrumentation(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("Instrumentation")) {
      close(x, forceful)
    }
  }

  final protected def closeGlobalModule(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("GlobalModules")) {
      close(x, forceful)
    }
  }

  final protected def closeGlobalRetiming(x: String, forceful: Boolean = false): Unit = {
    withStream(getStream("GlobalRetiming")) {
      close(x, forceful)
    }
  }

  protected def bitWidth(tp: Type[_]): Int = {
    c"$tp" match {
      case "Avalon" => 32
      case _ => throw new NoBitWidthException(tp)
    }
  }

  override def copyDependencies(out: String): Unit = {
    // s"mkdir ${out}${java.io.File.separator}templates" !
    // s"mkdir ${out}${java.io.File.separator}templates".!
    // dependencies.foreach{dep => if (dep.needsCopy) {
    //   log(s"Copying ${dep.input} to $out")
    //   s"cp ${dep.input} ${out}${java.io.File.separator}templates${java.io.File.separator}${dep.outputPath}" !
    // }}
    val resourcesPath = s"chiselgen"

    dependencies ::= DirDep(resourcesPath, "template-level/templates")
    // dependencies ::= DirDep(resourcesPath, "template-level/templates/hardfloat")
    dependencies ::= DirDep(resourcesPath, "template-level/fringeHW")
    dependencies ::= DirDep(resourcesPath, "template-level/fringeZynq")
    dependencies ::= DirDep(resourcesPath, "template-level/fringeASIC")
    dependencies ::= DirDep(resourcesPath, "template-level/fringeDE1SoC")
    dependencies ::= DirDep(resourcesPath, "template-level/fringeVCS")
    dependencies ::= DirDep(resourcesPath, "template-level/fringeXSIM")
    dependencies ::= DirDep(resourcesPath, "template-level/fringeAWS")
    dependencies ::= DirDep(resourcesPath, "template-level/fringeArria10")
    dependencies ::= DirDep(resourcesPath, "template-level/fringeASIC")
    dependencies ::= DirDep(resourcesPath, "app-level/scripts", "../", Some("scripts/"))

    dependencies ::= FileDep(resourcesPath, "app-level/Makefile", "../", Some("Makefile"))
    dependencies ::= FileDep(resourcesPath, "app-level/build.sbt", "../", Some("build.sbt"))
    dependencies ::= FileDep(resourcesPath, "app-level/run.sh", "../", Some("run.sh"))
    dependencies ::= FileDep(resourcesPath, "app-level/Top.scala", outputPath = Some("Top.scala"))

    super.copyDependencies(out)
  }


  def tabbing(stream: String): String = " "*(tabWidth*(streamTab getOrElse (stream, 0)))

  protected def strip_ext(name: String): String = {"\\..*".r.replaceAllIn(name,"")}
  protected def get_ext(name: String): String = {".*\\.".r.replaceAllIn(name,"")}
  protected def get_real_stream(curStream: String): String = {
    if ((curStream contains "IOModule") | (curStream contains "AccelTop")) {
      strip_ext(curStream)
    } else {
      val current_ext = streamExtensions(strip_ext(curStream)).last
      val cur_stream_ext = if (current_ext == 0) {strip_ext(curStream)} else {strip_ext(curStream) + "_" + current_ext}
      val cur_tabbing = streamTab(cur_stream_ext + "." + get_ext(curStream))
      if ((cur_tabbing == 1) & !disableSplit) streamLines(strip_ext(curStream)) += 1
      val global_lines = streamLines(strip_ext(curStream))
      val file_num = global_lines / maxLinesPerFile
      if (global_lines % maxLinesPerFile == 0 & (!streamExtensions(strip_ext(curStream)).contains(file_num))) {
        val next = newStream(strip_ext(curStream) + "_" + file_num)
        val curlist = streamExtensions(strip_ext(curStream))

        // Console.println(s"   Just appended ${file_num} to list for ${strip_ext(curStream)}!")
        streamExtensions += (strip_ext(curStream) -> {curlist :+ file_num})
        val prnt = if (file_num == 1) src"${strip_ext(curStream)}" else src"${strip_ext(curStream)}_${file_num-1}"
        withStream(next) {
          stream.println(src"""package accel
import templates._
import templates.ops._
import types._
import chisel3._
import chisel3.util._

trait ${strip_ext(curStream)}_${file_num} extends ${prnt} {
""")
        val methodized_trait_pattern = "^x[0-9]+".r
        val new_trait_name = src"""${strip_ext(curStream)}_${file_num}"""
        if (config.multifile == 6 & methodized_trait_pattern.findFirstIn(new_trait_name).isDefined) {
          stream.println(src"""def method_${strip_ext(curStream)}_${file_num}() {""")
        }

          streamTab(strip_ext(curStream) + "_" + file_num + "." + get_ext(curStream)) = 1

        }
      }
      cur_stream_ext
    }

  }



  override protected def emit(x: String, forceful: Boolean = false): Unit = {
    if (emitEn | forceful) {

      // val current_ext = streamExtensions(strip_ext(streamName)).last
      // val cur_stream_ext = if (current_ext == 0) {strip_ext(streamName)} else {strip_ext(streamName) + "_" + current_ext}
      // val cur_tabbing = streamTab(cur_stream_ext + "." + get_ext(streamName))
      // val global_lines = streamLines(strip_ext(streamName))
      // val ext_list = streamExtensions(strip_ext(streamName))
      // val file_num = global_lines / maxLinesPerFile
      // val debug_stuff = "// lines " + global_lines + " tabbing " + cur_tabbing + " ext_list " + ext_list + " file_num " + file_num

      val realstream = get_real_stream(streamName)
      withStream(getStream(realstream)) {stream.println(tabbing(realstream + "." + get_ext(streamName)) + x /*+ debug_stuff*/)}
    } else {
      if (config.emitDevel == 2) {Console.println(s"[ ${lang}gen-NOTE ] Emission of ${x} does not belong in this backend")}
    }
  }
  override protected def open(x: String, forceful: Boolean = false): Unit = {
    if (emitEn | forceful) {
      val realstream = get_real_stream(streamName)
      withStream(getStream(realstream)) {stream.println(tabbing(realstream + "." + get_ext(streamName)) + x)};
      if (streamTab contains {realstream + "." + get_ext(streamName)}) streamTab(realstream + "." + get_ext(streamName)) += 1
    } else {
      if (config.emitDevel == 2) {Console.println(s"[ ${lang}gen-NOTE ] Emission of ${x} does not belong in this backend")}
    }
  }
  override protected def close(x: String, forceful: Boolean = false): Unit = {
    if (emitEn | forceful) {
      val realstream = get_real_stream(streamName)
      if (streamTab contains {realstream + "." + get_ext(streamName)}) {
        streamTab(realstream + "." + get_ext(streamName)) -= 1;
        withStream(getStream(realstream)) {stream.println(tabbing(realstream + "." + get_ext(streamName)) + x)}
      }
    } else {
      if (config.emitDevel == 2) {Console.println(s"[ ${lang}gen-NOTE ] Emission of ${x} does not belong in this backend")}
    }
  }
  override protected def closeopen(x: String, forceful: Boolean = false): Unit = { // Good for "} else {" lines
    if (emitEn | forceful) {
      val realstream = get_real_stream(streamName)
      if (streamTab contains {realstream + "." + get_ext(streamName)}) {
        streamTab(realstream + "." + get_ext(streamName)) -= 1;
        withStream(getStream(realstream)) {stream.println(x);}
        streamTab(realstream + "." + get_ext(streamName)) += 1
      }
    } else {
      if (config.emitDevel == 2) {Console.println(s"[ ${lang}gen-NOTE ] Emission of ${x} does not belong in this backend")}
    }
  }


  final protected def withSubStream[A](name: String, parent: String, inner: Boolean = false)(body: => A): A = { // Places body inside its own trait file and includes it at the end
    if (config.multifile == 6) {
      // Console.println(s"substream $name, parent $parent ext ${streamExtensions(parent)}")
      val prnts = if (!(streamExtensions contains parent)) src"$parent" else streamExtensions(parent).map{i => if (i == 0) src"$parent" else src"${parent}_${i}"}.mkString(" with ")
      emit(src"// Creating sub kernel ${name}")
      withStream(newStream(name)) {
          emit("""package accel
import templates._
import templates.ops._
import types._
import chisel3._
import chisel3.util._
""")
          open(src"""trait ${name} extends ${prnts} {
def method_${name}() {""")
          try { body }
          finally {
            streamExtensions(name).foreach{i =>
              val fname = if (i == 0) src"$name" else src"${name}_${i}"
              withStream(getStream(fname)) { stream.println("}}")}
            }
          }
      }
    } else if (config.multifile == 5) {
      // Console.println(s"substream $name, parent $parent ext ${streamExtensions(parent)}")
      val prnts = if (!(streamExtensions contains parent)) src"$parent" else streamExtensions(parent).map{i => if (i == 0) src"$parent" else src"${parent}_${i}"}.mkString(" with ")
      emit(src"// Creating sub kernel ${name}")
      withStream(newStream(name)) {
          emit("""package accel
import templates._
import templates.ops._
import types._
import chisel3._
import chisel3.util._
""")
          open(src"""trait ${name} extends ${prnts} {""")
          try { body }
          finally {
            streamExtensions(name).foreach{i =>
              val fname = if (i == 0) src"$name" else src"${name}_${i}"
              withStream(getStream(fname)) { stream.println("}")}
            }
          }
      }
    } else if (config.multifile == 4) {
      // Console.println(s"substream $name, parent $parent ext ${streamExtensions(parent)}")
      val prnts = if (!(streamExtensions contains parent)) src"$parent" else streamExtensions(parent).map{i => if (i == 0) src"$parent" else src"${parent}_${i}"}.mkString(" with ")
      emit(src"// Creating sub kernel ${name}")
      withStream(newStream(name)) {
          emit("""package accel
import templates._
import templates.ops._
import types._
import chisel3._
import chisel3.util._
""")
          open(src"""trait ${name} extends ${prnts} {""")
          try { body }
          finally {
            streamExtensions(name).foreach{i =>
              val fname = if (i == 0) src"$name" else src"${name}_${i}"
              withStream(getStream(fname)) { stream.println("}")}
            }
          }
      }
    } else if (config.multifile == 3 & inner) {
        withStream(newStream(name)) {
            emit("""package accel
  import templates._
  import templates.ops._
  import types._
  import chisel3._
  import chisel3.util._
  """)
            open(src"""trait ${name} extends RootController {""")
            try { body }
            finally {
              close("}")
            }
        }

    } else if (config.multifile == 2) {
      open(src";{ // Multifile disabled, emitting $name kernel here")
      try { body }
      finally { close("}") }
    } else if (config.multifile == 1 & inner) {
      open(src";{ // Multifile disabled, emitting $name kernel here")
      try { body }
      finally { close("}") }
    } else {
      open(src"// Multifile disabled, emitting $name kernel here without scoping")
      try { body }
      finally { close("") }
    }
  }


}
