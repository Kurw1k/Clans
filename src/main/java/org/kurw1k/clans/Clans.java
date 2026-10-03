package org.kurw1k.clans;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class Clans extends JavaPlugin {

    public static Economy econ = null;
    public ClanData clanData;
    public ClanCommand clanCommand;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Не найден плагин Vault или экономика! Плагин выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        clanData = new ClanData(this);

        clanCommand = new ClanCommand(this);
        var clanCmd = getCommand("clan");
        if (clanCmd != null) {
            clanCmd.setExecutor(clanCommand);
            clanCmd.setTabCompleter(clanCommand);
        }

        getServer().getPluginManager().registerEvents(clanCommand, this);

        getLogger().info("Plani Clan успешно запущен!");
    }

    @Override
    public void onDisable() {
        if (clanData != null) {
            clanData.save();
        }
        if (clanCommand != null) {
            clanCommand.cancelAllTasks();
        }
        getLogger().info("Plani Clan выключен, данные сохранены.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return true;
    }
}