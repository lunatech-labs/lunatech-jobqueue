package com.lunatech.jobexecutor

import play.api.libs.json.Json
import java.io.File

/**
 * Representation of a job queue that the web service spits out
 */
case class JobQueue(
  name: String,
  executors: Int,
  maxExecutionTime: Long,
  queuedJobs: Int,
  runningJobs: Int,
  completedJobs: Int,
  failedJobs: Int)


object JobQueue {
  implicit val queueFormat = Json.format[JobQueue]

  def fromConfig(queueConfig: QueueConfig) = {
    val queuedJobs = queueConfig.queueDir.toFile.listFiles(jobFileFilter).size
    val runningJobs = queueConfig.spoolDir.toFile.listFiles.size
    val completedJobs = queueConfig.completedDir.toFile.listFiles.size
    val failedJobs = queueConfig.failedDir.toFile.listFiles.size

    JobQueue(queueConfig.queueName, queueConfig.executorConfig.executors, queueConfig.executorConfig.maxExecutionTime.toSeconds, queuedJobs, runningJobs, completedJobs, failedJobs)
  }
}

case class DetailedJobQueue(
  name: String,
  queuedJobsSize: Int,
  queuedJobs: Seq[Job],
  runningJobsSize: Int,
  runningJobs: Seq[Job],
  completedJobsSize: Int,
  completedJobs: Seq[Job],
  failedJobsSize: Int,
  failedJobs: Seq[Job])


object DetailedJobQueue {
  implicit val queueFormat = Json.format[DetailedJobQueue]

  def fromConfig(queueConfig: QueueConfig) = {
    val queuedJobs = queueConfig.queueDir.toFile.listFiles.map({ file =>
         play.Logger.info("File " + file);Job.fromFile(file)
    })
    val queuedJobsSize = queuedJobs.size
    val runningJobs = queueConfig.spoolDir.toFile.listFiles.map({ file =>
         file.listFiles.toList.find(_.getName == "job")
    }).flatten.map(Job.fromFile(_))
    val runningJobsSize = runningJobs.size
    val completedJobs = queueConfig.completedDir.toFile.listFiles.map({ file =>
         file.listFiles.toList.find(_.getName == "job")
    }).flatten.map(Job.fromFile(_))
    val completedJobsSize = completedJobs.size
    val failedJobs = queueConfig.failedDir.toFile.listFiles.map({ file =>
         file.listFiles.toList.find(_.getName == "job")
    }).flatten.map(Job.fromFile(_))
    val failedJobsSize = failedJobs.size

    DetailedJobQueue(queueConfig.queueName, queuedJobsSize, queuedJobs, runningJobsSize, runningJobs, completedJobsSize, completedJobs, failedJobsSize, failedJobs)
  }

}