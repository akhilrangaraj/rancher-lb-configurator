import dispatch._, Defaults._, dispatch.url
import java.util.TimerTask
import java.util.Timer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import java.io.InputStream
import java.io._
import scala.collection.mutable

object RancherLBConfigurator {
  var links = scala.collection.mutable.Map[String,String]()
  var rancherAccessKey : String = ""
  var rancherSecretKey : String = ""
  var rancherServerEndpoint: String = ""
  def getMetadata(endpoint: String) : Option[mutable.Map[String,String]] = {
    val svcName = url(endpoint+"/latest/containers/")
    // get listing of available containers
    val containerListFuture: Future[Option[String]] = Http.default(svcName OK as.String).option
    try {
      val optContainerList: Option[String] = Await.result(containerListFuture, 6000.millis)
      optContainerList match {
        case None => None
        case Some(containerList) => {
          val combinedResults: Array[(Option[String], Option[String])] = containerList.split("\n").map((line: String) => {
            val Array(id, containerName) = line.split("=")
            val labelNameUrl = url(s"${endpoint}/latest/containers/${id}/labels/com.casetext.dns_name")
            val stackNameUrl = url(s"${endpoint}/latest/containers/${id}/labels/io.rancher.stack_service.name")
            val dnsName: Future[Option[String]] = Http.default(labelNameUrl OK as.String).option
            val stackName: Future[Option[String]] = Http.default(stackNameUrl OK as.String).option
            val combinedFuture: Future[(Option[String], Option[String])] = for {
              dnsResult <- dnsName
              stackResult <- stackName
            } yield (dnsResult, stackResult)
            // We can do one at a time
            // Maybe consider doing all at once later

            val combinedResult: (Option[String], Option[String]) = Await.result(combinedFuture, 6000.millis)
            combinedResult
          })
          var dnsMap = mutable.Map[String, String]()
          combinedResults.foreach((optionTuple: (Option[String], Option[String])) => {
            optionTuple._1 match {
              case Some(id) => {
                //dnsMap += (optionTuple._1.get -> optionTuple._2.get)
                // another way to do this...
                // adds the key/value pair (without having to build a whole new
                // map, which `(key -> value)` has to do.
                // I recommend this way:
                dnsMap(optionTuple._1.get) = optionTuple._2.get
              }
              case None => {}
            }
          })
          Option(dnsMap)


        }
      }
    }
    catch {
      case e : java.util.concurrent.TimeoutException => {
        e.printStackTrace()
        println("Timed out while getting metadata")
        None
      }
    }
  }


  //
  def buildYamls(values : scala.collection.mutable.Map[String,String]) :  Boolean = {
    var yamlDoneFuture = Future {
      val rancherComposeStream: InputStream = getClass.getResourceAsStream("/docker/rancher-compose.yml")
      val lines = scala.io.Source.fromInputStream( rancherComposeStream ).getLines
      val rancherComposeWriter = new PrintWriter(new FileOutputStream(new File("/tmp/rancher-compose.yml")))
      for ( line <- lines)  { rancherComposeWriter.write(line); rancherComposeWriter.write("\n") }

      for ( (k,v) <- values) {
        rancherComposeWriter.write(s"      - hostname: ${k}\n")
        rancherComposeWriter.write(s"        path: ''\n")
        rancherComposeWriter.write(s"        priority: 1\n")
        rancherComposeWriter.write(s"        protocol: http\n")
        rancherComposeWriter.write(s"        service: ${v}\n")
        rancherComposeWriter.write(s"        source_port: 80\n")
        rancherComposeWriter.write(s"        target_port: 80\n")
      }
      rancherComposeWriter.flush()
      rancherComposeWriter.close()
      rancherComposeStream.close()
      true
    }
    Await.result(yamlDoneFuture, 6000.millis)

  }
  def rancherComposeShell() : Int = {
    try {
      //rancher-compose  -f docker-compose-prod.yml -r rancher-compose-prod.yml -p ocr-service-${HOST} --url https://rancher.data.casetext.com --access-key $RANCHER_ACCESS --secret-key $RANCHER_SECRET up -d -u  -c --batch-size 1
      sys.process.Process(Seq("rancher-compose", "-p", "LB", "--url", s"${rancherServerEndpoint}", "--access-key", s"${rancherAccessKey}", "--secret-key", s"${rancherSecretKey}", "up", "-c", "-u", "-d"), new java.io.File("/tmp")).!
    }
    catch {
      case e: Exception => {
        println(e)
        1
      }
    }
  }
  def runUpdate(service: String) = {
    println(s"Getting data from metadata service ${service}")
    val metadata = getMetadata(service)
    metadata match {
      case None => println("Something broke")
      case Some(value) => {
        if( value != links) {
          println("Updating configuration")
          if (buildYamls(value)) {
            val rancherReturnValue: Int = rancherComposeShell()
            if (rancherReturnValue != 0)
              println("There was an error running rancher compose!")
            else
              links = value
          }
        }
        else {
          println("No changes found, sleeping")
        }
      }
    }
  }
  implicit def function2TimerTask(f: () => Unit): TimerTask = {
    return new TimerTask {
      def run() = f()
    }
  }
  def main(args: Array[String]) {
    val metadataService = scala.util.Properties.envOrElse("METADATA_SERVICE", "http://rancher-metadata" )
    rancherAccessKey = scala.util.Properties.envOrElse("RANCHER_ACCESS_KEY", "")
    rancherSecretKey = scala.util.Properties.envOrElse("RANCHER_SECRET_KEY", "")
    rancherServerEndpoint = scala.util.Properties.envOrElse("RANCHER_SERVER", "")
    val dockerComposeStream: InputStream = getClass.getResourceAsStream("/docker/docker-compose.yml")
    val lines = scala.io.Source.fromInputStream( dockerComposeStream ).getLines
    val dockerComposeWriter = new PrintWriter(new FileOutputStream(new File("/tmp/docker-compose.yml")))
    for ( line <- lines)  { dockerComposeWriter.write(line); dockerComposeWriter.write("\n") }
    dockerComposeWriter.flush()
    dockerComposeWriter.close()
    dockerComposeStream.close()

    def timerTask() = runUpdate(metadataService)

    val timer = new Timer()
    timer.schedule(function2TimerTask(timerTask),1000, 15000)
  }
}
