package aqua.model.transform

import aqua.model.transform.ModelBuilder
import aqua.model.transform.{Transform, TransformConfig}
import aqua.model.{CallModel, FuncArrow, LiteralModel, VarModel}
import aqua.raw.ops.{Call, CallArrowRawTag, FuncOp, OnTag, RawTag, SeqTag}
import aqua.raw.value.{LiteralRaw, VarRaw}
import aqua.types.{ArrowType, NilType, ProductType, ScalarType}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import aqua.raw.value.{LiteralRaw, ValueRaw, VarRaw}
import aqua.res.{CallRes, CallServiceRes, MakeRes, SeqRes, XorRes}
import cats.data.Chain

class TransformSpec extends AnyFlatSpec with Matchers {

  import ModelBuilder.*

  val stringArrow: ArrowType = ArrowType(NilType, ProductType(ScalarType.string :: Nil))

  def callOp(i: Int, exportTo: List[Call.Export] = Nil, args: List[ValueRaw] = Nil): RawTag =
    CallArrowRawTag.service(
      VarRaw(s"srv$i", ScalarType.string),
      s"fn$i",
      Call(args, exportTo)
    )

  "transform.forClient" should "work well with function 1 (no calls before on), generate correct error handling" in {

    val ret = LiteralRaw.quote("return this")

    val func: FuncArrow =
      FuncArrow(
        "ret",
        OnTag(otherPeer, Chain.fromSeq(otherRelay :: Nil)).wrap(callOp(1).leaf),
        stringArrow,
        ret :: Nil,
        Map.empty,
        Map.empty,
        None
      )

    val bc = TransformConfig()

    val fc = Transform.funcRes(func, bc)

    val procFC = fc.value.body

    val expectedFC =
      XorRes.wrap(
        SeqRes.wrap(
          dataCall(bc, "-relay-", initPeer),
          through(relayV),
          through(otherRelay),
          XorRes.wrap(
            SeqRes.wrap(
              callRes(1, otherPeer),
              through(otherRelay),
              through(relayV)
            ),
            SeqRes.wrap(
              through(otherRelay),
              through(relayV),
              errorCall(bc, 1, initPeer)
            )
          ),
          XorRes.wrap(
            respCall(bc, ret, initPeer),
            errorCall(bc, 2, initPeer)
          )
        ),
        errorCall(bc, 3, initPeer)
      )

    procFC.equalsOrShowDiff(expectedFC) should be(true)

  }

  "transform.forClient" should "work well with function 2 (with a call before on)" in {

    val ret = LiteralRaw.quote("return this")

    val func: FuncArrow = FuncArrow(
      "ret",
      SeqTag.wrap(callOp(0).leaf, OnTag(otherPeer, Chain.empty).wrap(callOp(1).leaf)),
      stringArrow,
      ret :: Nil,
      Map.empty,
      Map.empty,
      None
    )

    val bc = TransformConfig(wrapWithXor = false)

    val fc = Transform.funcRes(func, bc)

    val procFC = fc.value.body

    val expectedFC =
      SeqRes.wrap(
        dataCall(bc, "-relay-", initPeer),
        callRes(0, initPeer),
        through(relayV),
        callRes(1, otherPeer),
        through(relayV),
        respCall(bc, ret, initPeer)
      )

    procFC.equalsOrShowDiff(expectedFC) should be(true)

  }

  "transform.forClient" should "link funcs correctly" in {
    /*
    func one() -> u64:
      variable <- Demo.get42()
      <- variable

    func two() -> u64:
      variable <- one()
      <- variable
     */

    val f1: FuncArrow =
      FuncArrow(
        "f1",
        CallArrowRawTag
          .service(
            LiteralRaw.quote("srv1"),
            "foo",
            Call(Nil, Call.Export("v", ScalarType.string) :: Nil)
          )
          .leaf,
        stringArrow,
        VarRaw("v", ScalarType.string) :: Nil,
        Map.empty,
        Map.empty,
        None
      )

    val f2: FuncArrow =
      FuncArrow(
        "f2",
        CallArrowRawTag
          .func("callable", Call(Nil, Call.Export("v", ScalarType.string) :: Nil))
          .leaf,
        stringArrow,
        VarRaw("v", ScalarType.string) :: Nil,
        Map("callable" -> f1),
        Map.empty,
        None
      )

    val bc = TransformConfig(wrapWithXor = false)

    val res = Transform.funcRes(f2, bc).value.body

    res.equalsOrShowDiff(
      SeqRes.wrap(
        dataCall(bc, "-relay-", initPeer),
        CallServiceRes(
          LiteralRaw.quote("srv1"),
          "foo",
          CallRes(Nil, Some(CallModel.Export("v", ScalarType.string))),
          initPeer
        ).leaf,
        respCall(bc, VarRaw("v", ScalarType.string), initPeer)
      )
    ) should be(true)
  }

}
