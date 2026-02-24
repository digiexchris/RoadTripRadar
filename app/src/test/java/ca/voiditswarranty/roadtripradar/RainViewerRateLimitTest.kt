package ca.voiditswarranty.roadtripradar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Integration tests that hit the real RainViewer API to measure rate-limit behavior.
 *
 * These tests require internet access and should NOT run in CI.
 * Run them manually from Android Studio to diagnose tile-loading performance.
 *
 * To run only these tests:
 *   ./gradlew test --tests "ca.voiditswarranty.roadtripradar.RainViewerRateLimitTest"
 */
class RainViewerRateLimitTest {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private lateinit var framePaths: List<String>

    // Representative tile coordinate (low zoom = always has data)
    private val tileZ = 3
    private val tileX = 3
    private val tileY = 3

    @Before
    fun fetchMetadata() {
        val request = Request.Builder()
            .url("https://api.rainviewer.com/public/weather-maps.json")
            .build()

        val response = client.newCall(request).execute()
        assert(response.isSuccessful) { "Metadata fetch failed: ${response.code}" }

        val json = Json.parseToJsonElement(response.body!!.string()).jsonObject
        val past = json["radar"]!!.jsonObject["past"]!!.jsonArray
        framePaths = past.takeLast(10).map { it.jsonObject["path"]!!.jsonPrimitive.content }

        println("=== RainViewer Metadata ===")
        println("Total past frames available: ${past.size}")
        println("Using last ${framePaths.size} frames")
        framePaths.forEachIndexed { i, path -> println("  Frame $i: $path") }
        println()
    }

    /**
     * Test A: Fire one tile request per frame (10 requests) in parallel.
     * Logs HTTP status codes and per-request latency.
     * Fails if ANY request returns a non-2xx status code.
     */
    @Test
    fun `parallel tile requests for 10 frames measure latency and status codes`() {
        data class Result(val frame: Int, val code: Int, val latencyMs: Long, val error: String?)

        val results = ConcurrentHashMap<Int, Result>()
        val latch = CountDownLatch(framePaths.size)

        framePaths.forEachIndexed { index, path ->
            Thread {
                try {
                    val url = "https://tilecache.rainviewer.com$path/512/$tileZ/$tileX/$tileY/2/1_1.png"
                    val request = Request.Builder().url(url).build()
                    val start = System.nanoTime()
                    val response = client.newCall(request).execute()
                    val latencyMs = (System.nanoTime() - start) / 1_000_000
                    response.body?.close()
                    results[index] = Result(index, response.code, latencyMs, null)
                } catch (e: Exception) {
                    results[index] = Result(index, -1, 0, e.message)
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        latch.await(60, TimeUnit.SECONDS)

        println("=== Test A: Parallel Tile Requests (1 tile × 10 frames) ===")
        println(String.format("%-6s  %-6s  %-10s  %s", "Frame", "Code", "Latency", "Error"))
        println("-".repeat(50))
        (0 until framePaths.size).forEach { i ->
            val r = results[i]!!
            println(String.format("%-6d  %-6d  %-10s  %s", r.frame, r.code, "${r.latencyMs}ms", r.error ?: ""))
        }

        val codes = results.values.map { it.code }
        val successful = codes.count { it in 200..299 }
        val rateLimited = codes.count { it == 429 }
        val failed = codes.count { it !in 200..299 }
        println()
        println("2xx OK: $successful  |  429 Rate Limited: $rateLimited  |  Other non-2xx: ${failed - rateLimited}")
        if (failed > 0) {
            val breakdown = codes.filter { it !in 200..299 }.groupingBy { it }.eachCount()
            println("Failure breakdown: $breakdown")
        }
        println()

        assert(failed == 0) {
            val breakdown = codes.filter { it !in 200..299 }.groupingBy { it }.eachCount()
            "Got $failed non-successful responses out of ${framePaths.size} parallel requests: $breakdown"
        }
    }

    /**
     * Test B: Burst requests — multiple tile coordinates per frame (4 tiles × 10 frames = 40).
     * Simulates what MapLibre does when the viewport covers multiple tiles.
     * Fails if ANY request returns a non-2xx status code.
     */
    @Test
    fun `burst tile requests detect 429 responses`() {
        // 4 tile coordinates at zoom 3 that are likely to have radar data (North America)
        val tileCoords = listOf(
            Triple(3, 1, 3), // z, x, y
            Triple(3, 2, 3),
            Triple(3, 3, 3),
            Triple(3, 2, 2),
        )

        data class Result(val frame: Int, val tile: String, val code: Int, val latencyMs: Long, val error: String?)

        val totalRequests = framePaths.size * tileCoords.size
        val results = ConcurrentHashMap<String, Result>()
        val latch = CountDownLatch(totalRequests)

        framePaths.forEachIndexed { frameIdx, path ->
            tileCoords.forEachIndexed { tileIdx, (z, x, y) ->
                Thread {
                    val key = "f${frameIdx}_t${tileIdx}"
                    try {
                        val url = "https://tilecache.rainviewer.com$path/512/$z/$x/$y/2/1_1.png"
                        val request = Request.Builder().url(url).build()
                        val start = System.nanoTime()
                        val response = client.newCall(request).execute()
                        val latencyMs = (System.nanoTime() - start) / 1_000_000
                        response.body?.close()
                        results[key] = Result(frameIdx, "$z/$x/$y", response.code, latencyMs, null)
                    } catch (e: Exception) {
                        results[key] = Result(frameIdx, "$z/$x/$y", -1, 0, e.message)
                    } finally {
                        latch.countDown()
                    }
                }.start()
            }
        }

        latch.await(60, TimeUnit.SECONDS)

        println("=== Test B: Burst Tile Requests (4 tiles × 10 frames = $totalRequests requests) ===")
        println(String.format("%-6s  %-10s  %-6s  %-10s  %s", "Frame", "Tile", "Code", "Latency", "Error"))
        println("-".repeat(60))
        results.entries.sortedBy { it.key }.forEach { (_, r) ->
            println(String.format("%-6d  %-10s  %-6d  %-10s  %s", r.frame, r.tile, r.code, "${r.latencyMs}ms", r.error ?: ""))
        }

        val codes = results.values.map { it.code }
        val successful = codes.count { it in 200..299 }
        val rateLimited = codes.count { it == 429 }
        val failed = codes.count { it !in 200..299 }
        val successfulResults = results.values.filter { it.code in 200..299 }
        val avgLatency = if (successfulResults.isNotEmpty()) successfulResults.map { it.latencyMs }.average() else 0.0
        val maxLatency = successfulResults.maxOfOrNull { it.latencyMs } ?: 0
        println()
        println("2xx OK: $successful  |  429 Rate Limited: $rateLimited  |  Other non-2xx: ${failed - rateLimited}")
        println("Avg latency: ${String.format("%.0f", avgLatency)}ms  |  Max latency: ${maxLatency}ms")
        if (failed > 0) {
            val breakdown = codes.filter { it !in 200..299 }.groupingBy { it }.eachCount()
            println("Failure breakdown: $breakdown")
        }
        println()

        assert(failed == 0) {
            val breakdown = codes.filter { it !in 200..299 }.groupingBy { it }.eachCount()
            "Got $failed non-successful responses out of $totalRequests burst requests: $breakdown"
        }
    }

    /**
     * Test C: Compare sequential vs parallel timing for the same 10 tile requests.
     * If per-request latency increases significantly under parallelism, it suggests
     * server-side throttling even without explicit 429 responses.
     */
    @Test
    fun `sequential vs parallel timing comparison`() {
        // --- Sequential ---
        val seqLatencies = mutableListOf<Long>()
        val seqCodes = mutableListOf<Int>()
        val seqStart = System.nanoTime()

        framePaths.forEach { path ->
            val url = "https://tilecache.rainviewer.com$path/512/$tileZ/$tileX/$tileY/2/1_1.png"
            val request = Request.Builder().url(url).build()
            val start = System.nanoTime()
            val response = client.newCall(request).execute()
            val latencyMs = (System.nanoTime() - start) / 1_000_000
            response.body?.close()
            seqLatencies.add(latencyMs)
            seqCodes.add(response.code)
        }

        val seqTotalMs = (System.nanoTime() - seqStart) / 1_000_000

        // --- Parallel ---
        data class ParResult(val code: Int, val latencyMs: Long)

        val parResults = ConcurrentHashMap<Int, ParResult>()
        val latch = CountDownLatch(framePaths.size)
        val parStart = System.nanoTime()

        framePaths.forEachIndexed { index, path ->
            Thread {
                try {
                    val url = "https://tilecache.rainviewer.com$path/512/$tileZ/$tileX/$tileY/2/1_1.png"
                    val request = Request.Builder().url(url).build()
                    val start = System.nanoTime()
                    val response = client.newCall(request).execute()
                    val latencyMs = (System.nanoTime() - start) / 1_000_000
                    response.body?.close()
                    parResults[index] = ParResult(response.code, latencyMs)
                } catch (_: Exception) {
                    parResults[index] = ParResult(-1, 0)
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        latch.await(60, TimeUnit.SECONDS)

        val parTotalMs = (System.nanoTime() - parStart) / 1_000_000
        val parLatencies = (0 until framePaths.size).map { parResults[it]!!.latencyMs }

        println("=== Test C: Sequential vs Parallel Timing ===")
        println()
        println("Sequential:")
        println(String.format("  Total: %dms  |  Avg: %.0fms  |  Max: %dms",
            seqTotalMs, seqLatencies.average(), seqLatencies.max()))
        println("  Per-request: ${seqLatencies.joinToString(", ") { "${it}ms" }}")
        println("  Status codes: ${seqCodes.joinToString(", ")}")
        println()
        println("Parallel:")
        println(String.format("  Total: %dms  |  Avg: %.0fms  |  Max: %dms",
            parTotalMs, parLatencies.average(), parLatencies.max()))
        println("  Per-request: ${parLatencies.joinToString(", ") { "${it}ms" }}")
        println("  Status codes: ${(0 until framePaths.size).map { parResults[it]!!.code }.joinToString(", ")}")
        println()

        val seqAvg = seqLatencies.average()
        val parAvg = parLatencies.average()
        val slowdownFactor = if (seqAvg > 0) parAvg / seqAvg else 0.0
        println(String.format("Parallel avg latency is %.1fx the sequential avg latency", slowdownFactor))

        if (slowdownFactor > 2.0) {
            println("WARNING: Significant per-request slowdown under parallelism suggests server-side throttling")
        }

        // Check for non-2xx in both batches
        val seqFailed = seqCodes.count { it !in 200..299 }
        val parFailed = (0 until framePaths.size).count { parResults[it]!!.code !in 200..299 }
        println("Sequential non-2xx: $seqFailed  |  Parallel non-2xx: $parFailed")
        if (seqFailed > 0) {
            val breakdown = seqCodes.filter { it !in 200..299 }.groupingBy { it }.eachCount()
            println("Sequential failure breakdown: $breakdown")
        }
        if (parFailed > 0) {
            val breakdown = (0 until framePaths.size).map { parResults[it]!!.code }.filter { it !in 200..299 }.groupingBy { it }.eachCount()
            println("Parallel failure breakdown: $breakdown")
        }
        println()

        assert(seqFailed + parFailed == 0) {
            "Got $seqFailed sequential + $parFailed parallel non-successful responses"
        }
    }
}
