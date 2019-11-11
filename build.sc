import mill._
import mill.scalalib._
import mill.scalalib.publish._
import coursier.maven.MavenRepository

trait JUnitTests extends TestModule {// JUnit 5 tests
  def ivyDeps = Agg(ivy"net.aichler:jupiter-interface:0.8.3")
  def testFrameworks = Seq("net.aichler.jupiter.api.JupiterFramework")
  def repositories = super.repositories ++ Seq(
    MavenRepository("https://jcenter.bintray.com")
  )
}

object main extends Cross[MainModule]("2.12.10", "2.13.1")

class MainModule(val crossScalaVersion: String) extends CrossScalaModule with PublishModule {
  def artifactName = "niol"
  def publishVersion = "2.0.1"
  def scalacOptions = Seq("-deprecation", "-feature")

  def ivyDeps = Agg("com.electronwill:more-collection:1.0.0") 
  
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
  
  object test extends Tests with JUnitTests {}
}
