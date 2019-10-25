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

import com.twitter.scrooge.frontend.ParseException
import scala.util.parsing.combinator._
import scala.util.matching.Regex

object MustacheAST {
  case class Template(segments: Seq[Segment])
  sealed trait Segment
  case class Data(data: String) extends Segment
  case class Interpolation(name: String) extends Segment
  case class Section(
    name: String,
    document: Template,
    reversed: Boolean,
    joiner: Option[String] = None)
      extends Segment
  case class Partial(name: String) extends Segment
}

class ParserPool(size: Int = 10) {
  private[this] val queue = new java.util.concurrent.LinkedBlockingQueue[MustacheParser]()
  (0 until size) foreach { _ =>
    queue.offer(new MustacheParser)
  }

  def apply[T](f: MustacheParser => T): T = {
    val parser = queue.take()
    try {
      f(parser)
    } finally {
      queue.offer(parser)
    }
  }
}

object MustacheParser {
  private[this] val pool = new ParserPool
  def apply(in: String): MustacheAST.Template =
    pool { _(in) }
}

class MustacheParser extends RegexParsers {
  import MustacheAST._

  override def skipWhitespace = false

  def document: Parser[Template] = rep(directive | data) ^^ { x =>
    Template(x.flatten)
  }

  def directive: Parser[Option[Segment]] = interpolation | section | comment | partial

  def interpolation: Parser[Some[Interpolation]] = "{{" ~> id <~ "}}" ^^ { x =>
    Some(Interpolation(x))
  }

  def startSection: Parser[(Boolean, String)] = "{{" ~> """#|\^""".r ~ id <~ "}}" ^^ {
    case prefix ~ id =>
      (prefix == "^", id)
  }

  def endSection: Parser[String ~ Option[String]] =
    "{{/" ~> id ~ opt("|" ~> """([^}]|}(?!}))+""".r) <~ "}}"

  def section: Parser[Some[Section]] = startSection ~ document ~ endSection ^^ {
    case (reversed, id1) ~ doc ~ (id2 ~ joiner) =>
      if (id1 != id2) err("Expected " + id1 + ", got " + id2)
      Some(Section(id1, doc, reversed, joiner))
  }

  def comment: Parser[None.type] = """\{\{!(.*?)}}""".r ^^^ None

  def partial: Parser[Some[Partial]] = "{{>" ~> id <~ "}}" ^^ { x =>
    Some(Partial(x))
  }

  def data: Parser[Option[Segment]] = """([^{]+|\{(?!\{)|\{(?=\{\{))+""".r ^^ { x =>
    Some(Data(x))
  }

  def id: Regex = """[A-Za-z0-9_\.]+""".r

  def apply(in: String): Template = {
    CleanupWhitespace {
      parseAll(document, in) match {
        case Success(result, _) => result
        case x @ Failure(msg, z) => throw new ParseException(x.toString)
        case x @ Error(msg, _) => throw new ParseException(x.toString)
      }
    }
  }
}

/**
 * If a section header is on its own line, remove the trailing linefeed.
 */
object CleanupWhitespace extends (MustacheAST.Template => MustacheAST.Template) {
  import MustacheAST._

  def apply(document: Template): Template = {
    var afterSectionHeader = true
    var sectionHeaderStartedLine = true
    val segments = document.segments.map {
      case Data(data) if afterSectionHeader && sectionHeaderStartedLine && (data startsWith "\n") =>
        afterSectionHeader = false
        sectionHeaderStartedLine = (data endsWith "\n")
        Data(data.substring(1))
      case x @ Section(_, _, _, _) =>
        afterSectionHeader = true
        apply(x)
      case x @ Data(data) if (data endsWith "\n") =>
        afterSectionHeader = false
        sectionHeaderStartedLine = true
        x
      case x =>
        afterSectionHeader = false
        sectionHeaderStartedLine = false
        apply(x)
    }
    Template(segments)
  }

  def apply(segment: Segment): Segment = {
    segment match {
      case Section(name, document, reversed, joiner) =>
        Section(name, apply(document), reversed, joiner)
      case x => x
    }
  }
}
