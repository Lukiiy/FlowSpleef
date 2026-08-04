package me.lukiiy.spleef

import org.bukkit.inventory.ItemStack

enum class Mode(private val items: Array<ItemStack>) {
    SHOVELS(arrayOf(Item.SHOVEL)),
    SNOWBALL(arrayOf(Item.BALL_MINEABLE.asQuantity(3))),
    MIXED(arrayOf(Item.SHOVEL));

    fun getItems(): Array<ItemStack> = items.clone()
}