package me.lukiiy.spleef;

import me.lukiiy.flow.Flow;
import me.lukiiy.spleef.map.ArenaAdapter;
import org.bukkit.plugin.java.JavaPlugin;

public final class Spleef extends JavaPlugin {
    public final ArenaAdapter worldAdapter = new ArenaAdapter();

    @Override
    public void onEnable() {
        Flow.getInstance().getManager().register(new Entry());
    }

    public static Spleef getInstance() {
        return JavaPlugin.getPlugin(Spleef.class);
    }
}
