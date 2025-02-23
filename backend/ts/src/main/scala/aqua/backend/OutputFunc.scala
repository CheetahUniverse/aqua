package aqua.backend

import aqua.backend.air.FuncAirGen
import aqua.backend.ts.TypeScriptCommon.fixupArgName
import aqua.backend.ts.{TSFuncTypes, TypeScriptCommon}
import aqua.res.FuncRes
import aqua.types.*
import cats.syntax.show.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*

case class OutputFunc(func: FuncRes, types: Types) {

  import FuncRes.*
  import TypeScriptCommon.*
  import func.*
  import types.*

  val funcTypes = types.funcType(func)

  import funcTypes.*
  import aqua.definitions.TypeDefinition.*
  import aqua.definitions.*

  def generate: (AirFunction, String) = {
    val tsAir = FuncAirGen(func).generate
    val codeLeftSpace = " " * 20

    val script = tsAir.show.linesIterator.map(codeLeftSpace + _).mkString("\n")
    val funcDef = FunctionDef(func)

    val scriptConstName = func.funcName + "_script"

    (
      AirFunction(func.funcName, script, funcDef),
      s"""export const $scriptConstName = `
         |$script
         |    `
         |${funcTypes.generate}
         |export function ${func.funcName}(${typed("...args", "any")}) {
         |

         |    return callFunction$$$$(
         |        args,
         |        ${funcDef.asJson.deepDropNullValues.spaces4},
         |        $scriptConstName
         |    )
         |}""".stripMargin
    )
  }

}
