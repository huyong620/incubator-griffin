/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package org.apache.griffin.measure.datasource.connector

import org.apache.griffin.measure.Loggable
import org.apache.griffin.measure.configuration.params.DataConnectorParam
import org.apache.griffin.measure.datasource.TimestampStorage
import org.apache.griffin.measure.datasource.cache.StreamingCacheClient
import org.apache.griffin.measure.datasource.connector.batch._
import org.apache.griffin.measure.datasource.connector.streaming._
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.StreamingContext

import scala.reflect.ClassTag
import scala.util.Try

object DataConnectorFactory extends Loggable {

  val HiveRegex = """^(?i)hive$""".r
  val AvroRegex = """^(?i)avro$""".r
  val TextDirRegex = """^(?i)text-dir$""".r

  val KafkaRegex = """^(?i)kafka$""".r

  /**
    * create data connector
    * @param sparkSession     spark env
    * @param ssc              spark streaming env
    * @param dcParam          data connector param
    * @param tmstCache        same tmst cache in one data source
    * @param streamingCacheClientOpt   for streaming cache
    * @return   data connector
    */
  def getDataConnector(sparkSession: SparkSession,
                       ssc: StreamingContext,
                       dcParam: DataConnectorParam,
                       tmstCache: TimestampStorage,
                       streamingCacheClientOpt: Option[StreamingCacheClient]
                      ): Try[DataConnector] = {
    val conType = dcParam.getType
    val version = dcParam.getVersion
    Try {
      conType match {
        case HiveRegex() => HiveBatchDataConnector(sparkSession, dcParam, tmstCache)
        case AvroRegex() => AvroBatchDataConnector(sparkSession, dcParam, tmstCache)
        case TextDirRegex() => TextDirBatchDataConnector(sparkSession, dcParam, tmstCache)
        case KafkaRegex() => {
          getStreamingDataConnector(sparkSession, ssc, dcParam, tmstCache, streamingCacheClientOpt)
        }
        case _ => throw new Exception("connector creation error!")
      }
    }
  }

  private def getStreamingDataConnector(sparkSession: SparkSession,
                                        ssc: StreamingContext,
                                        dcParam: DataConnectorParam,
                                        tmstCache: TimestampStorage,
                                        streamingCacheClientOpt: Option[StreamingCacheClient]
                                       ): StreamingDataConnector = {
    if (ssc == null) throw new Exception("streaming context is null!")
    val conType = dcParam.getType
    val version = dcParam.getVersion
    conType match {
      case KafkaRegex() => getKafkaDataConnector(sparkSession, ssc, dcParam, tmstCache, streamingCacheClientOpt)
      case _ => throw new Exception("streaming connector creation error!")
    }
  }

  private def getKafkaDataConnector(sparkSession: SparkSession,
                                    ssc: StreamingContext,
                                    dcParam: DataConnectorParam,
                                    tmstCache: TimestampStorage,
                                    streamingCacheClientOpt: Option[StreamingCacheClient]
                                   ): KafkaStreamingDataConnector  = {
    val KeyType = "key.type"
    val ValueType = "value.type"
    val config = dcParam.config
    val keyType = config.getOrElse(KeyType, "java.lang.String").toString
    val valueType = config.getOrElse(ValueType, "java.lang.String").toString
    (getClassTag(keyType), getClassTag(valueType)) match {
      case (ClassTag(k: Class[String]), ClassTag(v: Class[String])) => {
        KafkaStreamingStringDataConnector(sparkSession, ssc, dcParam, tmstCache, streamingCacheClientOpt)
      }
      case _ => {
        throw new Exception("not supported type kafka data connector")
      }
    }
  }

  private def getClassTag(tp: String): ClassTag[_] = {
    try {
      val clazz = Class.forName(tp)
      ClassTag(clazz)
    } catch {
      case e: Throwable => throw e
    }
  }

//  def filterDataConnectors[T <: DataConnector : ClassTag](connectors: Seq[DataConnector]): Seq[T] = {
//    connectors.flatMap { dc =>
//      dc match {
//        case mdc: T => Some(mdc)
//        case _ => None
//      }
//    }
//  }

}
