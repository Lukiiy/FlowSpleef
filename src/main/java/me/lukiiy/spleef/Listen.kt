package me.lukiiy.spleef

import me.lukiiy.flow.FlowPlayer
import me.lukiiy.flow.component.BasePlayer
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.*

class Listen(private val game: Game) : Listener {
    companion object {
        val ALLOWED_CAUSES: EnumSet<EntityDamageEvent.DamageCause> = EnumSet.of(EntityDamageEvent.DamageCause.CUSTOM, EntityDamageEvent.DamageCause.DROWNING, EntityDamageEvent.DamageCause.LAVA, EntityDamageEvent.DamageCause.VOID)
    }

    @EventHandler
    fun move(e: PlayerMoveEvent) {
        if (!e.to.block.isLiquid) return

        val fp = flowPlayer(e.player) ?: return
        game.eliminate(fp)
    }

    @EventHandler
    fun interact(e: PlayerInteractEvent) {
        if (!e.action.isLeftClick) return

        val block = e.clickedBlock ?: return
        val item = e.player.inventory.itemInMainHand

        if (item.type != Material.IRON_SHOVEL || block.type == Material.SNOW) return

        val fp = flowPlayer(e.player) ?: return
        if (fp.state != BasePlayer.State.PLAYING) return

        e.isCancelled = true
        breakBlock(e.player, block)
    }

    @EventHandler
    fun projectileThrow(e: ProjectileLaunchEvent) {
        val snowball = e.entity as? Snowball ?: return
        val player = snowball.shooter as? Player ?: return

        val fp = flowPlayer(player) ?: return
        if (fp.state != BasePlayer.State.PLAYING || game.entry().mode.value != Mode.SNOWBALL) return

        player.inventory.addItem(ItemStack(Material.SNOWBALL, 1))
    }

    @EventHandler
    fun projectileHit(e: ProjectileHitEvent) {
        val snowball = e.entity as? Snowball ?: return
        val player = snowball.shooter as? Player ?: return

        val block = e.hitBlock
        val entity = e.hitEntity

        if (block != null) {
            val fp = flowPlayer(player) ?: return
            if (fp.state != BasePlayer.State.PLAYING || snowball.location.block.isLiquid || block.type.blastResistance > 1200) return

            breakBlock(player, block)
        }

        if (entity != null && entity is Player) {
            e.isCancelled = true
            entity.world.playSound(entity.location, Sound.ENTITY_PLAYER_HURT, 1f, 1f)

            val hForce = -.35
            val vForce = .5

            val direction: Vector = snowball.velocity.normalize()
            val hKb = Vector(-direction.getX(), 0.0, -direction.getZ()).normalize().multiply(hForce)

            entity.velocity = hKb.add(Vector(0.0, vForce, 0.0))
            entity.playHurtAnimation(30f)
        }
    }

    private fun breakBlock(player: Player, block: Block) {
        val type = block.type
        if (type == Material.AIR) return

        val world = block.world
        val center = block.location.toCenterLocation()
        val blockData = block.blockData

        world.playSound(center, block.blockSoundGroup.breakSound, SoundCategory.BLOCKS, .7f, 1f)
        world.spawnParticle(Particle.BLOCK, center, 10, .25, .25, .25, .05, blockData)

        block.type = Material.AIR

        if (type == Material.TNT) {
            world.createExplosion(center, 1.5f, false, true, player)
            return
        }

        if (game.entry().mode.value == Mode.MIXED) player.inventory.addItem(ItemStack(Material.SNOWBALL, 1))
    }

    @EventHandler
    fun damage(e: EntityDamageEvent) {
        if (e.entity !is Player || !ALLOWED_CAUSES.contains(e.cause)) return

        e.isCancelled = true
    }

    @EventHandler
    fun blockExplosion(e: BlockExplodeEvent) {
        e.yield = 1.5f
        e.blockList().removeIf { it.type == Material.TNT }
    }

    private fun flowPlayer(player: Player): FlowPlayer? = game.getPlayers().firstOrNull { it is FlowPlayer && it.player == player } as? FlowPlayer
}
