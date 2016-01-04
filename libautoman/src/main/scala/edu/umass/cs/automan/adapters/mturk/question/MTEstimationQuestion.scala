package edu.umass.cs.automan.adapters.mturk.question

import java.util.{Date, UUID}
import edu.umass.cs.automan.adapters.mturk.mock.EstimationMockResponse
import edu.umass.cs.automan.core.logging.DebugLog
import edu.umass.cs.automan.core.logging.LogLevelDebug
import edu.umass.cs.automan.core.logging.LogType
import edu.umass.cs.automan.core.question.{EstimationQuestion, FreeTextQuestion}
import java.security.MessageDigest
import org.apache.commons.codec.binary.Hex

class MTEstimationQuestion extends EstimationQuestion with MTurkQuestion {
  override type A = EstimationQuestion#A

  // public API
  def memo_hash: String = {
    val md = MessageDigest.getInstance("md5")
    new String(Hex.encodeHex(md.digest(toXML(randomize = false).toString().getBytes)))
  }
  override def description: String = _description match { case Some(d) => d; case None => this.title }
  override def group_id: String = _group_id match { case Some(g) => g; case None => this.id.toString() }

  // private API
  override def toMockResponse(question_id: UUID, response_time: Date, a: A) : EstimationMockResponse = {
    EstimationMockResponse(question_id, response_time, a)
  }
  def fromXML(x: scala.xml.Node) : A = {
    // There is a SINGLE answer here, like this:
    //    <Answer>
    //      <QuestionIdentifier>721be34c-c867-42ce-8acd-829e64ae62dd</QuestionIdentifier>
    //      <FreeText>2.11</FreeText>
    //    </Answer>
    DebugLog("MTEstimationQuestion: fromXML:\n" + x.toString,LogLevelDebug(),LogType.ADAPTER,id)

    (x \\ "Answer" \ "FreeText").text.toDouble
  }
  def toXML(randomize: Boolean) = {
    <QuestionForm xmlns="http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionForm.xsd">
      <Question>
        <QuestionIdentifier>{ if (randomize) id_string else "" }</QuestionIdentifier>
        <QuestionContent>
          {
          _image_url match {
            case Some(url) => {
              <Binary>
                <MimeType>
                  <Type>image</Type>
                  <SubType>png</SubType>
                </MimeType>
                <DataURL>{ url }</DataURL>
                <AltText>{ image_alt_text }</AltText>
              </Binary>
            }
            case None => {}
          }
          }
          {
          // if formatted content is specified, use that instead of text field
          _formatted_content match {
            case Some(x) => <FormattedContent>{ scala.xml.PCData(x.toString()) }</FormattedContent>
            case None => <Text>{ text }</Text>
          }
          }
        </QuestionContent>
        <AnswerSpecification>
          <FreeTextAnswer>
            <Constraints>
              { isNumeric }
            </Constraints>
          </FreeTextAnswer>
        </AnswerSpecification>
      </Question>
    </QuestionForm>
  }

  private def isNumeric : scala.xml.Node = {
    (_min_value, _max_value) match {
      case (Some(min),Some(max)) => <IsNumeric minValue={ "\"" + Math.floor(min).toInt + "\"" } maxValue={ "\"" + Math.ceil(max).toInt + "\"" } />
      case (Some(min),None) => <IsNumeric minValue={ "\"" + Math.floor(min).toInt + "\"" } />
      case (None,Some(max)) => <IsNumeric maxValue={ "\"" + Math.ceil(max).toInt + "\"" } />
      case (None,None) => <IsNumeric />
    }
  }
}