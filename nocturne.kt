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
import org.openqa.selenium.phantomjs.PhantomJSDriverService
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


data class Noct(val url:String = "", var html:String = "", var parsed:Int = -1)


fun nocturneLoader(conf:String):Pair<String, String> { 
  val home = System.getenv("HOME")
  val (username, password) = File("${conf}".replace("~",home)).readText().split(" ")
  return Pair(username, password)
}


fun nocturneHunter() {
  val nocts:MutableSet<Noct> = mutableSetOf()
  
  println("ノクターンノベルズをスクレイピングします")
  val DesireCaps = DesiredCapabilities()
  DesireCaps.setCapability(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY, "/usr/local/bin/phantomjs");

  val driver = PhantomJSDriver(DesireCaps)
  val outputFile = "nocturne"
  driver.get("http://noc.syosetu.com/top/top/")
  driver.manage().window().setSize(Dimension(920, 1080))
  Thread.sleep(200)
  driver.findElement(By.id("yes18")).click()
  Thread.sleep(200)
  val data = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
  Files.write(Paths.get("data/nocturne/test.png"), data)
  println("ノクターンノベルズをスクレイピングします")
  driver.findElements(By.tagName("a")).map { x ->
    val url = x.getAttribute("href") 
    println(url)
    if( url.contains("http://noc.syosetu.com") || url.contains("http://novel18.syosetu.com") ) {
      if( ! nocts.any { x -> x.url == url } ) {
        val noct = Noct(url, "", -1 )
        nocts.add(noct)
      }
    }
  }
  println(nocts)
  val cookies = driver.manage().getCookies()
  println(cookies)
  cookies.map { x -> 
    println(x)
  }
  //System.exit(0)
  //Thread.sleep(200)
  while( ! nocts.all { x -> x.parsed == 1 }  ) { 
    val urls:MutableSet<String> = mutableSetOf()
    val size = nocts.size
    nocts.mapIndexed { i,x ->
      try {
        val soup = Jsoup.connect(x.url).cookie("over18", "yes;").get()
        soup.select("a").map { x ->
          //println(x.attr("abs:href"))
          urls.add( x.attr("abs:href") )
        }
        println("${i}/${size} ${x.url}")
        val html = soup.html()
        val saveName = x.url.replace("http://", "").replace("/", "_")
        PrintWriter("data/nocturne/${saveName}.html").append(html).close()
        x.parsed = 1
      } catch (e : java.io.IOException ) {
        null
      }
    }
    // 子ノードを更新する
    urls.map { url ->
      println(url)
      if( url.contains("http://noc.syosetu.com") || url.contains("http://novel18.syosetu.com") ) {
        if( ! nocts.any { x -> x.url == url } ) {
          val noct = Noct(url, "", -1 )
          nocts.add(noct)
        }
      }
    }
  }
  //もう必要ないので、driverをquitする
  driver.quit()
}
