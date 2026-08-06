package me.lukiiy.spleef.map

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.structure.Structure
import org.bukkit.util.BlockVector

object Platforms {
    @JvmStatic
    fun trim(structure: Structure): PlatformTemplate? {
        if (structure.paletteCount == 0) return null

        val palette = structure.palettes.first()

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        // offset stuff
        for (state in palette.blocks) {
            if (state.type.isAir) continue

            val pos = state.location

            minX = minOf(minX, pos.blockX)
            minY = minOf(minY, pos.blockY)
            minZ = minOf(minZ, pos.blockZ)
            maxX = maxOf(maxX, pos.blockX)
            maxY = maxOf(maxY, pos.blockY)
            maxZ = maxOf(maxZ, pos.blockZ)
        }

        if (minX > maxX) return null

        val trimmedMin = BlockVector(minX, minY, minZ)
        val trimmedSize = BlockVector(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1)

        return PlatformTemplate(structure, trimmedMin, trimmedSize)
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