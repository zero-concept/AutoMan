package edu.umass.cs.automan.adapter.mturk

import java.util.UUID
import edu.umass.cs.automan.adapters.mturk.MTurkAdapter
import edu.umass.cs.automan.adapters.mturk.mock.MockSetup
import edu.umass.cs.automan.automan
import edu.umass.cs.automan.core.answer._
import edu.umass.cs.automan.core.logging.{TestUtil, LogConfig}
import org.scalatest._

class MTurkRadioDistribTest extends FlatSpec with Matchers {

  "A radio button distribution program" should "work" in {
    val a = MTurkAdapter { mt =>
      mt.access_key_id = UUID.randomUUID().toString
      mt.secret_access_key = UUID.randomUUID().toString
      mt.use_mock = MockSetup(budget = 8.00)
      mt.logging = LogConfig.NO_LOGGING
      mt.poll_interval = 2
    }

    val sample_size = 30

    val mock_answers = TestUtil.genAnswers(
      Array('oscar, 'kermit, 'spongebob, 'cookie, 'count),
      Array("0.02", "0.14", "0.78", "0.05", "0.01"),
      sample_size
    ).toList

    automan(a) {
      def which_one() = a.RadioButtonDistributionQuestion { q =>
        q.sample_size = sample_size
        q.budget = 8.00
        q.text = "Which one of these does not belong?"
        q.options = List(
          a.Option('oscar, "Oscar the Grouch"),
          a.Option('kermit, "Kermit the Frog"),
          a.Option('spongebob, "Spongebob Squarepants"),
          a.Option('cookie, "Cookie Monster"),
          a.Option('count, "The Count")
        )
        q.mock_answers = mock_answers.toList
      }

      which_one().answer match {
        case DistributionAnswer(values, _) =>
          TestUtil.compareDistributions(mock_answers, values) should be (true)
        case DistributionOverBudget(_, _) =>
          fail()
      }
    }
  }
}