package edu.umass.cs.automan.core.validation

import edu.umass.cs.automan.core.question.PictureClause
import org.scalatest._

class PictureClauseTest extends FlatSpec with Matchers {
  "A picture clause" should "compile to an equivalent regular expression" in {
    val pc = "XXXXXYYY"
    val (regex,count) = PictureClause(pc, allow_empty = false)
    val regex_gt = "^[a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9]?[a-zA-Z0-9]?[a-zA-Z0-9]?$"
    regex should be (regex_gt)
  }

  "A picture clause with optional characters" should "compile to an equivalent regular expression" in {
    val pc = "XXXXXYYY"
    val (regex,count) = PictureClause(pc, allow_empty = true)
    val regex_gt = "(^[a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9]?[a-zA-Z0-9]?[a-zA-Z0-9]?$)|(^(N|n)(A|a)$)"
    regex should be (regex_gt)
  }

  "The picture clause XXXXXYYY" should "accept PNP4411" in {
    val pc = "XXXXXYYY"
    val (regex,count) = PictureClause(pc, allow_empty = true)
    regex.r.findFirstIn("PNP4411").nonEmpty should be (true)
  }

  "The picture clause XXXXXYYY" should "not accept PNP-4411" in {
    val pc = "XXXXXYYY"
    val (regex,count) = PictureClause(pc, allow_empty = true)
    regex.r.findFirstIn("PNP-4411").nonEmpty should be (false)
  }
}
