package me.lukiiy.spleef.map

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.structure.Structure
import org.bukkit.util.BlockVector

object Platforms {
    @JvmStatic
    @Deprecated("Platform trimming is no longer a feature")
    fun trim(structure: Structure): PlatformTemplate? {
        if (structure.paletteCount == 0) return null

        return PlatformTemplate(structure, BlockVector(0, 0, 0), structure.size)
    }

    @JvmStatic
    fun createPlacement(world: World, spawn: Location, template: PlatformTemplate, topY: Int): PlacedPlatform {
        val trimmedMaxY = template.trimmedMin.blockY + template.trimmedSize.blockY - 1
        val originX = spawn.blockX - template.trimmedMin.blockX - template.trimmedSize.blockX / 2
        val originZ = spawn.blockZ - template.trimmedMin.blockZ - template.trimmedSize.blockZ / 2
        val originY = topY - trimmedMaxY

        return PlacedPlatform(template, Location(world, originX.toDouble(), originY.toDouble(), originZ.toDouble()), topY)
    }

    @JvmStatic fun highest(platforms: List<PlacedPlatform>): PlacedPlatform = platforms.stream().max(Comparator.comparingInt(PlacedPlatform::topY)).orElseThrow()
    @JvmStatic fun lowest(platforms: List<PlacedPlatform>): PlacedPlatform = platforms.stream().min(Comparator.comparingInt(PlacedPlatform::topY)).orElseThrow()

    @JvmStatic fun minX(platform: PlacedPlatform): Int = platform.origin.blockX + platform.template.trimmedMin.blockX
    @JvmStatic fun maxX(platform: PlacedPlatform): Int = minX(platform) + platform.template.trimmedSize.blockX - 1
    @JvmStatic fun minZ(platform: PlacedPlatform): Int = platform.origin.blockZ + platform.template.trimmedMin.blockZ
    @JvmStatic fun maxZ(platform: PlacedPlatform): Int = minZ(platform) + platform.template.trimmedSize.blockZ - 1

    @JvmRecord
    data class PlatformTemplate(@JvmField val structure: Structure, @JvmField val trimmedMin: BlockVector, @JvmField val trimmedSize: BlockVector)

    @JvmRecord
    data class PlacedPlatform(@JvmField val template: PlatformTemplate, @JvmField val origin: Location, @JvmField val topY: Int)
}