import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.gson.responseObject
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    val elapsed = measureTimeMillis {

        val assets = listOf("stock", "etf")

        for (asset in assets) {
            val resourceAsText = getResourceAsText(asset + "_tickers.txt")

            val split = resourceAsText?.split("\n")
            if (split != null) {
                val counter = AtomicInteger(0)
                Flowable
                    .fromIterable(split)
                    .parallel(1)
                    .runOn(Schedulers.io())
                    .doOnNext { ticker ->
                        try {
                            val assetClass = if (asset == "stock") "stocks" else "etf"
                            Fuel.get("https://api.nasdaq.com/api/quote/$ticker/dividends?assetclass=$assetClass")
                                .header(
                                    mapOf(
                                        "accept-language" to "en",
                                        "user-agent" to "Chrome/101.0.4951.64 Safari/537.36"
                                    )
                                )
                                .responseObject<Response>
                                { _, _, result ->

                                    val dividentYieldString = result.get().data.yield
                                    if (dividentYieldString != "N/A") {
                                        val dividentYield = dividentYieldString.trim { it == '%' }.toDouble()
                                        if (dividentYield >= 10)
                                            println("$ticker --> $dividentYield%")
                                    }
                                }
                                .get()

                        } catch (e: Exception) {
                            println("$ticker: error on http request")
                        }

                        val count = counter.incrementAndGet()
                        if (count % 100 == 0) {
                            println("$count tickers handled")
                        }
                    }
                    .sequential()
                    .ignoreElements()
                    .blockingAwait()
            }
        }
    }

    println("Finished. Time spend = " + elapsed / 1000 + "s")
}

fun getResourceAsText(path: String): String? =
    object {}.javaClass.getResource(path)?.readText()


class Response(var data: Data)

class Data(var yield: String)