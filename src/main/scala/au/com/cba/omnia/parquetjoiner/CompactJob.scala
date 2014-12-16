package au.com.cba.omnia.parquetjoiner

import java.util.{List => JavaList}
import java.io.DataOutput
import java.io.DataInput

import scala.collection.mutable.MutableList
import scala.collection.JavaConverters._

import org.apache.hadoop.conf.{Configuration,Configured}
import org.apache.hadoop.fs.{FileSystem,Path}
import org.apache.hadoop.mapreduce.{Job,Mapper}
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
import org.apache.hadoop.util.{Tool,ToolRunner}

import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.`type`.TypeReference

import org.rogach.scallop.ScallopConf

import parquet.example.data.{Group,GroupWriter}
import parquet.hadoop.{BadConfigurationException,ParquetOutputFormat}
import parquet.hadoop.api.WriteSupport
import parquet.hadoop.example.GroupWriteSupport

import au.com.cba.omnia.permafrost.hdfs.Hdfs

class ParquetCompactConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  val input      = opt[String](required = true)
  val output     = opt[String](required = true)
  val count      = opt[Int](required = false, default = Option(10))
}

class ParquetCompactWriteSupport extends GroupWriteSupport {
  var extraMetadata: java.util.Map[String, String] = _

  override def init(configuration: Configuration): WriteSupport.WriteContext = {
    extractMetadata(configuration)
    super.init(configuration)
  }

  def extractMetadata(configuration: Configuration) = {
    val metadataJson = configuration.get(ParquetCompactWriteSupport.extraMetadataKey)
    try {
      extraMetadata = new ObjectMapper().readValue(metadataJson, new TypeReference[java.util.Map[String,String]](){})
    } catch { case e: java.io.IOException =>
      throw new BadConfigurationException("Unable to deserialize extra extra metadata: " + metadataJson, e)
    }
  }
}

object ParquetCompactWriteSupport {
  val extraMetadataKey = "parquetjoiner.compact.extrametadata" //Why do we need this?
  val joinFileIndex    = "parquetjoiner.compact.file.index"
  val joinFileCount    = "parquetjoiner.compact.file.count"
  val joinFileTotal    = "parquetjoiner.compact.file.total" // provided for sense-checking ... fail fast if number of footers changes
}

case class CompactJob(conf: ParquetCompactConf, index: Int, total: Int) extends Configured with Tool {
  override def run(args: Array[String]) = {
    val fs         = FileSystem.get(getConf)
    val inputPath  = new Path(conf.input())
    val outputPath = new Path(conf.output())

    // Pass along metadata (which includes the thrift schema) to the results.
    val metadata = ParquetUtils.readKeyValueMetaData(inputPath, fs)
    val metadataJson = new ObjectMapper().writeValueAsString(metadata)
    getConf.set(ParquetCompactWriteSupport.extraMetadataKey, metadataJson)
    getConf.set(ParquetCompactWriteSupport.joinFileIndex, index.toString)
    getConf.set(ParquetCompactWriteSupport.joinFileCount, conf.count().toString)
    getConf.set(ParquetCompactWriteSupport.joinFileTotal, total.toString)

    val job = new Job(getConf)

    FileInputFormat.setInputPaths(job, inputPath)
    FileOutputFormat.setOutputPath(job, outputPath)
    ParquetOutputFormat.setWriteSupportClass(job, classOf[ParquetCompactWriteSupport])
    GroupWriteSupport.setSchema(ParquetUtils.readSchema(inputPath, fs), job.getConfiguration)

    job.setJobName("compacter")
    job.setInputFormatClass(classOf[CompactGroupInputFormat]);
    job.setOutputFormatClass(classOf[ParquetOutputFormat[Group]])
    job.setMapperClass(classOf[Mapper[Void,Group,Void,Group]])
    job.setJarByClass(classOf[CompactJob])
    job.getConfiguration.set("mapreduce.job.user.classpath.first", "true")
    job.setNumReduceTasks(0)

    if(job.waitForCompletion(true)) 0 else 1
  }
}

object CompactJob {

  def main(args: Array[String]) = {
    args.foreach(println(_))
    val conf = new ParquetCompactConf(args)

    // dodgy, should be using configuration that takes args into account. Also should handle case where path is file
    val numFiles = Hdfs.files(new Path(conf.input()), "*.parquet").run(new Configuration).toOption.getOrElse(throw new Exception("Cannot read input path")).length
    val jobs = (0 to numFiles / conf.count()).map(i => CompactJob(conf, i * conf.count(), numFiles))
    jobs foreach (job => {
      println()
      println("========== Running job ============")
      println(job.conf)
      println(s"index = ${job.index}")
      println(s"total = ${job.total}")
      val result = ToolRunner.run(new Configuration, job, args.tail)
      println(s"job result = $result")
      if (result != 0) { System.exit(result) }
    })
  }
}
