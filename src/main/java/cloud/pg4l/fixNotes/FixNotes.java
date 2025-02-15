package cloud.pg4l.fixNotes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class FixNotes extends JavaPlugin implements Listener {
    private String fixNoteName;
    private String messageUsage;
    private String messagePlayerNotFound;
    private String messageItemReceived;
    private String messageItemRepaired;
    private boolean enchantGlint;
    private List<String> itemLore;

    @Override
    public void onEnable() {
        Metrics metrics = new Metrics(this, 24749);
        createDefaultConfig();
        loadMessages();
        getLogger().info("[FixNotes] Plugin enabled.");
        Objects.requireNonNull(getCommand("fixnote")).setExecutor(new FixNoteCommand(this));
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("[FixNotes] Plugin disabled.");
    }

    private void createDefaultConfig() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            getLogger().info("Creating default config.yml...");
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("fix_note_name", "&6Fix Note");
            config.set("message_usage", "&cUsage: /fixnote <give/reload> [player]");
            config.set("message_player_not_found", "&cPlayer is not online.");
            config.set("message_item_received", "&aPlayer {PLAYER} got a Fix Note!");
            config.set("message_item_repaired", "&aYour item has been fixed!");
            config.set("enchant_glint", true);
            config.set("item_lore", List.of("&7Use this to repair", "&7any damaged item!"));
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        reloadConfig();
    }

    private void loadMessages() {
        FileConfiguration config = getConfig();
        fixNoteName = ChatColor.translateAlternateColorCodes('&', config.getString("fix_note_name", "&6Fix Note"));
        messageUsage = ChatColor.translateAlternateColorCodes('&', config.getString("message_usage", "&cUsage: /fixnote <give/reload> [player]"));
        messagePlayerNotFound = ChatColor.translateAlternateColorCodes('&', config.getString("message_player_not_found", "&cPlayer is not online."));
        messageItemReceived = ChatColor.translateAlternateColorCodes('&', config.getString("message_item_received", "&aPlayer {PLAYER} got a Fix Note!"));
        messageItemRepaired = ChatColor.translateAlternateColorCodes('&', config.getString("message_item_repaired", "&aYour item has been fixed!"));
        enchantGlint = config.getBoolean("enchant_glint", true);
        itemLore = config.getStringList("item_lore").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
    }

    public static class FixNoteCommand implements CommandExecutor {
        private final FixNotes plugin;

        public FixNoteCommand(FixNotes plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(plugin.messageUsage);
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                plugin.reloadConfig();
                plugin.loadMessages();
                sender.sendMessage(ChatColor.GREEN + "FixNotes config reloaded.");
                return true;
            }

            if (args[0].equalsIgnoreCase("give")) {
                if (args.length != 2) {
                    sender.sendMessage(plugin.messageUsage);
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.messagePlayerNotFound);
                    return true;
                }

                ItemStack note = new ItemStack(Material.PAPER);
                ItemMeta meta = note.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(plugin.fixNoteName);
                    meta.setLore(plugin.itemLore);
                    NamespacedKey key = new NamespacedKey(plugin, "fix_note");
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "special_note");
                    if (plugin.enchantGlint) {
                        meta.addEnchant(Enchantment.MENDING, 10, true);
                    }
                    note.setItemMeta(meta);
                }

                target.getInventory().addItem(note);
                sender.sendMessage(plugin.messageItemReceived.replace("{PLAYER}", target.getName()));
                return true;
            }

            sender.sendMessage(plugin.messageUsage);
            return true;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        ItemStack cursorItem = event.getCursor();
        ItemStack clickedItem = event.getCurrentItem();
        if (cursorItem == null || clickedItem == null) return;

        NamespacedKey key = new NamespacedKey(this, "fix_note");

        if (cursorItem.getType() == Material.PAPER && cursorItem.hasItemMeta() &&
                cursorItem.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            if (clickedItem.getType().getMaxDurability() > 0 && clickedItem.getDurability() > 0) {
                event.setCancelled(true);

                clickedItem.setDurability((short) 0);
                player.sendMessage(messageItemRepaired);
                cursorItem.setAmount(cursorItem.getAmount() - 1);
                event.setCursor(cursorItem.getAmount() > 0 ? cursorItem : null);
            }
        }
    }
}
