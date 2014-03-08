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