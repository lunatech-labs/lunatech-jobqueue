package com.lunatech.jobexecutor.web

import com.lunatech.jobexecutor._
import com.lunatech.queue.DirectoryBackedQueue
import java.nio.file.Files
import java.util.UUID
import org.joda.time.DateTime
import play.api.libs.functional.syntax.toInvariantFunctorOps
import play.api.libs.json.Json
import play.api.mvc.{ Action, Controller }

class Webservice(queueConfigs: Seq[QueueConfig]) extends Controller {

  case class CreateJobData(id: Option[String],
    command: String) {

    def createJob: Job = {
      val uid = UUID.randomUUID().toString
      Job.apply(uid, id, DateTime.now, command)
    }
  }

  object CreateJobData {
    implicit val createJobDataFormat = Json.format[CreateJobData]
  }

  def listQueues = Action {
    val queues = queueConfigs.map { queueConfig => JobQueue.fromConfig(queueConfig) }
    Ok(Json.toJson(queues))
  }

  def showQueue(queueName: String) = Action {
    queueConfigs.find(_.queueName == queueName).map { queueConfig =>
      val queue = JobQueue.fromConfig(queueConfig)
      Ok(Json.toJson(queue))
    } getOrElse NotFound
  }

  def listJobs(queueName: String) = Action {
    queueConfigs.find(_.queueName == queueName).map { queueConfig =>
      val queue = DetailedJobQueue.fromConfig(queueConfig)

      Ok(Json.toJson(queue))
   } getOrElse NotFound
  }

  def postJob(queueName: String) = Action(parse.json) { request =>
    request.body.validate[CreateJobData].fold(
      invalid => BadRequest,
      jobData => queueConfigs.find(_.queueName == queueName).map { queueConfig =>
        val job = jobData.createJob
        val dbq = DirectoryBackedQueue[Job](queueConfig.queueDir)
        dbq.enqueue(job)
        Created(Json.toJson(job))
      } getOrElse NotFound)

  }

  def deleteJob(queueName: String, jobUid: String) = Action { request =>
    // Find the filename of the job with this id
    (for {
      queuePath <- queueConfigs.find(_.queueName == queueName).map(_.queueDir)
      jobFile <- queuePath.toFile.listFiles(jobFileFilter).toList.find { file =>
        Job.fromFile(file).uid == jobUid
      }
    } yield {
      val deleted = Files.deleteIfExists(jobFile.toPath)
      if (deleted) Ok
      else NotFound
    }) getOrElse NotFound
  }
}