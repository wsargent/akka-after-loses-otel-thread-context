package org.example.application

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.slf4j.LoggerFactory

import scala.language.reflectiveCalls
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object Main {
  private val logger = LoggerFactory.getLogger(getClass)
  private val openTelemetry = GlobalOpenTelemetry.get()
  private val tracer = openTelemetry.getTracer("example")

  // https://tersesystems.com/blog/2024/06/20/executioncontext.parasitic-and-friends/
  private val opportunisticExecutionContext = (scala.concurrent.ExecutionContext: {def opportunistic: scala.concurrent.ExecutionContextExecutor}).opportunistic

  def main(args: Array[String]): Unit = {
    implicit val actorSystem = ActorSystem("example")

    implicit val ec = ExecutionContext.global
    val f = for {
      _ <- operation("wrapping")
      _ <- operation("global")
      _ <- operation("parasitic")
      _ <- operation("opportunistic")
      _ <- operation("dispatcher")
    } yield actorSystem.terminate()

    Await.result(f, 30.seconds)
  }

  def operation(mode: String)(implicit actorSystem: ActorSystem): Future[Done] = {
    traceSync(s"root $mode") {
      val expectedSpan = Span.current()
      logger.info(s"mode: We expect ${expectedSpan}")
      val afterExecutionContext = defineExecutionContext(mode)

      org.apache.pekko.pattern.after(1.second, actorSystem.scheduler) {
        val actualSpan = Span.current()
        Future.successful {
          if (!expectedSpan.equals(actualSpan)) {
            logger.error(s"$mode: Unexpected $actualSpan")
          } else {
            logger.info(s"$mode: Reached delayed with $actualSpan")
          }
          Done
        }
      }(afterExecutionContext)
    }
  }

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

  def defineExecutionContext(mode: String)(implicit system: ActorSystem): ExecutionContext = {
    val dispatcher = system.classicSystem.dispatcher

    mode match {
      case "wrapping" =>
        val context = Context.current()
        new ExecutionContext {
          override def execute(runnable: Runnable): Unit = dispatcher.execute(context.wrap(runnable))

          override def reportFailure(cause: Throwable): Unit = dispatcher.reportFailure(cause)
        }
      case "global" =>
        ExecutionContext.global
      case "parasitic" =>
        ExecutionContext.parasitic
      case "opportunistic" =>
        opportunisticExecutionContext
      case _ =>
        dispatcher
    }
  }

}
