package me.lukiiy.spleef

import org.bukkit.inventory.ItemStack

enum class Mode(private val items: Array<ItemStack>) {
    SHOVELS(arrayOf(Item.SHOVEL)),
    SNOWBALL(arrayOf(Item.BALL)),
    MIXED(arrayOf(Item.SHOVEL, Item.BALL));

    fun getItems(): Array<ItemStack> = items.clone()
}