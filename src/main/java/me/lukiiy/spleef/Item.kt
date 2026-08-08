package me.lukiiy.spleef

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Tool
import io.papermc.paper.datacomponent.item.TooltipDisplay
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object Item {
    val KEY = NamespacedKey(Spleef.getInstance(), "item")
    val INSTAMINE = Tool.tool().defaultMiningSpeed(65535f).build()

    val SHOVEL: ItemStack = create(Material.IRON_SHOVEL) {
        addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE)
        setData(DataComponentTypes.UNBREAKABLE)
        addUnsafeEnchantment(Enchantment.EFFICIENCY, 3)
        setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build())
        setData(DataComponentTypes.TOOL, INSTAMINE)
    }

    val BALL: ItemStack = create(Material.SNOWBALL) {
        setData(DataComponentTypes.MAX_STACK_SIZE, 6)
        setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build())
        setData(DataComponentTypes.TOOL, Tool.tool().defaultMiningSpeed(0f).build())
    }

    val BALL_MINEABLE: ItemStack = BALL.clone().apply {
        setData(DataComponentTypes.TOOL, INSTAMINE)
    }

    private fun create(material: Material, builder: ItemStack.() -> Unit) = ItemStack.of(material).apply(builder).apply { editPersistentDataContainer { it.set(KEY, PersistentDataType.BOOLEAN, true) } }
}