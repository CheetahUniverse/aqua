package aqua.compiler

import aqua.parser.ParserError
import aqua.parser.lexer.Token
import aqua.semantics.SemanticError
import cats.data.NonEmptyChain

trait AquaError[I, E, S[_]]
case class SourcesErr[I, E, S[_]](err: E) extends AquaError[I, E, S]
case class ParserErr[I, E, S[_]](err: ParserError[S]) extends AquaError[I, E, S]

case class ResolveImportsErr[I, E, S[_]](fromFile: I, token: Token[S], err: E)
    extends AquaError[I, E, S]
case class ImportErr[I, E, S[_]](token: Token[S]) extends AquaError[I, E, S]

case class CycleError[I, E, S[_]](modules: NonEmptyChain[I]) extends AquaError[I, E, S]

case class CompileError[I, E, S[_]](err: SemanticError[S]) extends AquaError[I, E, S]
case class OutputError[I, E, S[_]](compiled: AquaCompiled[I], err: E) extends AquaError[I, E, S]
case class AirValidationError[I, E, S[_]](errors: NonEmptyChain[String]) extends AquaError[I, E, S]
