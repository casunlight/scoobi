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

import java.util.Arrays._
import org.apache.hadoop.mapreduce._
import lib.input.InvalidInputException
import org.apache.hadoop.filecache.DistributedCache

import core._
import testing.TestFiles
import org.apache.hadoop.io.serializer.WritableSerialization
import org.apache.hadoop.io.Writable
import java.io.{DataOutput, DataInput}

class ConstantStringDataSource(val value: String) extends DataSource[String, String, String] {

  def inputFormat: Class[_ <: InputFormat[String, String]] = classOf[ConstantStringInputFormat]
  def inputCheck(implicit sc: ScoobiConfiguration) {}
  def inputConfigure(job: Job)(implicit sc: ScoobiConfiguration) {
    job.getConfiguration.set("mapred.constant.string", "value")
    val file = TestFiles.createTempFile("cache")
    DistributedCache.addCacheFile(file.toURI, job.getConfiguration)
  }
  def inputSize(implicit sc: ScoobiConfiguration): Long = value.size
  lazy val inputConverter = ConstantStringInputConverter(value)

  case class ConstantStringInputConverter(value: String) extends InputConverter[String, String, String] {
    def fromKeyValue(context: this.type#InputContext, key: String, v: String) = value
  }

  override def equals(a: Any) = a match {
    case other: ConstantStringDataSource => value == other.value
    case _                               => false
  }
}
case class ConstantStringInputSplit(var value: String) extends InputSplit with Writable {
  def this() = this("")
  def getLength = value.size
  def readFields(in: DataInput) {
    value = readLine()
  }

  def write(out: DataOutput) {
    out.writeChars(value)
  }
  def getLocations = Array("localhost")


}


object ConstantStringDataSource {
  def apply(value: String) = new ConstantStringDataSource(value)
}
class FailingDataSource extends ConstantStringDataSource("") {
  override def inputFormat: Class[_ <: InputFormat[String, String]] = classOf[FailingInputFormat]
}
object FailingDataSource {
  def apply() = new FailingDataSource
}
case class ConstantStringRecordReader(value: String) extends RecordReader[String, String] {
  def this() = this("")
  def initialize(split: InputSplit, context: TaskAttemptContext) {}
  def nextKeyValue() = false
  def getCurrentKey = value
  def getCurrentValue = value
  def getProgress = 0.0f
  def close() {}
}

class ConstantStringInputFormat(value: String) extends InputFormat[String, String] {
  def this() = this("")
  def getSplits(context: JobContext) = asList(ConstantStringInputSplit(value))
  def createRecordReader(split: InputSplit, context: TaskAttemptContext) = ConstantStringRecordReader(value)
}

class FailingInputFormat extends InputFormat[String, String] {
  def getSplits(context: JobContext) = { throw new InvalidInputException(asList()); asList() }
  def createRecordReader(split: InputSplit, context: TaskAttemptContext) = ConstantStringRecordReader("")
}

