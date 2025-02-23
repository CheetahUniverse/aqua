package aqua.model.inline

import aqua.model.{EmptyModel, OpModel, ParModel, SeqModel}
import aqua.raw.ops.RawTag
import aqua.raw.value.ValueRaw
import cats.Monoid
import cats.data.Chain

import scala.collection.immutable.ListMap

sealed trait MergeMode
object SeqMode extends MergeMode
object ParMode extends MergeMode

/**
 * @param flattenValues values that need to be resolved before `predo`.
 *                      ListMap for keeping order of values (mostly for debugging purposes)
 * @param predo operations tree
 * @param mergeMode how `flattenValues` and `predo` must be merged
 */
private[inline] case class Inline(
  flattenValues: ListMap[String, ValueRaw] = ListMap.empty,
  predo: Chain[OpModel.Tree] = Chain.empty,
  mergeMode: MergeMode = ParMode
) {

  def desugar: Inline = {
    val desugaredPredo =
      predo.toList match {
        case Nil => Chain.empty
        case x :: Nil =>
          Chain.one(x)
        case l =>
          mergeMode match
            case SeqMode =>
              val wrapped = SeqModel.wrap(l: _*)
              wrapped match
                case EmptyModel.leaf => Chain.empty
                case _ => Chain.one(wrapped)
            case ParMode => Chain.one(ParModel.wrap(l: _*))
      }

    Inline(
      flattenValues,
      desugaredPredo
    )
  }

  def mergeWith(inline: Inline, mode: MergeMode): Inline = {
    val left = desugar
    val right = inline.desugar

    Inline(left.flattenValues ++ right.flattenValues, left.predo ++ right.predo, mode)
  }
}

// TODO may not be needed there
private[inline] object Inline {
  val empty: Inline = Inline()

  def preload(pairs: (String, ValueRaw)*): Inline = Inline(ListMap.from(pairs))

  def tree(tr: OpModel.Tree): Inline = Inline(predo = Chain.one(tr))

  given Monoid[Inline] with
    override val empty: Inline = Inline()

    override def combine(a: Inline, b: Inline): Inline =
      Inline(a.flattenValues ++ b.flattenValues, a.predo ++ b.predo)

  def parDesugarPrefix(ops: List[OpModel.Tree]): Option[OpModel.Tree] = ops match {
    case Nil => None
    case x :: Nil => Option(x)
    case _ => Option(ParModel.wrap(ops: _*))
  }

  def parDesugarPrefixOpt(ops: Option[OpModel.Tree]*): Option[OpModel.Tree] =
    parDesugarPrefix(ops.toList.flatten)
}
