import org.jsoup.Jsoup                                                                               
import org.jsoup.nodes.Document 
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
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
