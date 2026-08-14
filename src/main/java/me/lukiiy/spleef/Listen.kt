package me.lukiiy.spleef

import me.lukiiy.flow.FlowPlayer
import me.lukiiy.flow.component.BasePlayer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Snowball
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
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

        if (e.action.isRightClick) {
            e.setUseInteractedBlock(Event.Result.DENY)
            return
        }

        if (fp.state != BasePlayer.State.PLAYING || game.freeze.get()) return
        val block = e.clickedBlock ?: return

        if (block.type.blastResistance > 1200 || block.type == Material.SNOW) return
        val item = e.player.inventory.itemInMainHand

        val validTool = when (game.entry().mode.value) {
            Mode.SNOWBALL -> item.type == Material.SNOWBALL
            else -> item.type == Material.IRON_SHOVEL
        }

        if (!validTool) return

        breakBlock(e.player, block, null)
    }

    @EventHandler
    fun projectileThrow(e: ProjectileLaunchEvent) {
        val snowball = e.entity as? Snowball ?: return
        val player = snowball.shooter as? Player ?: return
        val fp = flowPlayer(player) ?: return
        val mode = game.entry().mode.value

        if (fp.state != BasePlayer.State.PLAYING) return

        if (game.freeze.get() || player.hasCooldown(Material.SNOWBALL)) {
            e.isCancelled = true
            return
        }

        if (game.entry().snowballCooldown.value) player.setCooldown(Material.SNOWBALL, 5)

        if (mode == Mode.SNOWBALL && itemAmount(player.inventory, Item.BALL_MINEABLE) < 3) player.inventory.addItem(Item.BALL_MINEABLE)
    }

    @EventHandler
    fun projectileHit(e: ProjectileHitEvent) {
        val snowball = e.entity as? Snowball ?: return
        val player = snowball.shooter as? Player ?: return
        if (flowPlayer(player) == null) return

        e.hitBlock?.let {
            if (snowball.location.block.isLiquid || it.type.blastResistance > 1200) return@let

            breakBlock(player, it, snowball)
        }

        val target = e.hitEntity as? Player ?: return
        val direction = snowball.velocity.normalize()
        val horizontal = Vector(-direction.x, 0.0, -direction.z).normalize().multiply(.25)

        e.isCancelled = true

        target.apply {
            world.playSound(target.location, Sound.ENTITY_PLAYER_HURT, 1f, 1f)

            val vertical = if (target.isOnGround) .25 else 0.0

            target.velocity = horizontal.add(Vector(0.0, vertical, 0.0))
            target.playHurtAnimation(30f)
        }

        snowball.remove()
    }

    private fun breakBlock(player: Player, block: Block, projectile: Projectile?) {
        if (player.gameMode == GameMode.ADVENTURE) return

        if (block.type == Material.TNT) {
            block.type = Material.AIR
            block.world.createExplosion(block.location.toCenterLocation(), 1.5f, false, true, player)
            return
        }

        if (projectile != null) {
            if (!game.showdown.get() || game.entry().showdownMode.value != ShowdownMode.SUPER_BALL) {
                Bukkit.getRegionScheduler().run(Spleef.getInstance(), block.location) { block.breakNaturally(true) }
            } else {
                val velocity = projectile.velocity

                val xOff = if (velocity.x >= 0.0) 0 else -1
                val zOff = if (velocity.z >= 0.0) 0 else -1

                val blocks = listOf(block, block.getRelative(xOff, 0, 0), block.getRelative(0, 0, zOff), block.getRelative(xOff, 0, zOff)).distinct()

                Bukkit.getRegionScheduler().run(Spleef.getInstance(), block.location) {
                    blocks.forEach { b -> if (b.type != Material.AIR) b.breakNaturally(true) }
                }
            }
        }

        if (game.entry().mode.value == Mode.MIXED && itemAmount(player.inventory, Item.BALL) < 8) player.inventory.addItem(Item.BALL)
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

        loc.world.createExplosion(loc, 1.5f, false, true)
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
