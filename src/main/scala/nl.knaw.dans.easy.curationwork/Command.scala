/**
 * Copyright (C) 2017 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.curationwork

import java.nio.file.Paths

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging

import scala.language.reflectiveCalls
import scala.util.control.NonFatal
import scala.util.{ Failure, Try }

object Command extends App with DebugEnhancedLogging {
  type FeedBackMessage = String

  val configuration = Configuration(Paths.get(System.getProperty("app.home")))
  val commandLine: CommandLineOptions = new CommandLineOptions(args, configuration) {
    verify()
  }
  val commonCurationArea = Paths.get(configuration.properties.getString("curation.common.directory"))
  val managerCurationDirString = configuration.properties.getString("curation.personal.directory")
  val datamanagerProperties = Configuration(Paths.get(System.getProperty("app.home"))).datamanagers

  val reporter = new Report(commonCurationArea, managerCurationDirString)
  val assigner = new Assign(commonCurationArea, managerCurationDirString, datamanagerProperties)
  val unassigner = new Unassign(commonCurationArea, managerCurationDirString)

  runSubcommand(reporter, assigner, unassigner)
    .doIfSuccess(msg => println(s"$msg"))
    .doIfFailure { case e => logger.error(e.getMessage, e) }
    .doIfFailure { case NonFatal(e) => println(s"FAILED: ${ e.getMessage }") }

  private def runSubcommand(reporter: Report, assigner: Assign, unassigner: Unassign): Try[FeedBackMessage] = {
    commandLine.subcommand
      .collect {
        case cmd @ commandLine.list =>
          if (validDatamanager(cmd.datamanager.toOption)) reporter.listCurationWork(cmd.datamanager.toOption)
          else Try(s"Error: Unknown datamanager ${cmd.datamanager()}")
        case cmd @ commandLine.assign =>
          if (validDatamanager(cmd.datamanager.toOption)) assigner.assignCurationWork(cmd.datamanager(), cmd.uuid())
          else Try(s"Error: Unknown datamanager ${cmd.datamanager()}")
        case cmd @ commandLine.unassign =>
          if (validDatamanager(cmd.datamanager.toOption)) unassigner.unassignCurationWork(cmd.datamanager.toOption, cmd.uuid.toOption)
          else Try(s"Error: Unknown datamanager ${cmd.datamanager()}")
      }
      .getOrElse(Failure(new IllegalArgumentException(s"Unknown command: ${ commandLine.subcommand }")))
  }

  private def validDatamanager(datamanager: Option[DatamanagerId]): Boolean = {
    datamanager.isEmpty || configuration.datamanagers.containsKey(datamanager.getOrElse(""))
  }
}
