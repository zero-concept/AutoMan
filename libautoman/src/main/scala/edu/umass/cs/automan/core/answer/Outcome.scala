package edu.umass.cs.automan.core.answer

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

sealed abstract class Outcome[T](protected[automan] val f: Future[AbstractAnswer[T]]) {
  def answer: AbstractAnswer[T] = {
    Await.result(f, Duration.Inf)
  }
}

case class EstimationOutcome(override protected[automan] val f: Future[AbstractEstimate]) extends Outcome[Double](f)

case class ScalarOutcome[T](override protected[automan] val f: Future[AbstractScalarAnswer[T]]) extends Outcome[T](f)

case class DistributionOutcome[T](override protected[automan] val f: Future[AbstractVectorAnswer[T]]) extends Outcome[T](f)