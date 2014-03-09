package com.lunatech.jobexecutor.executor

import akka.actor.{ Actor, ActorRef, Props, actorRef2Scala }
import com.lunatech.jobexecutor.{ Job, QueueConfig }
import com.lunatech.queue.DirectoryBackedQueue
import java.nio.file.Files
import play.api.Logger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.io.Codec

object JobQueueManager {
  case object CheckJobs
  case class JobCompleted(jobUid: String)
  case class JobTerminated(jobUid: String)

  def props(queueConfig: QueueConfig): Props = Props(new JobQueueManager(queueConfig))
}

class JobQueueManager(queueConfig: QueueConfig) extends Actor {
  import JobQueueManager._

  Logger.info(s"Starting JobQueueManager for queue ${queueConfig.queueName}")

  cleanupSpoolDir()

  val executors = (1 to queueConfig.executorConfig.executors).map { i =>
    Logger.info("Spawning executor for manager " + queueConfig.queueName)
    context.actorOf(JobExecutor.props(queueConfig, self), s"${queueConfig.queueName}-JobExecutor-$i")
  }.toSet

  val jobQueue = DirectoryBackedQueue[Job](queueConfig.queueDir)

  var busy: Set[ActorRef] = Set()

  context.system.scheduler.schedule(0.milliseconds, 100.milliseconds, self, CheckJobs)

  def receive = {
    case CheckJobs => {
      for {
        executor <- nonBusyExecutor
        job <- jobQueue.dequeue()
      } yield {
        busy = busy + executor
        executor ! job
        self ! CheckJobs
      }
    }
    case JobCompleted(jobUid: String) => {
      busy = busy - sender
      self ! CheckJobs
    }
    case JobTerminated(jobUid: String) => {
      busy = busy - sender
      self ! CheckJobs
    }
  }

  def nonBusyExecutor: Option[ActorRef] = (executors diff busy).headOption

  def cleanupSpoolDir() = {
    queueConfig.spoolDir.toFile.listFiles.filter(f => f.isDirectory && !f.isHidden).foreach { directory =>
      val failureReasonFile = directory.toPath resolve "reason"
      Files.write(failureReasonFile, "Job was running while JobQueueManager restarted".getBytes(Codec.UTF8.charSet))
      Files.move(directory.toPath, queueConfig.failedDir resolve directory.getName)
    }
  }
}

