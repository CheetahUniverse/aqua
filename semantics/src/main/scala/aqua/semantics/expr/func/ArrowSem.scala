package aqua.semantics.expr.func

import aqua.parser.expr.FuncExpr
import aqua.parser.expr.func.ArrowExpr
import aqua.parser.lexer.{Arg, DataTypeToken}
import aqua.raw.Raw
import aqua.raw.arrow.ArrowRaw
import aqua.raw.ops.*
import aqua.raw.value.VarRaw
import aqua.semantics.Prog
import aqua.semantics.rules.ValuesAlgebra
import aqua.semantics.rules.abilities.AbilitiesAlgebra
import aqua.semantics.rules.names.NamesAlgebra
import aqua.semantics.rules.types.TypesAlgebra
import aqua.types.{ArrayType, ArrowType, ProductType, StreamType, Type, CanonStreamType}
import cats.data.{Chain, NonEmptyList}
import cats.free.{Cofree, Free}
import cats.syntax.applicative.*
import cats.syntax.apply.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.traverse.*
import cats.{Applicative, Monad}

class ArrowSem[S[_]](val expr: ArrowExpr[S]) extends AnyVal {

  import expr.arrowTypeExpr

  def before[Alg[_]: Monad](implicit
    T: TypesAlgebra[S, Alg],
    N: NamesAlgebra[S, Alg],
    A: AbilitiesAlgebra[S, Alg]
  ): Alg[ArrowType] =
    // Begin scope -- for mangling
    A.beginScope(arrowTypeExpr) *> N.beginScope(arrowTypeExpr) *> T
      .beginArrowScope(
        arrowTypeExpr
      )
      .flatMap((arrowType: ArrowType) =>
        // Create local variables
        expr.arrowTypeExpr.args
          .flatMap(_._1)
          .zip(
            arrowType.domain.toList
          )
          .traverse {
            case (argName, t: ArrowType) =>
              N.defineArrow(argName, t, isRoot = false)
            case (argName, t) =>
              N.define(argName, t)
          }
          .as(arrowType)
      )

  def after[Alg[_]: Monad](funcArrow: ArrowType, bodyGen: Raw)(implicit
    T: TypesAlgebra[S, Alg],
    N: NamesAlgebra[S, Alg],
    A: AbilitiesAlgebra[S, Alg]
  ): Alg[Raw] =
    A.endScope() *> (
      N.streamsDefinedWithinScope(),
      T.endArrowScope(expr.arrowTypeExpr)
        .flatMap(retValues => N.getDerivedFrom(retValues.map(_.varNames)).map(retValues -> _))
    ).mapN { case (streams, (retValues, retValuesDerivedFrom)) =>
      bodyGen match {
        case FuncOp(m) =>
          // TODO: wrap with local on...via...

          // These streams are returned as streams
          val retStreams: Map[String, Option[Type]] =
            (retValues zip funcArrow.codomain.toList).collect {
              case (VarRaw(n, StreamType(_)), StreamType(_)) => n -> None
              case (VarRaw(n, StreamType(_)), t) => n -> Some(t)
            }.toMap

          val escapingStreams = retStreams.collect { case (n, None) =>
            n
          }

          // Remove stream arguments, and values returned as streams
          val localStreams = streams -- funcArrow.domain.labelledData.map(_._1) -- escapingStreams

          val derivedFromNames =
            retValuesDerivedFrom.reduceLeftOption(_ ++ _).getOrElse(Set.empty[String])

          // Restrict all the local streams
          val (body, retValuesFix) = localStreams.foldLeft((m, retValues)) {
            case ((b, rs), (n, st)) =>
              if (derivedFromNames(n))
                // TODO: what if this stream will be unused. It will be redundant
                RestrictionTag(n, isStream = true).wrap(
                  SeqTag.wrap(
                    b :: CanonicalizeTag(
                      VarRaw(n, st),
                      Call.Export(s"$n-fix", CanonStreamType(st.element))
                    ).leaf :: Nil: _*
                  )
                ) -> rs.map { vn =>
                  vn.shadow(n, VarRaw(s"$n-fix", CanonStreamType(st.element)))
                }
              else RestrictionTag(n, isStream = true).wrap(b) -> rs
          }

          ArrowRaw(funcArrow, retValuesFix, body)
        case m =>
          m
      }
    } <* N.endScope()

  def program[Alg[_]: Monad](implicit
    T: TypesAlgebra[S, Alg],
    N: NamesAlgebra[S, Alg],
    A: AbilitiesAlgebra[S, Alg]
  ): Prog[Alg, Raw] =
    Prog.around(
      before[Alg],
      after[Alg]
    )

}
