package com.codacy.analysis.core.storage

import better.files.File
import better.files.File.home
import io.circe._
import io.circe.syntax._
import org.log4s.{Logger, getLogger}

import scala.compat.Platform
import scala.util.Try

abstract class FileDataStorage[T](val storageFilename: String) {

  implicit val encoder: Encoder[T]
  implicit val decoder: Decoder[T]

  private val logger: Logger = getLogger

  private val cacheFolder: File = {
    val defaultFolder = File.currentWorkingDirectory / ".codacy" / "codacy-analysis-cli"
    val cacheBaseFolderOpt = sys.props.get("user.home").map(File(_))

    val result = cacheBaseFolderOpt.fold(defaultFolder) { cacheBaseFolder =>
      val osNameOpt = sys.props.get("os.name").map(_.toLowerCase)
      osNameOpt match {
        case Some(sysName) if sysName.contains("mas") =>
          cacheBaseFolder / "Library" / "Caches" / "com.codacy" / "codacy-analysis-cli"

        case Some(sysName) if sysName.contains("nix") || sysName.contains("nux") || sysName.contains("aix") =>
          cacheBaseFolder / ".cache" / "codacy" / "codacy-analysis-cli"

        case Some(sysName) if sysName.contains("windows") =>
          val windowsCacheDir = File(sys.env("APPDATA"))
          if (windowsCacheDir.exists) {
            windowsCacheDir / "Codacy" / "codacy-analysis-cli"
          } else {
            defaultFolder
          }
        case _ => defaultFolder
      }
    }

    result.createIfNotExists(asDirectory = true, createParents = true)
    result
  }

  val storageFile: File = cacheFolder / storageFilename

  private def writeToFile(content: String): Try[File] =
    Try {
      storageFile.write(content)
    }

  private def readFromFile(): Try[String] =
    Try {
      storageFile.contentAsString
    }

  def invalidate(): Try[Unit] =
    Try {
      logger.debug("Invalidating storage")
      storageFile.delete()
    }

  def save(values: Seq[T]): Boolean = {
    logger.debug("Saving new values to storage")
    val storageListJson = values.asJson.toString
    val wroteSuccessfully = writeToFile(storageListJson)
    logger.debug(s"Storage saved with status: ${wroteSuccessfully}")
    wroteSuccessfully.isSuccess
  }

  def get(): Option[Seq[T]] = {
    logger.debug("Retrieving storage")
    if (storageFile.exists) {
      val fileContentOpt = readFromFile().toOption
      fileContentOpt.flatMap { content =>
        parser.decode[Seq[T]](content).fold(_ => None, v => Some(v)).filter(_.nonEmpty)
      }
    } else {
      None
    }
  }
}
