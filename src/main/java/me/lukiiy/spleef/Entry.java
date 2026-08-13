package me.lukiiy.spleef;

import me.lukiiy.flow.GameEntry;
import me.lukiiy.flow.setting.BooleanSetting;
import me.lukiiy.flow.setting.CycleSetting;
import me.lukiiy.flow.setting.DoubleSetting;
import me.lukiiy.flow.setting.Option;
import me.lukiiy.spleef.map.ArenaAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;

public class Entry extends GameEntry {
    public final CycleSetting<String> map = setting(new CycleSetting<>("map", "Map", "The background map", () -> {
        List<Option<String>> maps = Spleef.getInstance().worldAdapter.listAvailableMaps().stream().map(name -> new Option<>(name, ArenaAdapter.Companion.formatString(name))).toList();

        return maps.isEmpty() ? List.of(new Option<>("none", "No maps available")) : maps;
    }));

    public final CycleSetting<Mode> mode = setting(new CycleSetting<>("mode", "Mode", "Item set", () -> List.of(
            new Option<>(Mode.SHOVELS, "Shovels"),
            new Option<>(Mode.SNOWBALL, "Snowball"),
            new Option<>(Mode.MIXED, "Mixed")
    )));

    public final DoubleSetting platformAmount = setting(new DoubleSetting("platformAmount", "Platforms", "How many platforms are stacked below each other", 1, 8, 3, 1));

    public final BooleanSetting snowballCooldown = setting(new BooleanSetting("snowCooldown", "Snowball Cooldown", "Implements a tiny cooldown to throw snowballs", false));

    public final CycleSetting<ShowdownMode> showdownMode = setting(new CycleSetting<>("showdown", "Showdown", "Sets the deathmatch type", () -> List.of(
            new Option<>(ShowdownMode.SUPER_BALL, "Super Ball"),
            new Option<>(ShowdownMode.FALLING_TNT, "Falling TNT"),
            new Option<>(ShowdownMode.CRUMBLING_TOP, "Crumbling Top")
    )));

    public final CycleSetting<Integer> showdownDelay = setting(new CycleSetting<>("showdownDelay", "Showdown Delay", "Sets the delay for the showdown to happen", () -> List.of(new Option<>(60, "1 minute"), new Option<>(180, "3 minutes"), new Option<>(300, "5 minutes"), new Option<>(420, "7 minutes"), new Option<>(600, "10 minutes"))));

    public Entry() {
        super("spleef", "Spleef", Game::new, Component.text("Spleef").color(TextColor.color(0x3db4af)));
    }
}
