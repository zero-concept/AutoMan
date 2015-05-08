package edu.umass.cs.automan.core.strategy

import edu.umass.cs.automan.core.answer._
import edu.umass.cs.automan.core.question._
import edu.umass.cs.automan.core.scheduler._

abstract class DistributionValidationStrategy(question: DistributionQuestion)
  extends ValidationStrategy(question) {

  def is_done(thunks: List[Thunk]) = {
    val done = completed_workerunique_thunks(thunks).size
    done >= question.sample_size
  }
  def rejection_response(thunks: List[Thunk]): String = {
    "We can only accept a single answer per worker."
  }
  def select_answer(thunks: List[Thunk]) : Question#AA = {
    val valid_thunks: List[Thunk] = completed_workerunique_thunks(thunks)
    val distribution: Set[Question#A] = valid_thunks.map { t => t.answer.get }.toSet
    val cost: BigDecimal = valid_thunks.map { t => t.cost }.foldLeft(BigDecimal(0)){ (acc, c ) => acc + c }
    DistributionAnswer(distribution, cost).asInstanceOf[Question#AA]
  }
  def select_over_budget_answer(thunks: List[Thunk]) : Question#AA = {
    val valid_thunks: List[Thunk] = completed_workerunique_thunks(thunks)
    val distribution: Set[Question#A] = valid_thunks.map { t => t.answer.get }.toSet
    val cost: BigDecimal = valid_thunks.map { t => t.cost }.foldLeft(BigDecimal(0)){ (acc, c ) => acc + c }
    DistributionOverBudget(distribution, cost).asInstanceOf[Question#AA]
  }
  override def thunks_to_accept(thunks: List[Thunk]): List[Thunk] = {
    completed_workerunique_thunks(thunks)
  }
  override def thunks_to_reject(thunks: List[Thunk]): List[Thunk] = {
    val accepts = thunks_to_accept(thunks).toSet
    thunks.filter { t => !accepts.contains(t) }
  }
}