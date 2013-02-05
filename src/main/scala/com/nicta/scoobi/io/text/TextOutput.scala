/**
 * Copyright 2011,2012 National ICT Australia Limited
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
package com.nicta.scoobi
package io
package text

import org.apache.commons.logging.LogFactory
import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapred.FileAlreadyExistsException
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
import org.apache.hadoop.mapreduce.Job

import core._
import impl.io.Helper
import avro.AvroInput
import org.apache.hadoop.conf.Configuration

/** Smart functions for persisting distributed lists by storing them as text files. */
object TextOutput {
  lazy val logger = LogFactory.getLog("scoobi.TextOutput")

  /** Persist a distributed list as a text file. */
  def toTextFile[A : Manifest](dl: DList[A], path: String, overwrite: Boolean = false) =
    dl.addSink(textFileSink(path, overwrite))

  def textFileSink[A : Manifest](path: String, overwrite: Boolean = false) = {
    new DataSink[NullWritable, A, A] {
      private val outputPath = new Path(path)

      val outputFormat = classOf[TextOutputFormat[NullWritable, A]]
      val outputKeyClass = classOf[NullWritable]
      val outputValueClass = implicitly[Manifest[A]] match {
        case Manifest.Boolean => classOf[java.lang.Boolean].asInstanceOf[Class[A]]
        case Manifest.Char    => classOf[java.lang.Character].asInstanceOf[Class[A]]
        case Manifest.Short   => classOf[java.lang.Short].asInstanceOf[Class[A]]
        case Manifest.Int     => classOf[java.lang.Integer].asInstanceOf[Class[A]]
        case Manifest.Long    => classOf[java.lang.Long].asInstanceOf[Class[A]]
        case Manifest.Float   => classOf[java.lang.Float].asInstanceOf[Class[A]]
        case Manifest.Double  => classOf[java.lang.Double].asInstanceOf[Class[A]]
        case Manifest.Byte    => classOf[java.lang.Byte].asInstanceOf[Class[A]]
        case mf               => mf.erasure.asInstanceOf[Class[A]]
      }

      def outputCheck(implicit sc: ScoobiConfiguration) {
        if (Helper.pathExists(outputPath)(sc.configuration) && !overwrite) {
            throw new FileAlreadyExistsException("Output path already exists: " + outputPath)
        } else logger.info("Output path: " + outputPath.toUri.toASCIIString)
      }

      def outputConfigure(job: Job)(implicit sc: ScoobiConfiguration) {
        FileOutputFormat.setOutputPath(job, outputPath)
      }

      override def outputSetup(implicit configuration: Configuration) {
        if (Helper.pathExists(outputPath)(configuration) && overwrite) {
          logger.info("Deleting the pre-existing output path: " + outputPath.toUri.toASCIIString)
          Helper.deletePath(outputPath)(configuration)
        }
      }

      lazy val outputConverter = new OutputConverter[NullWritable, A, A] {
        def toKeyValue(x: A) = (NullWritable.get, x)
      }

      /**
       * it is not possible to transform a Text sink, storing objects of type A to strings, to a Text source that could load
       * strings into objects of type A because we don't know at this stage how to go from String => A
       */
      override def toSource: Option[Source] = None
    }
  }

  /** Persist a distributed lists of 'Products' (e.g. Tuples) as a deliminated text file. */
  def toDelimitedTextFile[A <: Product : Manifest](dl: DList[A], path: String, sep: String = "\t", overwrite: Boolean = false) = {
    def anyToString(any: Any, sep: String): String = any match {
      case prod: Product => prod.productIterator.map(anyToString(_, sep)).mkString(sep)
      case _             => any.toString
    }
    toTextFile(dl map { anyToString(_, sep) }, path, overwrite)
  }
}
