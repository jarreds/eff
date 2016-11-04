package org.atnos.eff
package scalaz

import cats.Eval
import cats.implicits._
import org.atnos.eff.all._
import org.atnos.eff.syntax.all._
import org.atnos.eff.syntax.scalaz._

import org.specs2._
import org.specs2.concurrent.ExecutionEnv

import scala.collection.mutable.ListBuffer
import scala.concurrent._
import org.scalacheck._
import org.specs2.matcher.TaskMatchers._

class AsyncTaskSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck { def is = s2"""

 Async effects can be implemented with an AsyncTask service $e1
 Async effects can be attempted                             $e2
 Async effects can be executed concurrently                 $e3

"""

  type S = Fx.fx2[Async, Option]

  lazy val asyncService = AsyncTaskService.create
  import asyncService._

  def e1 = {
    def action[R :_async :_option]: Eff[R, Int] = for {
      a <- asyncFork(10)
      b <- asyncFork(20)
    } yield a + b

    action[S].runOption.runAsyncTask must returnValue(beSome(30))
  }

  def e2 = {
    def action[R :_async :_option]: Eff[R, Int] = for {
      a <- asyncFork(10)
      b <- asyncFork { boom; 20 }
    } yield a + b

    action[S].asyncAttempt.runOption.runAsyncTask must returnValue(beSome(beLeft(boomException)))
  }

  def e3 = prop { ls: List[Int] =>
    val messages: ListBuffer[Int] = new ListBuffer[Int]

    def action[R :_async](i: Int): Eff[R, Int] =
      asyncFork {
        Thread.sleep(i.toLong)
        messages.append(i)
        i
      }

    val actions = Eff.traverseA(ls)(i => action[S](i))
    actions.runOption.runAsyncTask.unsafePerformSync

    "the messages must not be received in the same order" ==> {
      (messages.toList.sorted !=== ls).unless(isSorted(ls))
    }

  }.setGen(Gen.listOfN(5, Gen.oneOf(10, 200, 300, 500))).set(minTestsOk = 10)

  def timeout: Unit = throw new TimeoutException
  def boom: Unit = throw boomException
  val boomException: Throwable = new Exception("boom")

  def isSorted[T : Numeric](ls: List[T]): Boolean =
    ls.sorted == ls
}

