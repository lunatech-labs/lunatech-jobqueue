package com.lunatech.jobexecutor.executor

import akka.actor.{ Actor, ActorRef, Cancellable, Props, actorRef2Scala }
import com.lunatech.jobexecutor.{ Job, QueueConfig }
import com.lunatech.queue.Serializable
import java.nio.file.{ Files, Path }
import scala.concurrent.duration.DurationInt
import scala.io.Codec
import scala.util.control.Exception

object JobExecutor {
  case object CheckStatus
  case class Terminate(jobId: String)

  def props(queueConfig: QueueConfig, jobQueueManager: ActorRef): Props = Props(new JobExecutor(queueConfig, jobQueueManager))
}

class JobExecutor(queueConfig: QueueConfig, jobQueueManager: ActorRef) extends Actor {
  import JobExecutor._
  import JobQueueManager._

  implicit val ec = context.dispatcher

  var currentJobId: Option[String] = None
  var runningProcess: Option[Process] = None
  var checkSchedule: Option[Cancellable] = None
  var killSchedule: Option[Cancellable] = None

  def receive = {
    case job: Job => {
      currentJobId = Some(job.id)
      Files.createDirectory(jobSpoolDir(job.id))
      Files.write(jobSpoolFile(job.id), implicitly[Serializable[Job]].serialize(job))
      val pb = processBuilder(job)

      runningProcess = Some(pb.start())
      checkSchedule = Some(context.system.scheduler.schedule(100.milliseconds, 100.milliseconds, self, CheckStatus))
      killSchedule = Some(context.system.scheduler.scheduleOnce(queueConfig.executorConfig.maxExecutionTime, self, Terminate(job.id)))
    }
    case CheckStatus => runningProcess foreach { process =>
      Exception.catching(classOf[IllegalThreadStateException]) opt process.exitValue() foreach { exitValue =>
        runningProcess = None
        checkSchedule.foreach(_.cancel())
        killSchedule.foreach(_.cancel())
        if (exitValue == 0)
          handleJobSuccess()
        else
          handleJobFailure("Exit code " + exitValue)
        jobQueueManager ! JobCompleted(currentJobId.get)
        currentJobId = None
      }
    }
    case Terminate(jobId) => runningProcess foreach { process =>
      process.destroy()
      handleJobFailure(s"Maximum execution time of ${queueConfig.executorConfig.maxExecutionTime} exceeded.")
      jobQueueManager ! JobTerminated(currentJobId.get)
      currentJobId = None
    }
  }

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    handleJobFailure(reason.getStackTraceString)
    jobQueueManager ! JobTerminated(currentJobId.get)
  }

  def processBuilder(job: Job): ProcessBuilder = {
    // ProcessBuilder needs command and parameters to be separate. We don't have that. Alternatively,
    // we could use Runtime.exec, but then redirecting output to files is awkward. We settle on executing
    // the command through a shell
    val pb = new ProcessBuilder("/bin/sh", "-c", job.command)
    pb.redirectError(stderrSpoolFile(job.id).toFile)
    pb.redirectOutput(stdoutSpoolFile(job.id).toFile)
    pb
  }

  def jobSpoolDir(jobId: String): Path = queueConfig.spoolDir resolve jobId
  def jobSpoolFile(jobId: String): Path = jobSpoolDir(jobId) resolve "job"
  def stderrSpoolFile(jobId: String): Path = jobSpoolDir(jobId) resolve "stderr"
  def stdoutSpoolFile(jobId: String): Path = jobSpoolDir(jobId) resolve "stdout"

  def jobFailedDir(jobId: String): Path = queueConfig.failedDir resolve jobId
  def failureReasonFile(jobId: String): Path = jobFailedDir(jobId) resolve "reason"

  def jobCompletedDir(jobId: String): Path = queueConfig.completedDir resolve jobId

  def handleJobFailure(reason: String) = currentJobId foreach { jobId =>
    Files.move(jobSpoolDir(jobId), jobFailedDir(jobId))
    Files.write(failureReasonFile(jobId), reason.getBytes(Codec.UTF8.charSet))
  }

  def handleJobSuccess() = currentJobId foreach { jobId =>
    Files.move(jobSpoolDir(jobId), jobCompletedDir(jobId))
  }
}
