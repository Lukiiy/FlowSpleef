package me.lukiiy.spleef;

import org.bukkit.plugin.java.JavaPlugin;

public final class Spleef extends JavaPlugin {
    @Override
    public void onEnable() {
        // Plugin startup logic
    }

    public static Spleef getInstance() {
        return JavaPlugin.getPlugin(Spleef.class);
    }
}
