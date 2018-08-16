import mill._
import mill.scalalib._
import mill.scalalib.publish._
import coursier.maven.MavenRepository

trait JUnitTesting extends TestModule {// JUnit 5 tests
  def ivyDeps = Agg(ivy"net.aichler:jupiter-interface:0.7.0")
  def testFrameworks = Seq("net.aichler.jupiter.api.JupiterFramework")
  def repositories = super.repositories ++ Seq(
    MavenRepository("https://jcenter.bintray.com")
  )
}

object main extends ScalaModule with JUnitTesting with PublishModule {
  def scalaVersion = "2.12.6"
  def artifactName = "niol"
  def publishVersion = "1.5.4"
  
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
  object test extends Tests with JUnitTesting {}
}
