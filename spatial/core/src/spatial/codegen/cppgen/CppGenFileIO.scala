package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.FileIOExp
import spatial.SpatialConfig


trait CppGenFileIO extends CppCodegen  {
  val IR: FileIOExp
  import IR._



  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case OpenFile(filename, isWr) => 
    	val dir = if (isWr) "o" else "i"
    	emit(src"""std::${dir}fstream ${lhs}_file ($filename);""")
    case CloseFile(file) =>
    	emit(src"${file}_file.close();")
    case ReadTokens(file, delim) =>
    	emit(src"std::vector<string>* ${lhs} = new std::vector<string>; ")
    	open(src"if (${file}_file.is_open()) {")
    		open(src"while ( ${file}_file.good() ) {")
	    		emit(src"string ${lhs}_str;")
	    		val chardelim = src"$delim".replace("\"","'")
  	  		emit(src"""getline (${file}_file, ${lhs}_str, ${chardelim});""")
  	  		emit(src"${lhs}->push_back(${lhs}_str);")
  	  	close(src"}")
  	  close(src"}")
    case WriteTokens(file, delim, len, token, i) =>
    	emit(" // TODO: MOST LIKELY WRONG, not sure how to use $i")
    	open(src"for (int ${lhs}_id = 0; ${lhs}_id < $len; ${lhs}_id++) {")
    		open(src"if (${file}_file.is_open()) {")
    			emit(src"${file}_file << ${token};")
	    		val chardelim = src"$delim".replace("\"","'")
    			emit(src"""${file}_file << ${chardelim};""")
    		close("}")
    	close("}")

    case _ => super.emitNode(lhs, rhs)
  }



}
