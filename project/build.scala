import sbt._
import Keys._

import sbtassembly.Plugin._, AssemblyKeys._

import au.com.cba.omnia.uniform.dependency.UniformDependencyPlugin._
import au.com.cba.omnia.uniform.core.standard.StandardProjectPlugin._
import au.com.cba.omnia.uniform.assembly.UniformAssemblyPlugin._
import au.com.cba.omnia.uniform.core.version.UniqueVersionPlugin._


object build extends Build {
  type Sett = Def.Setting[_]

  val parquetVersion     = "1.2.5-cdh4.6.0-p485"

  uniformAssemblySettings

  lazy val all = Project(
    id = "all"
  , base = file(".")
  , settings =
       Defaults.defaultSettings ++ uniformDependencySettings
         ++ uniform.project("parquetjoiner", "au.com.cba.omnia")
         ++ uniform.ghsettings
         ++ Seq[  Sett](
          libraryDependencies ++= Seq(
                "com.twitter"       % "parquet-common"   % parquetVersion       % "provided",
                "com.twitter"       % "parquet-encoding" % parquetVersion       % "provided",
                "com.twitter"       % "parquet-column"   % parquetVersion       % "provided",
                "com.twitter"       % "parquet-hadoop"   % parquetVersion       % "provided",
                "org.apache.hadoop" % "hadoop-client"    % "2.0.0-mr1-cdh4.6.0" % "provided",
                "org.apache.hadoop" % "hadoop-core"      % "2.0.0-mr1-cdh4.6.0" % "provided",
                "org.rogach"       %% "scallop"          % "0.9.5" 
             )
         )
  )
}
