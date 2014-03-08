package com.lunatech

import play.api.libs.json.Format
import com.lunatech.queue.Serializable
import play.api.libs.json.Json
import scala.io.Codec
import java.io.FileFilter
import java.io.File

package object jobexecutor {

  implicit def serializableFromJson[A: Format]: Serializable[A] = new Serializable[A] {
    override def serialize(item: A): Array[Byte] = Json.prettyPrint(Json.toJson(item)).getBytes(Codec.UTF8.charSet)
    override def deserialize(bytes: Array[Byte]): A = Json.parse(new String(bytes, Codec.UTF8.charSet)).as[A]
  }

  val queueDirectoryFilter = new FileFilter {
    def accept(file: File) = file.isDirectory() && !file.isHidden()
  }

  val jobFileFilter = new FileFilter {
    def accept(file: File) = file.isFile && !file.isHidden
  }

}