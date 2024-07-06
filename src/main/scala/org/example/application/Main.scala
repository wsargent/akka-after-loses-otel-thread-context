package org.example.application

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import org.apache.pekko.actor.ActorSystem
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object Main {
  private val logger = LoggerFactory.getLogger(getClass)

  private val InstrumentationName: String = "example"

  def main(args: Array[String]): Unit = {
    val openTelemetry = GlobalOpenTelemetry.get()
    logger.info(s"Using $openTelemetry")
    val actorSystem = ActorSystem("example")
    val tracer = openTelemetry.getTracer(InstrumentationName)

    def traceSync[A](traceName: String)(block: => A): A = {
      val span = tracer.spanBuilder(traceName).startSpan()
      assert(span.isRecording, "No-op span, you must run this class with the java agent so it instruments correctly!")

      try {
        val scope = span.makeCurrent()
        try {
          block
        } finally {
          scope.close()
        }
      } finally {
        span.end()
      }
    }

    val f = traceSync("root") {
      val expectedSpan = Span.current()
      logger.info(s"We expect ${expectedSpan}")
      org.apache.pekko.pattern.after(1.second) {
        val actualSpan = Span.current()
        Future.successful {
          if (! expectedSpan.equals(actualSpan)) {
            logger.error(s"Unexpected $actualSpan")
          } else {
            logger.info(s"Reached delayed with $actualSpan")
          }
          actorSystem.terminate()
        }
      }(actorSystem)
    }
    Await.result(f, 5.seconds)
  }
}
