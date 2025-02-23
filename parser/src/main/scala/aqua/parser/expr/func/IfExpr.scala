package aqua.parser.expr.func

import aqua.parser.Expr
import aqua.parser.expr.func.{ForExpr, IfExpr}
import aqua.parser.lexer.Token.*
import aqua.parser.lexer.{EqOp, LiteralToken, ValueToken}
import aqua.parser.lift.LiftParser
import aqua.types.LiteralType
import cats.parse.Parser as P
import cats.{~>, Comonad}
import aqua.parser.lift.Span
import aqua.parser.lift.Span.{P0ToSpan, PToSpan}

case class IfExpr[F[_]](left: ValueToken[F], eqOp: EqOp[F], right: ValueToken[F])
    extends Expr[F](IfExpr, eqOp) {

  override def mapK[K[_]: Comonad](fk: F ~> K): IfExpr[K] =
    copy(left.mapK(fk), eqOp.mapK(fk), right.mapK(fk))
}

object IfExpr extends Expr.AndIndented {

  // list of expressions that can be used inside this block
  override def validChildren: List[Expr.Lexem] = ForExpr.validChildren

  override val p: P[IfExpr[Span.S]] =
    (`if` *> ` ` *> ValueToken.`value` ~ (` ` *> EqOp.p ~ (` ` *> ValueToken.`value`)).?).map {
      case (left, Some((e, right))) =>
        IfExpr(left, e, right)
      case (left, None) =>
        IfExpr(left, EqOp(left.as(true)), LiteralToken(left.as("true"), LiteralType.bool))
    }
}
