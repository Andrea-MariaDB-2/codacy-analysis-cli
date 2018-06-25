package com.codacy.analysis.core.files

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.clients.api.ProjectConfiguration
import com.codacy.analysis.core.configuration.CodacyConfigurationFile

import scala.util.Try

final case class FilesTarget(directory: File, readableFiles: Set[Path], unreadableFiles: Set[Path])

private[files] final case class CheckedFiles(readableFiles: Set[Path], unreadableFiles: Set[Path])

class FileSystemFileCollector extends FileCollector[Try] {

  override def list(directory: File,
                    localConfiguration: Either[String, CodacyConfigurationFile],
                    remoteConfiguration: Either[String, ProjectConfiguration]): Try[FilesTarget] = {
    Try {
      val allFiles =
        directory
          .listRecursively()
          .collect { case f if f.isRegularFile => directory.relativize(f) }
          .filterNot(_.startsWith(".git"))
          .to[Set]

      val filteredFiles = defaultFilter(allFiles, localConfiguration, remoteConfiguration)

      val checkedFiles = checkPermissions(directory, filteredFiles)

      FilesTarget(directory, checkedFiles.readableFiles, checkedFiles.unreadableFiles)
    }
  }

}

object FileSystemFileCollector extends FileCollectorCompanion[Try] {

  val name: String = "file-system"

  override def apply(): FileCollector[Try] = new FileSystemFileCollector()

}
