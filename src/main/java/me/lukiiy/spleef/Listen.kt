package me.lukiiy.spleef

import me.lukiiy.flow.FlowPlayer
import me.lukiiy.flow.component.BasePlayer
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.util.Vector
import java.util.*

class Listen(private val game: Game) : Listener {
    companion object {
        val ALLOWED_CAUSES: EnumSet<EntityDamageEvent.DamageCause> = EnumSet.of(EntityDamageEvent.DamageCause.CUSTOM, EntityDamageEvent.DamageCause.DROWNING, EntityDamageEvent.DamageCause.LAVA, EntityDamageEvent.DamageCause.VOID)
    }

    @EventHandler
    fun move(e: PlayerMoveEvent) {
        if (game.freeze.get()) {
            val from = e.from
            val to = e.to

            if (from.x != to.x || from.y != to.y || from.z != to.z) {
                e.isCancelled = true

                e.to = from.clone().apply {
                    yaw = to.yaw
                    pitch = to.pitch
                }
            }

            return
        }

        if (e.to.block.isLiquid) flowPlayer(e.player)?.let(game::eliminate)
    }

    @EventHandler
    fun death(e: PlayerDeathEvent) {
        flowPlayer(e.player)?.let(game::eliminate)
    }

    @EventHandler
    fun interact(e: PlayerInteractEvent) {
        val fp = flowPlayer(e.player) ?: return
        val block = e.clickedBlock ?: return
        val item = e.player.inventory.itemInMainHand

        if (e.action.isRightClick) {
            e.setUseInteractedBlock(Event.Result.DENY)
            e.setUseItemInHand(Event.Result.DENY)
            return
        }

        if (block.type.blastResistance > 1200 || block.type == Material.SNOW || fp.state != BasePlayer.State.PLAYING) return

        val validTool = when (game.entry().mode.value) {
            Mode.SNOWBALL -> item.type == Material.SNOWBALL
            else -> item.type == Material.IRON_SHOVEL
        }

        if (!validTool) return

        breakBlock(e.player, block)
    }

    @EventHandler
    fun projectileThrow(e: ProjectileLaunchEvent) {
        val snowball = e.entity as? Snowball ?: return
        val player = snowball.shooter as? Player ?: return
        val fp = flowPlayer(player) ?: return

        if (game.freeze.get() || player.hasCooldown(Material.SNOWBALL)) {
            e.isCancelled = true
            return
        }

        if (fp.state != BasePlayer.State.PLAYING || game.entry().mode.value == Mode.SHOVELS) return

        if (game.entry().snowballCooldown.value) player.setCooldown(Material.SNOWBALL, 5)

        if (itemAmount(player.inventory, Item.BALL) < 3) player.inventory.addItem(Item.BALL)
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

            val hForce = -.25
            val vForce = if (entity.fallDistance > .1) 0.0 else .25

            val direction: Vector = snowball.velocity.normalize()
            val hKb = Vector(-direction.getX(), 0.0, -direction.getZ()).normalize().multiply(hForce)

            entity.velocity = hKb.add(Vector(0.0, vForce, 0.0))
            entity.playHurtAnimation(30f)

            snowball.remove()
        }
    }

    private fun breakBlock(player: Player, block: Block) {
        if (block.type == Material.TNT) {
            block.type = Material.AIR
            block.world.createExplosion(block.location.toCenterLocation(), 1.5f, false, true, player)
            return
        }

        if (game.entry().mode.value == Mode.SNOWBALL && itemAmount(player.inventory, Item.BALL) < 3) player.inventory.addItem(Item.BALL)
    }

    @EventHandler
    fun damage(e: EntityDamageEvent) {
        if (e.entity !is Player) return

        if (game.end.get()) {
            e.isCancelled = true
            return
        }

        if (!ALLOWED_CAUSES.contains(e.cause)) e.isCancelled = true
    }

    @EventHandler
    fun tntExplode(e: EntityExplodeEvent) {
        if (e.entity !is TNTPrimed) return

        e.isCancelled = true

        val loc = e.location

        loc.world.createExplosion(loc, 2f, false, true)
    }

    @EventHandler
    fun blockBreak(e: BlockBreakEvent) {
        if (flowPlayer(e.player) == null) return

        if (game.freeze.get() || !e.player.inventory.itemInMainHand.persistentDataContainer.has(Item.KEY)) e.isCancelled = true
    }

    @EventHandler
    fun drop(e: PlayerDropItemEvent) {
        if (flowPlayer(e.player) != null && e.itemDrop.itemStack.persistentDataContainer.has(Item.KEY)) e.isCancelled = true
    }

    @EventHandler
    fun entityBlockChange(e: EntityChangeBlockEvent) {
        if (e.entity is FallingBlock && e.block.isEmpty) e.isCancelled = true
    }

    fun itemAmount(inventory: PlayerInventory, item: ItemStack): Int = inventory.contents.filterNotNull().filter { it.isSimilar(item) }.sumOf { it.amount }

    private fun flowPlayer(player: Player): FlowPlayer? = game.getPlayers().filterIsInstance<FlowPlayer>().firstOrNull { it.player == player }
}
