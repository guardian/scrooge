/*
 * Copyright 2011 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.scrooge.mustache

import HandlebarLoader._
import java.util.logging.{Level, Logger}
import java.util.Properties
import scala.collection.concurrent.TrieMap
import scala.io.Source

class HandlebarLoader(
  prefix: String,
  suffix: String = ".mustache",
  commentFct: (CommentStyle => String) = scalaJavaCommentFunction) {

  private[this] val cache = new TrieMap[String, Handlebar]

  def apply(name: String): Handlebar = {
    val fullName = prefix + name + suffix
    cache.getOrElseUpdate(
      name,
      getClass.getResourceAsStream(fullName) match {
        case null =>
          throw new NoSuchElementException("template not found: " + fullName)
        case inputStream =>
          try {
            new Handlebar(Source.fromInputStream(inputStream).getLines().mkString("\n"))
          } catch {
            case e: Exception =>
              println("Exception parsing template at " + fullName)
              throw e
          }
      }
    )
  }

  val header: String = {
    val p = new Properties
    val resource = getClass.getResource("/com/twitter/scrooge-generator/build.properties")
    if (resource == null)
      Logger
        .getLogger("scrooge-generator")
        .log(Level.WARNING, "Scrooge's build.properties not found")
    else
      p.load(resource.openStream())

    Seq(
      commentFct(BlockBegin),
      commentFct(BlockContinuation) + "Generated by Scrooge",
      commentFct(BlockContinuation) + "  version: %s".format(p.getProperty("version", "?")),
      commentFct(BlockContinuation) + "  rev: %s".format(p.getProperty("build_revision", "?")),
      commentFct(BlockContinuation) + "  built at: %s".format(p.getProperty("build_name", "?")),
      commentFct(BlockEnd)
    ).mkString("\n")
  }
}

object HandlebarLoader {
  sealed abstract class CommentStyle
  case object BlockBegin extends CommentStyle
  case object BlockContinuation extends CommentStyle
  case object BlockEnd extends CommentStyle
  case object SingleLineComment extends CommentStyle

  def scalaJavaCommentFunction(commentStyle: CommentStyle): String = {
    commentStyle match {
      case BlockBegin => "/**"
      case BlockContinuation => " * "
      case BlockEnd => " */\n"
      case SingleLineComment => "// "
    }
  }
}
