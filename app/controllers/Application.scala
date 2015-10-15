package controllers

import play.api._
import play.api.mvc._

import org.fusesource.stomp.jms._
import javax.jms._

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.concurrent.Await
import scala.concurrent.TimeoutException
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.JavaConverters._

import collection.mutable.{ HashMap, MultiMap, Set, ListBuffer }

import org.fusesource.stomp.client.Constants._
import org.fusesource.stomp.codec.StompFrame
import org.fusesource.stomp.client.Stomp

class Application extends Controller {

  // Simple data store (TODO: persist to simple csv flat file, leveldb, h2, accumulo, https://github.com/jeluard/stone, https://github.com/rrd4j/rrd4j?)
  var head = List[Tuple6[String, String, Long, String, String, Double]]()

  // in future, make for each message-collector a extra list.
  // merge the list.views for the analysis view to get a global view.
  // make the view a parallel collection with .par for better performance.
  // how far can we go with such a simple concept?
  def analysisView = head.view.par

  var ordererAgentSet = Set[String]()

  // Connect to the broker with a raw connection
  val stomp = new Stomp("ashburner", 61613)
  val connection = stomp.connectBlocking

  // Listen on the billing topic
  val frame = new StompFrame(SUBSCRIBE)
  frame.addHeader(DESTINATION, StompFrame.encodeHeader("/topic/billing"))
  frame.addHeader(ID, connection.nextId)
  val response = connection.request(frame)

  // fetch all messages
  Future {
    while (true) {
      val frame = connection.receive
      if (frame.action == MESSAGE) {
        val headerList = frame.headerList.asScala
        val header = headerList.map( x =>
          x.getKey.toString -> x.getValue.toString
        ).toMap

        val (digit, unitName) = header("unit").split(" ") match {
          case Array(digit) => (digit.toDouble, "undefined")
          case Array(digit, unitName) => (digit.toDouble, unitName)
          case _ => (0.0, "undefined")
        }

        val (orderNr, ordererAgent) = header("tracking-nr").split("@") match {
          case Array(orderNr, ordererAgent) => (orderNr, ordererAgent)
          case _ => ("undefiend", "undefined")
        }

        val tuple = (
          ordererAgent, orderNr,
          header("timestamp").toLong, header("event"),
          header("agent"), 0.01 * digit
        )

        ordererAgentSet = Set(ordererAgent) ++ ordererAgentSet

        head = tuple :: head  // This is O(1)
        println(tuple)
      }
    }
  }

  def index = Action {
    val stats = ordererAgentSet.map( userKey =>
      userKey -> analysisView.filter(_._1 == userKey).map(_._6).sum
    ).toMap

    Ok(views.html.main("Accounting")(head.size, stats))
  }

}
