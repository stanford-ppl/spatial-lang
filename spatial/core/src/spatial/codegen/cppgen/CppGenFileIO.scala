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
    	emit(src"""std::${dir}fstream ${lhs}_file ("$filename");""")
    case CloseFile(file) =>
    	emit(src"${lhs}_file.close();")
    case ReadTokens(file, delim) =>
    	emit(src"std::vector<${lhs.tp}> ${lhs} = new std::vector<${lhs.tp}>; ")
    	open(src"if (${file}_file.is_open() {")
    		open(src"while ( ${lhs}_file.good() ) {")
	    		emit(src"string ${lhs}_str;")
  	  		emit(src"getline (${file}_file, ${lhs}_str, '${delim}');")
  	  		src"${lhs.tp}" match {
  	  			case "int32_t" => emit(src"${lhs.tp} ${lhs}_next = stoi(${lhs}_string);")
  	  			case "double" => emit(src"${lhs.tp} ${lhs}_next = stof(${lhs}_string);")
  	  			case "float" => emit(src"${lhs.tp} ${lhs}_next = stof(${lhs}_string);")
  	  			case _ => emit(src"${lhs.tp} ${lhs}_next = stoi(${lhs}_string);")
  	  		}
  	  		emit(src"${lhs}->push_back(stof(${lhs}_next));")
  	  	close(src"}")
  	  close(src"}")
    case WriteTokens(file, delim, len, token, i) =>
    	emit(" // TODO: MOST LIKELY WRONG, not sure how to use $i")
    	open(src"for (int ${lhs}_id = 0; ${lhs}_id < $len; ${lhs}_id++) {")
    		open(src"if (${file}_file.is_open()) {")
    			emit(src"${file}_file << ${token};")
    			emit(src"${file}_file << ${delim};")
    		close("}")
    	close("}")

    case _ => super.emitNode(lhs, rhs)
  }



}
