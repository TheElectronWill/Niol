import mill._
import mill.scalalib._
import mill.scalalib.publish._
import coursier.maven.MavenRepository

trait ScalaTest extends TestModule {
  def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.1.0")
  def testFrameworks = Seq("org.scalatest.tools.Framework")
}

object main extends Cross[MainModule]("2.12.10", "2.13.1")

class MainModule(val crossScalaVersion: String) extends CrossScalaModule with PublishModule {
  def artifactName = "niol"
  def publishVersion = "2.0.1"
  def scalacOptions = Seq("-deprecation", "-feature")

  def ivyDeps = Agg(ivy"com.electronwill::more-collections:1.0.0") 
  
  def pomSettings = PomSettings(
    description = "Lightweight network library for Scala",
    organization = "com.electronwill",
    url = "https://github.com/TheElectronWill/Niol",
    licenses = Seq(License.`LGPL-3.0-or-later`),
    versionControl = VersionControl.github("TheElectronWill", "Niol"),
    developers = Seq(
      Developer("TheElectronWill", "Guillaume Raffin", "https://electronwill.com")
    )
  )
  
  object test extends Tests with ScalaTest {}
}
