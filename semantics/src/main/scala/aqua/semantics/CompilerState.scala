package aqua.semantics

import aqua.parser.lexer.Token
import aqua.raw.Raw
import aqua.raw.RawContext
import aqua.semantics.rules.abilities.AbilitiesState
import aqua.semantics.rules.definitions.DefinitionsState
import aqua.semantics.rules.locations.LocationsState
import aqua.semantics.rules.names.NamesState
import aqua.semantics.rules.types.TypesState
import cats.Semigroup
import cats.data.{Chain, State}
import cats.kernel.Monoid
import cats.syntax.monoid.*

case class CompilerState[S[_]](
  errors: Chain[SemanticError[S]] = Chain.empty[SemanticError[S]],
  names: NamesState[S] = NamesState[S](),
  abilities: AbilitiesState[S] = AbilitiesState[S](),
  types: TypesState[S] = TypesState[S](),
  definitions: DefinitionsState[S] = DefinitionsState[S](),
  locations: LocationsState[S] = LocationsState[S]()
)

object CompilerState {
  type St[S[_]] = State[CompilerState[S], Raw]

  def init[F[_]](ctx: RawContext): CompilerState[F] =
    CompilerState(
      names = NamesState.init[F](ctx),
      abilities = AbilitiesState.init[F](ctx),
      types = TypesState.init[F](ctx)
    )

  implicit def compilerStateMonoid[S[_]]: Monoid[St[S]] = new Monoid[St[S]] {
    override def empty: St[S] = State.pure(Raw.Empty("compiler state monoid empty"))

    override def combine(x: St[S], y: St[S]): St[S] = for {
      a <- x.get
      b <- y.get
      _ <- State.set(
        CompilerState[S](
          a.errors ++ b.errors,
          a.names |+| b.names,
          a.abilities |+| b.abilities,
          a.types |+| b.types,
          locations = a.locations |+| b.locations
        )
      )
      am <- x
      ym <- y
    } yield {
      // println(s"MONOID COMBINE $am $ym")
      am |+| ym
    }
  }

}
