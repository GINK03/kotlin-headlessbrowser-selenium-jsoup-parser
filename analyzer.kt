import org.jsoup.Jsoup                                                                               
import org.jsoup.nodes.Document 
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.io.*

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import kotlin.concurrent.thread

fun wikiepdia() { 
  Files.newDirectoryStream(Paths.get("./out"), "*").map { name ->
    val doc = Jsoup.parse(File(name.toString()), "UTF-8")
    val title = doc.title().replace(" - Wikipedia","")
    doc.select("td").map { x ->
      //スリーサイズのみをマッチする
      if(Regex(""".*\d{1,}\s\-\s\d{1,}\s\-\s\d{1,}\scm.*""").matches(x.text()) ) {
        println("${title} ${x.text()}")
      }
    }
  }
}

data class Cookpad(val title:String, val material:List<String>, val imgUrl:String)
fun cookpad() { 
  val gson = Gson()
  Files.newDirectoryStream(Paths.get("cookpad"), "*").mapIndexed { index, name ->
    if(index%20 == 0) println("now iter ${index}")
    val doc    = Jsoup.parse(File(name.toString()), "UTF-8")
    val recipe = doc.select("div#recipe-main")
    val title    = recipe.select("div#recipe-title").text()
    val material = recipe.select("span.name").map { x -> x.text() }
    val imgUrl   = recipe.select("img.large_photo_clickable").attr("src").split("?").first()
    val cookpad = Cookpad(title, material, imgUrl)
    if (cookpad.title != "" && cookpad.imgUrl != "" && cookpad.material != listOf<String>() ) {
      val json = gson.toJson(cookpad)
      val jsonFilename = "dataset/${imgUrl.split("/").last().replace(".jpg", "")}.json"
      val imgFilename = "dataset/${imgUrl.split("/").last()}"
      if( !File(jsonFilename).exists() || !File(imgFilename).exists() ) {
        thread { 
          Runtime.getRuntime().exec("wget ${imgUrl} -O ${imgFilename}").waitFor()
          PrintWriter(jsonFilename).append(json).close()
          if(index%5==0) {
            println(json) 
          }
        }
      }
    }
  }
}


fun main(args: Array<String>) {
  val MODE = args.getOrElse(0) { "cookpad" }
  when(MODE) { 
    "cookpad" -> cookpad()
  }
}
