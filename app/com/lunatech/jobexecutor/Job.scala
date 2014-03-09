package com.lunatech.jobexecutor

import org.joda.time.DateTime
import play.api.libs.json.Json
import java.io.File
import java.nio.file.Files

case class Job(
  uid: String,
  id: Option[String],
  createdOn: DateTime,
  command: String)

object Job {
  implicit val jobFormat = Json.format[Job]

  def fromFile(file: File): Job = {
    val bytes = Files.readAllBytes(file.toPath)
    serializableFromJson[Job].deserialize(bytes)
  }
}