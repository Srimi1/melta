package com.melta.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

/**
 * Gathers every Pokémon sprite at build time and packs it into the APK as assets.
 *
 * The images are downloaded into the Gradle user cache (so rebuilds are instant), checked to be
 * valid PNGs, then copied to `outputDir/sprites/[variant/]{id}.png`. Nothing is committed to git.
 * The app trims and scales each sprite at runtime, so the originals are packed untouched,
 * which keeps the pack small (about 2 MB for normal + shiny).
 */
@CacheableTask
abstract class SpritePackTask : DefaultTask() {

    /** Highest national dex number to include. */
    @get:Input
    abstract val maxId: Property<Int>

    /** Base URL of the PokeAPI sprite folder, without a trailing slash. */
    @get:Input
    abstract val baseUrl: Property<String>

    /** Sub folders to pack, "" for normal sprites and "shiny" for shiny ones. */
    @get:Input
    abstract val variants: ListProperty<String>

    /** Raw download cache shared between builds and branches. Not part of the cache key. */
    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun pack() {
        val base = baseUrl.get().trimEnd('/')
        val cache = cacheDir.get().asFile.apply { mkdirs() }
        val out = outputDir.get().asFile.resolve("sprites").apply {
            deleteRecursively()
            mkdirs()
        }
        val client = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build()

        val jobs = variants.get().flatMap { v -> (1..maxId.get()).map { id -> v to id } }
        val failed = java.util.Collections.synchronizedList(mutableListOf<String>())
        val done = AtomicInteger()
        val pool = Executors.newFixedThreadPool(THREADS)
        for ((variant, id) in jobs) {
            pool.execute {
                val rel = if (variant.isEmpty()) "$id.png" else "$variant/$id.png"
                val cached = cache.resolve(rel)
                val ok = (cached.isValidPng() || download(client, "$base/$rel", cached)) &&
                    runCatching { cached.copyTo(out.resolve(rel).apply { parentFile.mkdirs() }, overwrite = true) }.isSuccess
                if (!ok) failed += rel
                val n = done.incrementAndGet()
                if (n % 250 == 0) logger.lifecycle("Sprite pack: $n / ${jobs.size}")
            }
        }
        pool.shutdown()
        if (!pool.awaitTermination(30, TimeUnit.MINUTES)) throw GradleException("Sprite pack timed out")

        if ("25.png" in failed) {
            throw GradleException("Sprite pack is missing Pikachu (#25). Check your connection or build with -Pmelta.skipSpritePack=true")
        }
        if (failed.size > jobs.size / 100) {
            throw GradleException(
                "Sprite pack failed for ${failed.size} of ${jobs.size} sprites (for example ${failed.take(5)}). " +
                    "Check your connection or build with -Pmelta.skipSpritePack=true",
            )
        }
        if (failed.isNotEmpty()) logger.warn("Sprite pack: ${failed.size} sprites missing, the app will download them on demand: $failed")
        logger.lifecycle("Sprite pack: ${jobs.size - failed.size} sprites packed")
    }

    private fun download(client: HttpClient, url: String, target: File): Boolean {
        target.parentFile.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        var delayMs = 1_000L
        repeat(ATTEMPTS) {
            try {
                val request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Melta-build")
                    .GET()
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
                when (response.statusCode()) {
                    200 -> if (tmp.isValidPng() && tmp.renameTo(target)) return true
                    404 -> return false // this sprite simply does not exist
                }
            } catch (e: IOException) {
                logger.info("Sprite pack: retrying $url (${e.message})")
            }
            tmp.delete()
            Thread.sleep(delayMs) // GitHub rate limits bursts with 429, so back off
            delayMs *= 2
        }
        return false
    }

    private fun File.isValidPng(): Boolean =
        isFile && length() > 0 && runCatching { ImageIO.read(this) != null }.getOrDefault(false)

    private companion object {
        const val THREADS = 8
        const val ATTEMPTS = 5
    }
}
