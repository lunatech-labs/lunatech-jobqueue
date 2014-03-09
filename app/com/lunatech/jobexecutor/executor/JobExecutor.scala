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

  var currentJobUid: Option[String] = None
  var runningProcess: Option[Process] = None
  var checkSchedule: Option[Cancellable] = None
  var killSchedule: Option[Cancellable] = None

  def receive = {
    case job: Job => {
      currentJobUid = Some(job.uid)
      Files.createDirectory(jobSpoolDir(job.uid))
      Files.write(jobSpoolFile(job.uid), implicitly[Serializable[Job]].serialize(job))
      val pb = processBuilder(job)

      runningProcess = Some(pb.start())
      checkSchedule = Some(context.system.scheduler.schedule(100.milliseconds, 100.milliseconds, self, CheckStatus))
      killSchedule = Some(context.system.scheduler.scheduleOnce(queueConfig.executorConfig.maxExecutionTime, self, Terminate(job.uid)))
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
        currentJobUid.map { id => 
          jobQueueManager ! JobTerminated(id)
        }
        currentJobUid = None
      }
    }
    case Terminate(jobUid) => runningProcess foreach { process =>
      process.destroy()
      handleJobFailure(s"Maximum execution time of ${queueConfig.executorConfig.maxExecutionTime} exceeded.")
      currentJobUid.map { id => 
          jobQueueManager ! JobTerminated(id)
      }
      currentJobUid = None
    }
  }

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    handleJobFailure(reason.getStackTraceString)
    currentJobUid.map { id => 
        jobQueueManager ! JobTerminated(id)
    }
  }

  def processBuilder(job: Job): ProcessBuilder = {
    // ProcessBuilder needs command and parameters to be separate. We don't have that. Alternatively,
    // we could use Runtime.exec, but then redirecting output to files is awkward. We settle on executing
    // the command through a shell
    val pb = new ProcessBuilder("/bin/sh", "-c", job.command)
    pb.redirectError(stderrSpoolFile(job.uid).toFile)
    pb.redirectOutput(stdoutSpoolFile(job.uid).toFile)
    pb
  }

  def jobSpoolDir(jobUid: String): Path = queueConfig.spoolDir resolve jobUid
  def jobSpoolFile(jobUid: String): Path = jobSpoolDir(jobUid) resolve "job"
  def stderrSpoolFile(jobUid: String): Path = jobSpoolDir(jobUid) resolve "stderr"
  def stdoutSpoolFile(jobUid: String): Path = jobSpoolDir(jobUid) resolve "stdout"

  def jobFailedDir(jobUid: String): Path = queueConfig.failedDir resolve jobUid
  def failureReasonFile(jobUid: String): Path = jobFailedDir(jobUid) resolve "reason"

  def jobCompletedDir(jobUid: String): Path = queueConfig.completedDir resolve jobUid

  def handleJobFailure(reason: String) = currentJobUid foreach { jobUid =>
    Files.move(jobSpoolDir(jobUid), jobFailedDir(jobUid))
    Files.write(failureReasonFile(jobUid), reason.getBytes(Codec.UTF8.charSet))
  }

  def handleJobSuccess() = currentJobUid foreach { jobUid =>
    Files.move(jobSpoolDir(jobUid), jobCompletedDir(jobUid))
  }
}
