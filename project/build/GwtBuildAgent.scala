import sbt._

class GwtBuildAgent(info : ProjectInfo) extends DefaultProject(info) {
  override def fork = forkRun
}
