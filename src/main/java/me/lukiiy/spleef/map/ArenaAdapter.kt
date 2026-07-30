package me.lukiiy.spleef.map

import me.lukiiy.spleef.Spleef
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.Locale.getDefault
import java.util.concurrent.ThreadLocalRandom
import kotlin.Boolean
import kotlin.String
import kotlin.require

class ArenaAdapter {
    companion object {
        val MAPS_DIR = File(Bukkit.getWorldContainer(), "maps")
        private val IGNORED = setOf("uid.dat", "session.lock", "players", "advancements", "stats", "datapacks", "paper-world.yml", "paper-world-defaults.yml")
        private const val WORLD_PREFIX = "spf_"

        fun formatString(input: String?): String = input.orEmpty().removePrefix(WORLD_PREFIX).split("_").filter { it.isNotEmpty() }.joinToString(" ") {
            it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(getDefault()) else c.toString() }
        }
    }

    fun listAvailableMaps(): List<String> = MAPS_DIR.listFiles { f -> f.isDirectory && f.name.startsWith(WORLD_PREFIX) }?.map { it.name }?.sorted() ?: emptyList()

    fun load(folder: File): World? {
        val source = folder.toPath()
        require(Files.exists(source.resolve("level.dat"))) { "No valid world at $folder" }

        val name = "${folder.name}_${ThreadLocalRandom.current().nextLong().toUInt().toString(36)}"
        val instance = Bukkit.getWorldContainer().toPath().resolve(name)

        return runCatching { copy(source, instance) }
            .onFailure { Spleef.getInstance().logger.severe("Failed to copy map ${folder.name}: ${it.message}") }
            .mapCatching { WorldCreator(name).createWorld() ?: throw IllegalStateException() }
            .getOrElse {
                runCatching { delete(instance) }
                null
            }
    }

    fun unload(world: World): Boolean {
        val spawn = Bukkit.getWorlds().first().spawnLocation
        world.players.forEach { it.teleport(spawn) }

        val result = Bukkit.unloadWorld(world, false)
        runCatching { delete(world.worldFolder.toPath()) }.onFailure { Spleef.getInstance().logger.warning("Failed to delete temporary world ${world.name}: ${it.message}") }

        return result
    }

    private fun copy(source: Path, target: Path) {
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) =
                if (ignored(dir)) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE.also { Files.createDirectories(target.resolve(source.relativize(dir))) }

            override fun visitFile(file: Path, attrs: BasicFileAttributes) = FileVisitResult.CONTINUE.also {
                if (!ignored(file)) Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING)
            }
        })
    }

    private fun delete(root: Path) {
        if (!Files.exists(root)) return

        Files.walk(root).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    private fun ignored(path: Path) = IGNORED.contains(path.fileName.toString())
}