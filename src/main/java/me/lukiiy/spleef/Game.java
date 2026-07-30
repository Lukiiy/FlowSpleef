package me.lukiiy.spleef;

import me.lukiiy.flow.*;
import me.lukiiy.spleef.map.ArenaAdapter;
import me.lukiiy.spleef.map.Platforms;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

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

    private final AtomicBoolean inProgress = new AtomicBoolean(false);

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

            switch (entry().mode.getValue()) {
                case SHOVELS, MIXED -> p.getInventory().addItem(Item.INSTANCE.getSHOVEL());
                case SNOWBALL -> p.getInventory().addItem(Item.INSTANCE.getBALL());
            }
        });
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
        getAlive().stream().findAny().ifPresent(target -> player.getPlayer().teleport(target.getPlayer().getLocation()));
        checkWin();
    }

    public void checkWin() {
        List<FlowPlayer> alive = getAlive();
        if (alive.size() > 1) return;

        FlowPlayer winner = alive.isEmpty() ? null : alive.getFirst();

        Component iron = Component.object(ObjectContents.sprite(Key.key(Key.MINECRAFT_NAMESPACE, "item/iron_shovel")));
        Component diamond = Component.object(ObjectContents.sprite(Key.key(Key.MINECRAFT_NAMESPACE, "item/diamond_shovel")));
        Component snowball = Component.object(ObjectContents.sprite(Key.key(Key.MINECRAFT_NAMESPACE, "item/snowball")));

        Component endMsg;

        if (winner != null && winner.getPlayer() != null) {
            forEachPlayer(it -> it.getPlayer().showTitle(Title.title(iron.append(diamond), Component.empty().append(winner.getPlayer().displayName()).append(Component.text(" won!")))));

            endMsg = Component.empty().append(iron).appendSpace().append(winner.getPlayer().displayName()).append(Component.text(" won! ")).append(diamond);
        } else endMsg = snowball.append(Component.text(" Nobody won!"));

        Bukkit.getGlobalRegionScheduler().run(Spleef.getInstance(), _ -> broadcast(endMsg));
        Bukkit.getGlobalRegionScheduler().runDelayed(Spleef.getInstance(), _ -> stop(), 100L);
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
                Block feet = world.getBlockAt(x, topY + 1, z);
                Block head = world.getBlockAt(x, topY + 2, z);

                if (!standOn.getType().isSolid() || !feet.isEmpty()|| !head.isPassable()) continue;

                candidates.add(new Location(world, x + 0.5, topY + 1, z + 0.5));
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
