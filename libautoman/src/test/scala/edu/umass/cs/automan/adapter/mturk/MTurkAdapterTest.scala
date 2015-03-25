package edu.umass.cs.automan.adapter.mturk

import edu.umass.cs.automan.adapters.mturk.MTurkAdapter
import edu.umass.cs.automan.automan
import edu.umass.cs.automan.core.answer.{ScalarOverBudget, ScalarAnswer}
import org.scalatest._

class MTurkAdapterTest extends FlatSpec with Matchers {
  "A simple program" should "work" in {
    val a = MTurkAdapter { mt =>
      mt.mock_answers = ???
    }

    automan(a) {
      def which_one() = a.RadioButtonQuestion { q =>
        q.budget = 8.00
        q.text = "Which one of these does not belong?"
        q.options = List(
          a.Option('oscar, "Oscar the Grouch"),
          a.Option('kermit, "Kermit the Frog"),
          a.Option('spongebob, "Spongebob Squarepants"),
          a.Option('cookie, "Cookie Monster"),
          a.Option('count, "The Count")
        )
      }

      which_one().answer match {
        case ScalarAnswer(value, _, _) =>
          println("The answer is: " + value)
        case ScalarOverBudget(value, cost, conf) =>
          println(
            "You ran out of money. The best answer is \"" +
              value + "\" with a confidence of " + conf
          )
      }
    }
  }
}