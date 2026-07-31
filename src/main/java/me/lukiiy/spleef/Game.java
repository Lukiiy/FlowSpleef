package me.lukiiy.spleef;

import me.lukiiy.flow.*;
import me.lukiiy.spleef.map.ArenaAdapter;
import me.lukiiy.spleef.map.Platforms;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class Game extends Minigame {
    private static final int PLATFORM_GAP = 8;

    private World world;
    private final List<Platforms.PlatformTemplate> templates = new ArrayList<>();
    private final List<Platforms.PlacedPlatform> placedPlatforms = new ArrayList<>();

    public final AtomicBoolean freeze = new AtomicBoolean(false);
    public final AtomicBoolean end = new AtomicBoolean(false);

    private final BossBar bossBar = BossBar.bossBar(Component.text("Starting"), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);

    @Override
    protected List<Listener> listeners() {
        return List.of(new Listen(this));
    }

    @Override
    protected void prepare() {
        templates.clear();
        placedPlatforms.clear();

        world = loadSelectedWorld();
        if (world == null) throw new MinigameException("No arena is selected or registered.");

        world.setGameRule(GameRule.LOCATOR_BAR, false);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);

        loadTemplates();
        if (templates.isEmpty()) throw new MinigameException("No spleef structures (.nbt) found to build a platform stack.");

        buildPlatformStack();
    }

    @Override
    protected void onStart() {
        Platforms.PlacedPlatform top = placedPlatforms.getFirst();

        List<Location> spawns = computeSpawns(getPlayers().size(), top);
        Iterator<Location> spawnIterator = spawns.iterator();
        Location fallback = world.getSpawnLocation();

        forEachPlayer(fp -> {
            Player p = fp.getPlayer();

            FUtils.softReset(p, GameMode.SURVIVAL);
            p.teleport(spawnIterator.hasNext() ? spawnIterator.next() : fallback);
            p.setRespawnLocation(fallback, true);
            p.showBossBar(bossBar);
            p.getInventory().addItem(entry().mode.getValue().getItems());
        });

        freeze.set(true);

        new Countdown(Spleef.getInstance(), Duration.ofSeconds(5), (c) -> {
            if (c == 5) return;

            bossBar.name(Component.text("Time to start: " + c + " seconds"));
        }, () -> {
            freeze.set(false);

            bossBar.name(Component.text("Round start!"));

            Bukkit.getGlobalRegionScheduler().runDelayed(Spleef.getInstance(), (_) -> {
                forEachPlayer(it -> it.getPlayer().hideBossBar(bossBar));

                bossBar.name(Component.empty());
            }, 40L);
        }).start();
    }

    @Override
    protected void onStop() {
        BaseLobby lobby = Flow.getInstance().getManager().getLobby();
        if (lobby != null) forEachPlayer(lobby::sendToLobby);

        if (world != null) {
            Spleef.getInstance().worldAdapter.unload(world);

            world = null;
        }

        templates.clear();
        placedPlatforms.clear();
    }

    public List<FlowPlayer> getAlive() {
        return getPlayers().stream().filter(p -> p instanceof FlowPlayer fp && fp.getState() == FlowPlayer.State.PLAYING).map(p -> (FlowPlayer) p).toList();
    }

    public void eliminate(FlowPlayer player) {
        if (player.getState() != FlowPlayer.State.PLAYING) return;

        player.setState(FlowPlayer.State.SPECTATING);
        player.getPlayer().setGameMode(GameMode.SPECTATOR);

        end.set(true);

        getAlive().stream().map(FlowPlayer::getPlayer).forEach(it -> it.playSound(it, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1, 1));
        checkWin();
    }

    public void checkWin() {
        List<FlowPlayer> alive = getAlive();
        if (alive.size() > 1) return;

        FlowPlayer winner = alive.isEmpty() ? null : alive.getFirst();

        bossBar.name(Component.text("Round end!"));
        forEachPlayer(it -> it.getPlayer().showBossBar(bossBar));

        freeze.set(false);

        ShadowColor noShadow = ShadowColor.shadowColor(0, 0, 0, 0);

        Component iron = Component.object(ObjectContents.sprite(Key.key("minecraft:items"), Key.key("item/iron_shovel"))).shadowColor(noShadow);
        Component diamond = Component.object(ObjectContents.sprite(Key.key("minecraft:items"), Key.key("item/diamond_shovel"))).shadowColor(noShadow);
        Component snowball = Component.object(ObjectContents.sprite(Key.key("minecraft:items"), Key.key("item/snowball"))).shadowColor(noShadow);
        Component endMsg;

        if (winner != null && winner.getPlayer() != null) {
            forEachPlayer(it -> it.getPlayer().showTitle(Title.title(Component.empty(), Component.empty().append(iron).appendSpace().append(winner.getPlayer().displayName()).append(Component.text(" won! ")).append(diamond))));

            endMsg = Component.empty().append(iron).appendSpace().append(winner.getPlayer().displayName()).append(Component.text(" won! ")).append(diamond);
        } else endMsg = snowball.append(Component.text(" Nobody won!"));

        Bukkit.getGlobalRegionScheduler().run(Spleef.getInstance(), _ -> broadcast(endMsg));

        new Countdown(Spleef.getInstance(), Duration.ofSeconds(5), (c) -> {
            if (c == 5) return;

            bossBar.name(Component.text("Time to round end: " + c + " seconds"));
        }, this::stop).start();
    }

    private void loadTemplates() {
        File dir = new File(Bukkit.getWorldContainer().getParentFile(), "spleef");

        File[] files = dir.listFiles((t, name) -> name.endsWith(".nbt"));
        if (files == null) return;

        for (File file : files) {
            try {
                Platforms.PlatformTemplate template = Platforms.trim(Bukkit.getStructureManager().loadStructure(file));

                if (template != null) templates.add(template);
            } catch (IOException e) {
                Spleef.getInstance().getLogger().warning("Failed to load platform structure " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    // Platform stacking

    private void buildPlatformStack() {
        Location spawn = world.getSpawnLocation();

        int amount = entry().platformAmount.getValue().intValue();
        int nextTopY = spawn.getBlockY() - 2;

        for (int i = 0; i < amount; i++) {
            Platforms.PlatformTemplate template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));

            int trimmedMaxY = template.trimmedMin.getBlockY() + template.trimmedSize.getBlockY() - 1;
            int originX = spawn.getBlockX() - template.trimmedMin.getBlockX() - template.trimmedSize.getBlockX() / 2;
            int originZ = spawn.getBlockZ() - template.trimmedMin.getBlockZ() - template.trimmedSize.getBlockZ() / 2;
            int originY = nextTopY - trimmedMaxY;

            Location origin = new Location(world, originX, originY, originZ);
            template.structure.place(origin, false, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, ThreadLocalRandom.current());

            placedPlatforms.add(new Platforms.PlacedPlatform(template, origin, nextTopY));

            nextTopY = origin.getBlockY() - PLATFORM_GAP;
        }
    }

    // Spawn detection

    private List<Location> computeSpawns(int count, Platforms.PlacedPlatform platform) {
        Platforms.PlatformTemplate template = platform.template;

        int minX = platform.origin.getBlockX() + template.trimmedMin.getBlockX();
        int maxX = minX + template.trimmedSize.getBlockX() - 1;
        int minZ = platform.origin.getBlockZ() + template.trimmedMin.getBlockZ();
        int maxZ = minZ + template.trimmedSize.getBlockZ() - 1;
        int topY = platform.topY;

        List<Location> candidates = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block standOn = world.getBlockAt(x, topY, z);
                if (!standOn.getType().isSolid()) continue;

                VoxelShape shape = standOn.getCollisionShape();
                if (shape.getBoundingBoxes().isEmpty()) continue;

                double maxCollisionY = shape.getBoundingBoxes().stream().mapToDouble(BoundingBox::getMaxY).max().orElse(1); // Defaults to full block height if empty
                double actualSpawnY = topY + maxCollisionY;

                // Check if 2 blocks of air above the collision surface are clear
                Block feet = world.getBlockAt(x, (int) Math.floor(actualSpawnY), z);
                Block head = world.getBlockAt(x, (int) Math.floor(actualSpawnY + 1), z);
                if (!feet.isPassable() || !head.isPassable()) continue;

                candidates.add(new Location(world, x + 0.5, actualSpawnY, z + 0.5));
            }
        }

        if (candidates.isEmpty()) return List.of();

        Collections.shuffle(candidates);

        double area = (double) template.trimmedSize.getBlockX() * template.trimmedSize.getBlockZ();
        double minSpacing = Math.max(2.0, Math.sqrt(area / Math.max(1, count)) * 0.75);

        List<Location> selected = new ArrayList<>();

        for (Location candidate : candidates) {
            if (selected.size() >= count) break;

            if (selected.stream().allMatch(loc -> loc.distanceSquared(candidate) >= minSpacing * minSpacing)) selected.add(candidate);
        }

        // Couldn't fit everyone, fill the rest ignoring spacing
        if (selected.size() < count) {
            for (Location candidate : candidates) {
                if (selected.size() >= count) break;

                if (!selected.contains(candidate)) selected.add(candidate);
            }
        }

        return selected;
    }

    private World loadSelectedWorld() {
        String map = entry().map.getValue();

        File source = new File(ArenaAdapter.Companion.getMAPS_DIR(), map);
        if (!source.isDirectory()) return null;

        return Spleef.getInstance().worldAdapter.load(source);
    }

    public Entry entry() {
        return (Entry) entry;
    }
}
