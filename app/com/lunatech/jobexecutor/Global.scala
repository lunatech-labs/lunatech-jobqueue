package com.lunatech.jobexecutor

import play.api.{ Application, GlobalSettings, Logger }
import java.nio.file.Paths
import java.nio.file.Files
import play.api.Play
import com.lunatech.jobexecutor.web.Webservice
import com.lunatech.queue.DirectoryBackedQueue
import play.api.libs.concurrent.Akka
import com.lunatech.jobexecutor.executor.JobQueueManager
import scala.concurrent.duration._
import scala.util.control.Exception
import java.nio.file.Path
import java.nio.file.FileAlreadyExistsException
import scala.collection.JavaConverters._

object Global extends GlobalSettings {
  private var controllers: Map[Class[_], _] = Map()


  override def onStart(app: Application): Unit = {
      val config = Play.configuration(app)

      val dataDirKey = "jobqueue.data-directory"

      val dataPath = Paths.get(config.getString(dataDirKey).getOrElse(sys.error(s"Configuration key $dataDirKey missing.")))
      if (!Files.isDirectory(dataPath)) {
         sys.error(s"The path $dataPath is not a directory")
      }

      Logger.info(s"Data directory is $dataPath")

      val globalQueueDirectory = dataPath resolve "queues"
      val globalSpoolDirectory = dataPath resolve "spools"
      val globalCompletedDirectory = dataPath resolve "completed"
      val globalFailedDirectory = dataPath resolve "failures"

      createDirectoryIfNotExists(globalQueueDirectory)
      createDirectoryIfNotExists(globalSpoolDirectory)
      createDirectoryIfNotExists(globalCompletedDirectory)
      createDirectoryIfNotExists(globalFailedDirectory)

      val queuesConfigKey = "jobqueue.queues"
      val queueConfigs = config.getConfigList(queuesConfigKey).getOrElse(sys.error(s"Configuration key $queuesConfigKey is missing.")).asScala.toList map { config =>
        val queueName = config.getString("name").get
        val queueDirectory = globalQueueDirectory resolve queueName
        val spoolDirectory = globalSpoolDirectory resolve queueName
        val completedDirectory = globalCompletedDirectory resolve queueName
        val failedDirectory = globalFailedDirectory resolve queueName

        createDirectoryIfNotExists(queueDirectory)
        createDirectoryIfNotExists(spoolDirectory)
        createDirectoryIfNotExists(completedDirectory)
        createDirectoryIfNotExists(failedDirectory)

        val executorConfig = ExecutorConfig(
          executors = config.getInt("executors").getOrElse(1),
          maxExecutionTime = Duration(config.getMilliseconds( "maxExecutionTime").getOrElse(60000L), "milliseconds"))

        val queueConfig = QueueConfig(queueName, queueDirectory, spoolDirectory, completedDirectory, failedDirectory, executorConfig)

        Akka.system(app).actorOf(JobQueueManager.props(queueConfig), s"$queueName-JobQueueManager")
        queueConfig
      }

      controllers += classOf[Webservice] -> new Webservice(queueConfigs)
  }

  def createDirectoryIfNotExists(path: Path): Unit =
    Exception.catching(classOf[FileAlreadyExistsException]) opt Files.createDirectory(path)


  override def getControllerInstance[A](controllerClass: Class[A]): A = controllers(controllerClass).asInstanceOf[A]

}
