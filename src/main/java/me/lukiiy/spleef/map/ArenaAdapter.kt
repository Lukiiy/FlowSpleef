package me.lukiiy.spleef.map;

import me.lukiiy.spleef.Spleef;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class ArenaAdapter {
    public static final File MAPS_DIR = new File(Bukkit.getWorldContainer(), "maps");
    private static final Set<String> IGNORED = Set.of("uid.dat", "session.lock", "players", "advancements", "stats", "datapacks", "paper-world.yml", "paper-world-defaults.yml");

    private static final String WORLD_PREFIX = "spf_";

    public List<String> listAvailableMaps() {
        File[] dirs = MAPS_DIR.listFiles(file -> file.isDirectory() && file.getName().startsWith(WORLD_PREFIX));
        if (dirs == null) return List.of();

        return Arrays.stream(dirs).map(File::getName).sorted().toList();
    }

    public World load(File folder) {
        Path source = folder.toPath();

        if (!Files.exists(source.resolve("level.dat"))) throw new IllegalArgumentException("No valid world at " + folder);

        String name = folder.getName() + "_" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
        Path instance = Bukkit.getWorldContainer().toPath().resolve(name);

        try {
            copy(source, instance);
        } catch (IOException e) {
            Spleef.getInstance().getLogger().severe("Failed to copy map " + folder.getName() + ": " + e.getMessage());
            return null;
        }

        World world = new WorldCreator(name).createWorld();

        if (world == null) {
            try {
                delete(instance);
            } catch (IOException ignored) {}

            return null;
        }

        return world;
    }

    public boolean unload(World world) {
        world.getPlayers().forEach(p -> p.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation()));

        boolean result = Bukkit.unloadWorld(world, false);

        try {
            delete(world.getWorldFolder().toPath());
        } catch (IOException e) {
            Spleef.getInstance().getLogger().warning("Failed to delete temporary world " + world.getName() + ": " + e.getMessage());
        }

        return result;
    }

    private static void copy(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (ignored(dir)) return FileVisitResult.SKIP_SUBTREE;

                Files.createDirectories(target.resolve(source.relativize(dir)));

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!ignored(file)) Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean ignored(Path path) {
        return IGNORED.contains(path.getFileName().toString());
    }

    public static String formatString(String input) {
        if (input == null || input.isEmpty()) return "";

        String noPrefix = input.startsWith(WORLD_PREFIX) ? input.substring(4) : input;

        return Arrays.stream(noPrefix.split("_")).filter(s -> !s.isEmpty()).map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1)).collect(Collectors.joining(" "));
    }
}