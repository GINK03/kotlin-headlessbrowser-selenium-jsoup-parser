import kotlin.concurrent.thread
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.file.Files
import java.nio.file.Paths
import java.io.*
import java.net.URLDecoder
import java.time.LocalDateTime

//ここからselenium
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.phantomjs.PhantomJSDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.OutputType
import org.openqa.selenium.Dimension
import org.openqa.selenium.remote.DesiredCapabilities
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import java.net.URL
import org.openqa.selenium.support.ui.ExpectedConditions
import java.time.Instant

// ここからgson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ここからs3
import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.amazonaws.services.s3.model.ListObjectsV2Result

data class Oneshot(var name:String = "", var id:String = "", var context:String = "", var increment:Long = 0, var parseTime:Long = 0)


fun passwordLoader(conf:String):Pair<String, String> { 
  val home = System.getenv("HOME")
  val (username, password) = File("${conf}".replace("~",home)).readText().split(" ")
  return Pair(username, password)
}

fun s3syncer(outputFile:String, key:String) { 
  thread { 
    val s3client:AmazonS3     = AmazonS3Client(ProfileCredentialsProvider())
    val file                  = File("pawoo/${key}.json")
    //println("try to put aws ${key}")
    try { 
      s3client.putObject(PutObjectRequest("pawoo-hunter", "${key}.json", file))
    } catch(e: com.amazonaws.services.s3.model.AmazonS3Exception) {
    }
  }
}

fun chiseinoKatamari(key:String):String { 
  val chisei = key.replace("https:", "")
                  .replace("http:", "")
                  .replace("/", "__SLA__")
  return chisei
}

fun pawooHunter(instance:Int, args: List<String?>, targetInstance:String, conf:String, outputFile:String) {
  System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver")
  val capability = DesiredCapabilities.firefox()
  capability.setCapability("marionette", true)
  capability.setBrowserName("firefox")
  println("call ${targetInstance}")
  val options = ChromeOptions()
  options.setBinary("/usr/bin/google-chrome")
  val driver = ChromeDriver(options)
  driver.manage().window().setSize(Dimension(920, 1080))
  driver.get(targetInstance)
  driver.findElement(By.className("webapp-btn")).click()
  // fill email and password
  val (username, password) = passwordLoader(conf)
  Thread.sleep(200)
  driver.findElement(By.id("user_email")).sendKeys(username)
  driver.findElement(By.id("user_password")).sendKeys(password)
  val data = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
  Files.write(Paths.get("${outputFile}/test.png"), data)
  try{ driver.findElement(By.name("button")).click() } catch(e: org.openqa.selenium.NoSuchElementException){ null}
  println(driver.getCurrentUrl())

  driver.get("${targetInstance}/web/timelines/public")
  Thread.sleep(500)
  println(driver.getCurrentUrl())
  val gson = Gson()
  while(true) {
    try { 
      //Files.write(Paths.get("${outputFile}/test_${Instant.now().toEpochMilli().toString()}.png"), 
      //            (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)  )
      driver.findElements(By.className("status")).mapIndexed { index, status -> 
        val oneshot = Oneshot()
        oneshot.context = status.findElement(By.className("status__content")).getText()
        status.findElements(By.cssSelector("a")).filter { a -> 
          a.getText() != ""
        }.map{ a ->
          if( a.getText().contains("@") ) {
            val ents = a.getText().split(" ")
            val name = ents.slice(0..ents.size - 1).joinToString().split(',')[0]
            val id   = ents.last()
            oneshot.name = name
            oneshot.id   = id
          }
          if ( a.getText().contains("前") )  {
            try { 
              val increment = a.getAttribute("href").split("/").last()
              oneshot.increment = try{ increment.toLong() }catch( e :java.lang.NumberFormatException) { -1 } 
            } catch (e: java.lang.IllegalArgumentException) {
              // a.getAtributeがnullを返すことがあるっぽい
              // oneshot.incrementには-1を入れておく
              oneshot.increment = -1
            }
          }
        }
        oneshot.parseTime = Instant.now().toEpochMilli().toLong() 
        oneshot.context   = oneshot.context
        // uniqであるはずのincrementが0のときがあってその時は、-をつけたparseTimeにする
        if( oneshot.increment == 0.toLong() ) oneshot.increment = -1.toLong() * oneshot.parseTime 
        
        val value = gson.toJson(oneshot)
        // たまにIDにURLが入るので削除
        val key   = chiseinoKatamari("${oneshot.id}_${oneshot.increment}")
        try { 
          PrintWriter("${outputFile}/${key}.json").append(value).close()
        } catch(e: java.io.IOException) { 
          //ファイル名が長すぎることがあるらしい（保存を諦める）
        }
        // context中のmediaリンクがある場合には、別途保存
        // mstdn.jpのフォーマット形式
        oneshot.context.split(" ").filter { term ->
          term.contains("http") && term.contains("media")
        }.map { url ->
          Runtime.getRuntime().exec("wget ${url} -O ${outputFile}/${key}.png").waitFor()
        }
        // pixiv社のインスタンス形式
        status.findElements(By.cssSelector("a")).filter { a ->
          a.getAttribute("href") != null
        }.filter { a ->
          a.getAttribute("href").contains("/media/")
        }.map { a ->
          val url = a.getAttribute("href")
          Runtime.getRuntime().exec("wget ${url} -O ${outputFile}/${key}.png").waitFor()
          println("mediaデータを含んでいます, ${url}")
        }
        if( args.contains("s3sync") ){ 
          s3syncer(outputFile, key)
        }
        println("instance=${instance} ${index}, ${value}")
      }
      println("一巡が終わりました...")
      if( Instant.now().getEpochSecond()%1 == 0.toLong() ) { 
        driver.navigate().refresh()
        Thread.sleep(1000)
      }
    } catch (e: org.openqa.selenium.StaleElementReferenceException)  { 
      //要はAjaxで内容が書き換わってしまうので、参照できなくなったときに発生する
      driver.navigate().refresh()
      Thread.sleep(1000)
    }
  }
  //もう必要ないので、driverをquitする
  driver.quit()
}
