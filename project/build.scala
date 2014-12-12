import sbt._
import Keys._

import au.com.cba.omnia.uniform.dependency.UniformDependencyPlugin._
import au.com.cba.omnia.uniform.core.standard.StandardProjectPlugin._

object build extends Build {
  type Sett = Def.Setting[_]

  val parquetVersion     = "1.2.5-cdh4.6.0-p485"

  lazy val standardSettings: Seq[Sett] =
    Defaults.defaultSettings ++
    uniformDependencySettings

  lazy val all = Project(
    id = "all"
  , base = file(".")
  , settings =
       Defaults.defaultSettings ++ uniformDependencySettings
         ++ uniform.project("parquetjoiner", "au.com.cba.omnia")
         ++ Seq(
          libraryDependencies := depend.hadoop()
          ++ Seq(
                "com.twitter" % "parquet-common" % parquetVersion % "provided",
                "com.twitter" % "parquet-encoding" % parquetVersion % "provided",
                "com.twitter" % "parquet-column" % parquetVersion % "provided",
                "com.twitter" % "parquet-hadoop" % parquetVersion % "provided"
             )
         )
  )
}
