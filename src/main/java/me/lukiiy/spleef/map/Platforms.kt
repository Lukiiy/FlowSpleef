package me.lukiiy.spleef.map

import org.bukkit.Location
import org.bukkit.structure.Palette
import org.bukkit.structure.Structure
import org.bukkit.util.BlockVector
import kotlin.math.max
import kotlin.math.min

object Platforms {
    @JvmStatic
    fun trim(structure: Structure): PlatformTemplate? {
        if (structure.paletteCount == 0) return null
        val palette: Palette = structure.palettes.first()

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        // offset stuff
        for (state in palette.blocks) {
            if (state.type.isAir()) continue

            val pos = state.location
            minX = min(minX, pos.blockX)
            minY = min(minY, pos.blockY)
            minZ = min(minZ, pos.blockZ)
            maxX = max(maxX, pos.blockX)
            maxY = max(maxY, pos.blockY)
            maxZ = max(maxZ, pos.blockZ)
        }

        if (minX > maxX) return null // entirely air, ignore

        val trimmedMin = BlockVector(minX, minY, minZ)
        val trimmedSize = BlockVector(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1)

        return PlatformTemplate(structure, trimmedMin, trimmedSize)
    }

    @JvmRecord
    data class PlatformTemplate(@JvmField val structure: Structure?, @JvmField val trimmedMin: BlockVector?, @JvmField val trimmedSize: BlockVector?)

    @JvmRecord
    data class PlacedPlatform(@JvmField val template: PlatformTemplate?, @JvmField val origin: Location?, @JvmField val topY: Int)
}
