import java.util.stream.Stream
import java.util.stream.Collector
import java.time.LocalDateTime
fun main(args: Array<String>) {
  println(LocalDateTime.now())
  
  System.exit(0)

  val stream = (0..1000).toList().parallelStream().map { e ->
    val e2 = e.toDouble()
    val r  = Math.pow(e2, 2.0)
    println(r)
    r
  }
  val r = stream.forEach { r ->
    r
  }
  println(r)
}
