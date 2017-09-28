import kotlin.concurrent.thread
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.*
import org.jsoup.Connection.Response
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
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.phantomjs.PhantomJSDriver
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.OutputType
import org.openqa.selenium.Dimension
import java.util.concurrent.TimeUnit
import java.net.URLEncoder

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

val url_details:MutableMap<String, Data> = mutableMapOf()

fun _writer(url:String, title:String, text:String, outDir:String) {
   val escapeTitle = title.replace("/", "___SLA___")
   val escapeUrl   = url.replace("http://", "")
                        .replace("https://", "")
                        .replace("/", "_")
   var joined = "${escapeUrl}_${escapeTitle}"
   if(joined.length > 50 ) {
     joined = joined.slice(0..50)
   }
   val f = PrintWriter("${outDir}/${joined}")
   f.append(text)
   f.close()
}

fun _save() { 
  val gson = Gson()
  val serialized = url_details.map { kv ->
    val (k, v) = kv
    gson.toJson(v)
  }.toList().joinToString("\n")
  PrintWriter("url_details.json").append(serialized).close()
}
fun _load_conf() { 
  try {
    val gson = Gson()
    val Type = object : TypeToken<Data>() {}.type
    File("url_details.json").readText().toString().split("\n").map { x -> 
      val data:Data  = gson.fromJson<Data>(x, Type)
      val url        = data.key
      url_details[url] = data
    }
  } catch( e: java.io.FileNotFoundException ) {
    println(e)
  } catch( e: java.lang.IllegalStateException ) {
    println(e)
  }
}

fun _parser(url:String, outDir:String):Set<String> { 
  var doc:Document
  var response:Response
  try { 
    doc       = Jsoup.connect(url).timeout(6000).get()
    response  = Jsoup.connect(url).followRedirects(true).execute()
  } catch( e : org.jsoup.HttpStatusException ) {
    println(e)
    return setOf()
  } catch( e : java.lang.IllegalArgumentException ) { 
    println(e)
    return setOf()
  } catch( e : java.net.MalformedURLException ) {
    println(e)
    return setOf()
  } catch( e : java.net.UnknownHostException ) {
    //ホスト不明
    return setOf()
  } catch( e : java.net.SocketException ) { 
    return setOf()
  } catch( e : java.io.IOException ) {
    return setOf()
  }
  if( doc == null || doc.body() == null ) 
    return setOf()
  val btext =  doc.body().text()     
  val title =  doc.title()    
  val urls = doc.select("a[href]").map { link ->
    link.attr("abs:href")
  }
  val saveUrl = response.url().toString()
  _writer(saveUrl, title, doc.html(), outDir)
  return  urls.toSet()
}

fun widthSearch(args:Array<String>) { 
  val outDir           = args.toList().getOrElse(1) { "out" } 
  val TargetDomain     = args.toList().getOrElse(2) { "http://www.yahoo.co.jp" } 
  val concurrent    = args.toList().getOrElse(3) { "250" }
  val concurrentNum    = concurrent.toInt()
  val FilteringDomains = File("conf/filterURLs").readText().replace("\n", " ").split(" ").filter{ x -> x != "" }.toList()
  _parser(TargetDomain, outDir).map { url -> 
    if( FilteringDomains.any{ f -> url.contains(f) } )
      url_details[url] = Data(url, "まだ", System.currentTimeMillis().toLong(), "") 
  }
  _load_conf()
  for(depth in (0..1000) ) {
    val urls:MutableSet<String> = mutableSetOf()
    val threads = url_details.keys.map { url ->
      val th = if( FilteringDomains.any { f -> url.contains(f) } ) {
        val th = Thread { 
          if(url_details[url]!!.state == "まだ") {
            val urlSet = _parser(url, outDir)
            urlSet.map { next ->
              urls.add(next)
            }
            if( urlSet != setOf<String>() ) { 
              println("終わりに更新 : $url")
              url_details[url]!!.state = "終わり"
            }
          }
        }
        th 
      } else { 
        null 
      } 
      th
    }.filter{ th -> th != null }
    threads.map { th -> 
      th!!.start()
      while(true) {
        if(Thread.activeCount() > concurrentNum ) {
          println("now sleeping...")
          Thread.sleep(50)
        } else { 
          break 
        } 
      }
    }
    threads.map { th -> 
      th!!.join() 
    }
    println("now regenerationg url_index...")
    urls.map { url ->
      if( FilteringDomains.any { f -> url.contains(f) } && url_details.get(url) == null ) {
        url_details[url] = Data( url, "まだ", System.currentTimeMillis().toLong() ) 
      }
    }
    _save()
  }
}

fun batchExecutor(args :Array<String>) {
  val filename = args[1]
  val urls = File(filename).readLines().toList()
  urls.map { url -> 
    val decoded = URLDecoder.decode(url)
    println(decoded)
    _parser(decoded, "out")
  }
}

fun imageSeleniumDriver(args: List<String?>) {
  val inputFile = args.getOrElse(1) { "bwh.txt" }
  val outputFile = args.getOrElse(2) { "imgs" } 
  println("このファイルを用います, ${inputFile}")
  println("このディレクトリに保存します, ${outputFile}")
  File("${inputFile}").readLines().map { x -> 
    x.split(" ").first()
  }.map { name ->
    println(name)
    // ディレクトリを作成
    Runtime.getRuntime().exec("mkdir -p ${outputFile}").waitFor()
    // 名前をURLエンコード
    val encoded = URLEncoder.encode(name)
    val driver = PhantomJSDriver()
    driver.manage().window().setSize(Dimension(4096,4160))
    driver.get("https://www.bing.com/images/search?q=${encoded}")
    //すべての画像が描画されるのを待つ
    Thread.sleep(3001)
    val html = driver.getPageSource()
    val doc  = Jsoup.parse(html.toString(), "UTF-8")
    println(doc.title())
    doc.select("img").filter { x -> 
      x.attr("class") == "mimg"
    }.map { x ->
      println(x)
      val data_bm = x.attr("data-bm")
      val src     = x.attr("src")
      Runtime.getRuntime().exec("wget ${src} -O ${outputFile}/${name}_${data_bm}.jpg").waitFor()
    }
    val data = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
    Files.write(Paths.get("${outputFile}/${name}.png"), data)
    //もう必要ないので、driverをquitする
    driver.quit()
  }
}

fun imageAdSeleniumCollector(args: List<String?>) {
  val inputFile = args.getOrElse(1) { "bwh.txt" }
  val outputFile = args.getOrElse(2) { "imgs" } 
  println("このファイルを用います, ${inputFile}")
  println("このディレクトリに保存します, ${outputFile}")
  // ディレクトリを作成
  Runtime.getRuntime().exec("mkdir -p ${outputFile}").waitFor()

  // URLコンテナ
  val URL = mutableMapOf<String, String>()
  //val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36"
  //System.setProperty("phantomjs.page.settings.userAgent", userAgent)
  //System.setProperty("phantomjs.binary.path", "/usr/bin/phantomjs")
  val chromeOptions = ChromeOptions()
  chromeOptions.setBinary("/usr/bin/google-chrome");
  chromeOptions.addArguments("--headless");
  System.setProperty("webdriver.chrome.driver","/usr/local/bin/chromedriver")
  //val driver = PhantomJSDriver()
  val driver = ChromeDriver(chromeOptions)
  driver.manage().window().setSize(Dimension(4024,4024))
  println("AAAA")
  val masterUrl = "http://jin115.com/archives/52193186.html"
  URL[masterUrl] = "noscraped"

  loop@while(true) {
    val urls = URL.toList().filter { it.second == "noscraped" }.map { it.first }
    if( urls.size == 0 ) break@loop

    for( url in urls ) {
      println("now scaning url is ${url}... please wait...")
      driver.get( url )
      println("BBB")

      //すべての画像が描画されるのを待つ
      Thread.sleep(1000)
      val html = driver.getPageSource()
      val doc  = Jsoup.parse(html.toString(), "UTF-8")
      println(doc.title())
      
      for( ahref in doc.select("a[href]") ) { 
        val newURL = ahref.attr("abs:href")
        println(newURL )
        if ( !!newURL.contains("http://jin115.com") )
          continue
        if ( URL.get(newURL) == null )  
          URL[newURL] = "noscraped"
      }
      for( iframe in driver.findElements(By.tagName("iframe")) ) {
        try {
          val iframeD = driver.switchTo().frame( iframe )
          Thread.sleep(500)
          val iframeHtml = iframeD.getPageSource()
          val iframeDoc  = Jsoup.parse(iframeHtml.toString(), "UTF-8")
          iframeDoc.select("img").filter { x -> 
            // this flag mean google adword image cratives, im.c.yimg.jp
            x.attr("src").contains("im.c.yimg.jp") 
          }.map { x ->
            println(x)
            val src = x.attr("src")
            val saveName = src.split("/").last()
            Runtime.getRuntime().exec("wget ${src} -O ${outputFile}/${saveName}").waitFor()
          }
          // Google Adwordsのように複数回ネストしてアドが入っている場合
          iframeDoc.select("iframe").filter {
            it.attr("id").contains("google")
          }.map {
            // ここは入れ子のオペレーション
            for( iframe2 in driver.findElements(By.tagName("iframe")) ) {
              val iframeD2 = driver.switchTo().frame( iframe2 )
              val iframeHtml2 = iframeD2.getPageSource()
              val iframeDoc2  = Jsoup.parse(iframeHtml2.toString(), "UTF-8")
              iframeDoc2.select("img").filter { x -> 
                // this flag mean google adword image cratives, im.c.yimg.jp
                x.attr("src").contains("googleads") 
              }.map { x ->
                println(x)
                val src = x.attr("src")
                val saveName = src.split("/").last()
                Runtime.getRuntime().exec("wget ${src} -O ${outputFile}/${saveName}").waitFor()
              }
              driver.switchTo().defaultContent()
            }
          }
          // recover to master page
          driver.switchTo().defaultContent()
        } catch( e : java.lang.Exception ) {
          //println("Exception as ${e}")
          println("Exception occured ${iframe}")
        }
      }
      URL[url] = "finished"
    }
  }
  // go to loop
  val data = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
  Files.write(Paths.get("${outputFile}.png"), data)
  //もう必要ないので、driverをquitする
  driver.quit()
}


fun pawooHunterDriver(args:List<String>, mode:Int ){ 
  val num = args.filter { x -> x.contains("th=") }.map { x -> x.split("=").last() }?.last()?.toInt() ?: 3
  val (targetInstance, conf, outputFile) = when(mode) { 
    1 -> Triple("https://pawoo.net", "~/private_configs/pawoo_mail_pass", "pawoo")
    else -> Triple("https://mstdn.jp" , "~/private_configs/mstdnjp_mail_pass", "mstdnjp")
  }
  for( instance in (1..num) ) { 
    thread { pawooHunter(instance, args, targetInstance, conf, outputFile) } 
    Thread.sleep(500)
  }
}

fun main(args: Array<String>) {
  val Mode  = args.toList().getOrElse(0) {  
    println("モードを指定してくだい...")
    System.exit(0)
  }
  when(Mode) { 
    "widthSearch" -> widthSearch(args)
    "batch"       -> batchExecutor(args)
    "image"       -> imageSeleniumDriver(args.toList())
    "pawooHunter" -> pawooHunterDriver(args.toList(), 1)
    "mstdnHunter" -> pawooHunterDriver(args.toList(), 2)
    "nocturne"    -> nocturneHunter()
    "imageAd" -> imageAdSeleniumCollector(args.toList())
  }
}
