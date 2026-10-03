package org.kurw1k.clans;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ClanData {
    private final Clans plugin;
    private final File file;

    public final Map<String, Clan> clans = new HashMap<>();
    public final Map<UUID, String> playerClans = new HashMap<>();
    public final Map<UUID, String> invites = new HashMap<>();

    public ClanData(Clans plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    public static class Clan {
        public String name;
        public UUID leader;
        public List<UUID> members;
        public double balance;
        public int level;
        public boolean pvp;
        public long blocks;
        public Map<UUID, Map<String, Boolean>> permissions;
        public ItemStack[] storageContents;
        private Clans pluginInstance;

        public Clan(String name, UUID leader, List<UUID> members, double balance, int level, boolean pvp, long blocks, Map<UUID, Map<String, Boolean>> permissions) {
            this.name = name;
            this.leader = leader;
            this.members = members;
            this.balance = balance;
            this.level = level;
            this.pvp = pvp;
            this.blocks = blocks;
            this.permissions = permissions;
            this.storageContents = new ItemStack[54];
        }

        public boolean hasPermission(UUID playerUuid, String perm) {
            if (leader.equals(playerUuid)) return true;

            if (!permissions.containsKey(playerUuid) || permissions.get(playerUuid) == null) {
                return getDefaultPermission(perm);
            }

            Map<String, Boolean> playerPerms = permissions.get(playerUuid);
            return playerPerms.getOrDefault(perm, getDefaultPermission(perm));
        }

        private boolean getDefaultPermission(String perm) {
            return switch (perm) {
                case "invest", "storage_open", "storage_deposit", "storage_withdraw" -> true;
                default -> false;
            };
        }

        public int getUnlockedSlots(Clans plugin) {
            if (plugin != null && plugin.getConfig().contains("levels." + level + ".storage-slots")) {
                return plugin.getConfig().getInt("levels." + level + ".storage-slots", 4);
            }
            return 4;
        }

        public Inventory getStorageInventory(Clans plugin) {
            this.pluginInstance = plugin;
            Inventory inv = Bukkit.createInventory(null, 54, "§1Склад клана: " + name);
            int unlocked = getUnlockedSlots(plugin);

            for (int i = 0; i < 54; i++) {
                if (i < unlocked) {
                    if (i < storageContents.length && storageContents[i] != null) {
                        inv.setItem(i, storageContents[i]);
                    }
                } else {
                    int requiredLevel = -1;
                    if (plugin != null && plugin.getConfig().contains("levels")) {
                        org.bukkit.configuration.ConfigurationSection levelsSec = plugin.getConfig().getConfigurationSection("levels");
                        if (levelsSec != null) {
                            for (String lvlKey : levelsSec.getKeys(false)) {
                                int slotsAtLevel = plugin.getConfig().getInt("levels." + lvlKey + ".storage-slots", 0);
                                if (slotsAtLevel > i) {
                                    int lvlNum = Integer.parseInt(lvlKey);
                                    if (requiredLevel == -1 || lvlNum < requiredLevel) {
                                        requiredLevel = lvlNum;
                                    }
                                }
                            }
                        }
                    }

                    ItemStack lockedPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                    ItemMeta meta = lockedPane.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§cЗаблокированный слот");
                        List<String> lore = new ArrayList<>();
                        lore.add("§7Текущий уровень клана: §e" + level);
                        if (requiredLevel != -1) {
                            lore.add("§7Требуется уровень: §c" + requiredLevel);
                        } else {
                            lore.add("§7Требуется уровень: §cМаксимальный");
                        }
                        lore.add("§cСлот недоступен!");
                        meta.setLore(lore);
                        lockedPane.setItemMeta(meta);
                    }
                    inv.setItem(i, lockedPane);
                }
            }
            return inv;
        }
    }

    public void save() {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();

        for (Map.Entry<String, Clan> entry : clans.entrySet()) {
            String path = "clans." + entry.getKey() + ".";
            Clan clan = entry.getValue();

            config.set(path + "name", clan.name);
            config.set(path + "leader", clan.leader.toString());

            List<String> mems = new ArrayList<>();
            for (UUID m : clan.members) {
                mems.add(m.toString());
            }
            config.set(path + "members", mems);

            config.set(path + "balance", clan.balance);
            config.set(path + "level", clan.level);
            config.set(path + "pvp", clan.pvp);
            config.set(path + "blocks", clan.blocks);

            if (clan.permissions != null) {
                for (Map.Entry<UUID, Map<String, Boolean>> pEntry : clan.permissions.entrySet()) {
                    for (Map.Entry<String, Boolean> subEntry : pEntry.getValue().entrySet()) {
                        config.set(path + "permissions." + pEntry.getKey().toString() + "." + subEntry.getKey(), subEntry.getValue());
                    }
                }
            }

            for (int i = 0; i < clan.storageContents.length; i++) {
                if (clan.storageContents[i] != null) {
                    config.set(path + "storage." + i, clan.storageContents[i]);
                }
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        if (!file.exists()) return;

        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        if (!config.contains("clans")) return;

        clans.clear();
        playerClans.clear();

        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("clans");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String path = "clans." + key + ".";

            String name = config.getString(path + "name", key);
            UUID leader = UUID.fromString(config.getString(path + "leader"));

            List<UUID> members = new ArrayList<>();
            List<String> memsStr = config.getStringList(path + "members");
            for (String mStr : memsStr) {
                members.add(UUID.fromString(mStr));
            }

            double balance = config.getDouble(path + "balance", 0.0);
            int level = config.getInt(path + "level", 1);
            boolean pvp = config.getBoolean(path + "pvp", false);
            long blocks = config.getLong(path + "blocks", 0);

            Map<UUID, Map<String, Boolean>> permissions = new HashMap<>();
            org.bukkit.configuration.ConfigurationSection permSec = config.getConfigurationSection(path + "permissions");
            if (permSec != null) {
                for (String uuidStr : permSec.getKeys(false)) {
                    UUID u = UUID.fromString(uuidStr);
                    Map<String, Boolean> pMap = new HashMap<>();
                    org.bukkit.configuration.ConfigurationSection subSec = permSec.getConfigurationSection(uuidStr);
                    if (subSec != null) {
                        for (String pKey : subSec.getKeys(false)) {
                            pMap.put(pKey, subSec.getBoolean(pKey));
                        }
                    }
                    permissions.put(u, pMap);
                }
            }

            Clan clan = new Clan(name, leader, members, balance, level, pvp, blocks, permissions);

            org.bukkit.configuration.ConfigurationSection storageSec = config.getConfigurationSection(path + "storage");
            if (storageSec != null) {
                for (String sKey : storageSec.getKeys(false)) {
                    int idx = Integer.parseInt(sKey);
                    ItemStack item = config.getItemStack(path + "storage." + sKey);
                    if (idx >= 0 && idx < clan.storageContents.length) {
                        clan.storageContents[idx] = item;
                    }
                }
            }

            clans.put(name.toLowerCase(), clan);
            playerClans.put(leader, name.toLowerCase());
            for (UUID m : members) {
                playerClans.put(m, name.toLowerCase());
            }
        }
    }
}