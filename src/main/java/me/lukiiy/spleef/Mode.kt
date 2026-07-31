package me.lukiiy.spleef

import org.bukkit.inventory.ItemStack

enum class Mode(private val items: Array<ItemStack>) {
    SHOVELS(arrayOf(Item.SHOVEL)),
    SNOWBALL(arrayOf(Item.BALL.asQuantity(3))),
    MIXED(arrayOf(Item.SHOVEL, Item.BALL.asQuantity(3)));

    fun getItems(): Array<ItemStack> = items.clone()
}