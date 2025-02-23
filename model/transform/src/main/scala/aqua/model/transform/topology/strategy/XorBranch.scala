package aqua.model.transform.topology.strategy

import aqua.model.transform.topology.Topology
import aqua.model.{OnModel, ParGroupModel, SeqGroupModel, ValueModel, XorModel}

import cats.Eval
import cats.data.Chain
import cats.syntax.functor.*
import cats.instances.lazyList.*
import cats.syntax.option.*

// Parent == Xor
object XorBranch extends Before with After {
  override def toString: String = Console.RED + "<xor>/*" + Console.RESET

  override def beforeOn(current: Topology): Eval[List[OnModel]] =
    current.prevSibling.map(_.endsOn) getOrElse super.beforeOn(current)

  // Find closest par exit up and return its branch current is in
  // Returns none if there is no par up
  //                 or current is not at its exit
  private def closestParExitChild(current: Topology): Option[Topology] =
    current.parents
      .fproduct(_.parent.map(_.cursor.op))
      .dropWhile {
        case (t, Some(_: SeqGroupModel)) =>
          t.nextSibling.isEmpty
        case (_, Some(XorModel)) =>
          true
        case _ => false
      }
      .headOption
      .collect { case (t, Some(_: ParGroupModel)) => t }

  private def closestParExit(current: Topology): Option[Topology] =
    closestParExitChild(current).flatMap(_.parent)

  override def forceExit(current: Topology): Eval[Boolean] =
    closestParExitChild(current).fold(
      Eval.later(current.cursor.moveUp.exists(_.hasExecLater))
    )(_.forceExit) // Force exit if par branch needs it

  override def afterOn(current: Topology): Eval[List[OnModel]] =
    current.forceExit.flatMap {
      case true =>
        closestParExit(current).fold(afterParent(current))(_.afterOn)
      case false => super.afterOn(current)
    }

  // Parent of this branch's parent xor – fixes the case when this xor is in par
  override def pathAfter(current: Topology): Eval[Chain[ValueModel]] =
    closestParExit(current).fold(super.pathAfter(current))(_ =>
      // Ping next if we are exiting from par
      super.pathAfterAndPingNext(current)
    )
}
