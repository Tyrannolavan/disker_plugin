package me.tyrn11.disker;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DiskerPlugin extends JavaPlugin implements CommandExecutor {

    private File musicFolder;
    private File resourcePackFolder;
    private File oggFolder;
    private List<String> availableSongs;

    @Override
    public void onEnable() {
        getLogger().info("Disker Plugin enabled!");

        musicFolder = new File(getDataFolder(), "music");
        resourcePackFolder = new File(getDataFolder(), "resourcepack");
        oggFolder = new File(resourcePackFolder, "assets/minecraft/sounds/music/discs");

        if (!musicFolder.exists()) {
            musicFolder.mkdirs();
            getLogger().info("Created music folder at " + musicFolder.getAbsolutePath());
        }
        if (!oggFolder.exists()) {
            oggFolder.mkdirs();
        }

        availableSongs = new ArrayList<>();

        getLogger().info("Starting audio conversion...");
        convertMp3sToOgg();
        loadAvailableSongs();
        generateResourcePack();

        if (getCommand("Disker") != null) {
            getCommand("Disker").setExecutor(this);
        }

        getLogger().info("Disker loaded with " + availableSongs.size() + " songs ready!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disker Plugin disabled!");
    }

    private void convertMp3sToOgg() {
        File[] mp3Files = musicFolder.listFiles((dir, name) -> name.endsWith(".mp3"));

        if (mp3Files == null || mp3Files.length == 0) {
            getLogger().info("No MP3 files found to convert");
            return;
        }

        for (File mp3File : mp3Files) {
            String fileName = mp3File.getName().replace(".mp3", "");
            File oggFile = new File(oggFolder, fileName + ".ogg");

            if (oggFile.exists()) {
                getLogger().info("OGG already exists for: " + fileName);
                continue;
            }

            getLogger().info("Converting: " + fileName + ".mp3");

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg",
                        "-i", mp3File.getAbsolutePath(),
                        "-q:a", "9",
                        oggFile.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    getLogger().info("Successfully converted: " + fileName);
                } else {
                    getLogger().warning("Failed to convert: " + fileName);
                }
            } catch (Exception e) {
                getLogger().severe("Error converting " + fileName + ": " + e.getMessage());
                getLogger().severe("Make sure FFmpeg is installed and in your PATH");
            }
        }
    }

    private void loadAvailableSongs() {
        availableSongs.clear();
        File[] oggFiles = oggFolder.listFiles((dir, name) -> name.endsWith(".ogg"));

        if (oggFiles != null) {
            for (File file : oggFiles) {
                String songName = file.getName().replace(".ogg", "");
                availableSongs.add(songName);
            }
        }
    }

    private void generateResourcePack() {
        try {
            File packMcmeta = new File(resourcePackFolder, "pack.mcmeta");
            FileWriter writer = new FileWriter(packMcmeta);
            writer.write("{\n");
            writer.write("  \"pack\": {\n");
            writer.write("    \"pack_format\": 32,\n");
            writer.write("    \"description\": \"Disker Custom Music Pack\"\n");
            writer.write("  }\n");
            writer.write("}\n");
            writer.close();

            generateSoundsJson();

            getLogger().info("Resource pack generated successfully");
        } catch (IOException e) {
            getLogger().severe("Failed to generate resource pack: " + e.getMessage());
        }
    }

    private void generateSoundsJson() {
        try {
            File soundsJson = new File(resourcePackFolder, "assets/minecraft/sounds.json");
            soundsJson.getParentFile().mkdirs();

            StringBuilder json = new StringBuilder();
            json.append("{\n");

            for (int i = 0; i < availableSongs.size(); i++) {
                String song = availableSongs.get(i);
                json.append("  \"record.").append(song).append("\": {\n");
                json.append("    \"sounds\": [\n");
                json.append("      \"music/discs/").append(song).append("\"\n");
                json.append("    ]\n");
                json.append("  }");
                if (i < availableSongs.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("}\n");

            FileWriter writer = new FileWriter(soundsJson);
            writer.write(json.toString());
            writer.close();

            getLogger().info("Generated sounds.json with " + availableSongs.size() + " entries");
        } catch (IOException e) {
            getLogger().severe("Failed to generate sounds.json: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!cmd.getName().equalsIgnoreCase("Disker")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("§cUsage: /Disker <create|list> [songname]");
            return true;
        }

        String action = args[0].toLowerCase();

        if (action.equals("list")) {
            if (availableSongs.isEmpty()) {
                player.sendMessage("§cNo songs available!");
                return true;
            }
            player.sendMessage("§6Available songs:");
            for (String song : availableSongs) {
                player.sendMessage("§7 - " + song);
            }
            return true;
        }

        if (action.equals("create")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /Disker create <songname>");
                return true;
            }

            StringBuilder songName = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                songName.append(args[i]);
                if (i < args.length - 1) songName.append(" ");
            }

            String finalSongName = songName.toString();
            if (!availableSongs.contains(finalSongName)) {
                player.sendMessage("§cSong '" + finalSongName + "' not found! Use /Disker list to see available songs.");
                return true;
            }

            ItemStack disc = createMusicDisc(finalSongName);
            player.getInventory().addItem(disc);
            player.sendMessage("§aMusic disc for '" + finalSongName + "' added to your inventory!");
            return true;
        }

        player.sendMessage("§cUnknown action! Use 'create' or 'list'");
        return true;
    }

    private ItemStack createMusicDisc(String songName) {
        ItemStack disc = new ItemStack(Material.MUSIC_DISC_5);

        ItemMeta meta = disc.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + songName);

            List<String> lore = new ArrayList<>();
            lore.add("§7Custom Music Disc");
            lore.add("§7Play in jukebox!");
            meta.setLore(lore);

            disc.setItemMeta(meta);
        }

        return disc;
    }
}