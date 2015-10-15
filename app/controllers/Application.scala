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

import com.github.nitram509.jmacaroons.MacaroonsBuilder
import com.github.nitram509.jmacaroons.MacaroonsVerifier
import com.github.nitram509.jmacaroons.verifier.TimestampCaveatVerifier

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
  val brokerURI = Play.current.configuration.getString("play.server.broker").get
  val brokerAdr = brokerURI.split("//")(1).split(":")
  val stomp = new Stomp(brokerAdr(0), brokerAdr(1).toInt)
  val connection = stomp.connectBlocking

  // Listen on the billing topic
  val topic = Play.current.configuration.getString("play.server.topic").get
  val frame = new StompFrame(SUBSCRIBE)
  frame.addHeader(DESTINATION, StompFrame.encodeHeader(topic))
  frame.addHeader(ID, connection.nextId)
  val response = connection.request(frame)

  // Shared Secret for Macaroons
  val sharedSecret = Play.current.configuration.getString("play.server.vendorkey").get

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

        // Macaroon based tracking-nr
        val validated = try {
          val macaroon = MacaroonsBuilder.deserialize(header("tracking-nr"))
          val verifier = new MacaroonsVerifier(macaroon)
          verifier.satisfyGeneral(new TimestampCaveatVerifier())
          val isValid = verifier.isValid(sharedSecret)
          if (isValid)
            (macaroon.location, macaroon.identifier)
          else
            ("Faker", macaroon.identifier)
        } catch {
          case e: Exception => ("Undefined", "no-macaroon")
        }

        val tuple = (
          validated._1, validated._2,
          header("timestamp").toLong, header("event"),
          header("agent"), 0.01 * digit
        )

        ordererAgentSet = Set(tuple._1) ++ ordererAgentSet

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
