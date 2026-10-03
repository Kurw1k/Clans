package org.kurw1k.clans;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClanCommand implements CommandExecutor, Listener, TabCompleter {
    private final Clans plugin;
    private final Map<UUID, BukkitTask> inviteTasks = new HashMap<>();
    private static final Map<UUID, Integer> cursorIndex = new HashMap<>();

    public ClanCommand(Clans plugin) {
        this.plugin = plugin;
    }

    public void cancelAllTasks() {
        for (BukkitTask task : inviteTasks.values()) {
            task.cancel();
        }
        inviteTasks.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadConfig();
                sender.sendMessage("§a[Clans] Конфигурация успешно перезагружена!");
                return true;
            }
            sender.sendMessage("Только для игроков!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cИспользование: /clan [create|delete|invite|join|storage|info|pvp|invest|withdraw|money|members|reload]");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload": {
                if (!player.hasPermission("clans.admin")) {
                    player.sendMessage("§cУ вас нет прав на выполнение этой команды!");
                    return true;
                }
                plugin.reloadConfig();
                player.sendMessage("§aКонфигурация плагина §eClans §aуспешно перезагружена!");
                break;
            }
            case "create": {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /clan create <название>");
                    return true;
                }
                if (plugin.clanData.playerClans.containsKey(player.getUniqueId())) {
                    player.sendMessage("§cВы уже в клане!");
                    return true;
                }
                String name = args[1];
                int min = plugin.getConfig().getInt("settings.min-name-length", 3);
                int max = plugin.getConfig().getInt("settings.max-name-length", 16);

                if (name.length() < min || name.length() > max) {
                    player.sendMessage("§cНазвание клана должно быть от " + min + " до " + max + " символов!");
                    return true;
                }

                if (plugin.clanData.clans.containsKey(name.toLowerCase())) {
                    player.sendMessage("§cКлан с таким названием уже существует!");
                    return true;
                }

                double cost = plugin.getConfig().getDouble("settings.creation-cost", 25000.0);
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());

                if (Clans.econ != null) {
                    if (!Clans.econ.has(offlinePlayer, cost)) {
                        player.sendMessage("§cУ вас недостаточно денег для создания клана! Требуется: §e" + cost);
                        return true;
                    }
                    Clans.econ.withdrawPlayer(offlinePlayer, cost);
                } else {
                    player.sendMessage("§cОшибка: Экономика не подключена!");
                    return true;
                }

                boolean defaultPvp = plugin.getConfig().getBoolean("settings.allow-clan-pvp-default", false);
                ClanData.Clan clan = new ClanData.Clan(name, player.getUniqueId(), new ArrayList<>(), 0.0, 1, defaultPvp, 0, new HashMap<>());
                plugin.clanData.clans.put(name.toLowerCase(), clan);
                plugin.clanData.playerClans.put(player.getUniqueId(), name.toLowerCase());
                plugin.clanData.save();

                player.sendMessage("§aКлан успешно создан! С вашего счета списано §e" + cost);
                break;
            }
            case "delete": {
                String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
                if (clanName == null) {
                    player.sendMessage("§cВы не в клане!");
                    return true;
                }
                ClanData.Clan clan = plugin.clanData.clans.get(clanName);
                if (!clan.leader.equals(player.getUniqueId())) {
                    player.sendMessage("§cТолько лидер может удалить клан!");
                    return true;
                }

                plugin.clanData.playerClans.remove(clan.leader);
                for (UUID m : clan.members) {
                    plugin.clanData.playerClans.remove(m);
                }
                plugin.clanData.clans.remove(clanName);
                plugin.clanData.save();

                player.sendMessage("§cКлан был удален.");
                break;
            }
            case "invite": {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /clan invite <игрок>");
                    return true;
                }
                String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
                if (clanName == null) {
                    player.sendMessage("§cВы не в клане!");
                    return true;
                }
                ClanData.Clan clan = plugin.clanData.clans.get(clanName);
                if (!clan.hasPermission(player.getUniqueId(), "invite")) {
                    player.sendMessage("§cУ вас нет прав на приглашение игроков!");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cИгрок не найден или оффлайн!");
                    return true;
                }

                if (plugin.clanData.playerClans.containsKey(target.getUniqueId())) {
                    player.sendMessage("§cЭтот игрок уже состоит в клане!");
                    return true;
                }

                if (plugin.clanData.invites.containsKey(target.getUniqueId())) {
                    player.sendMessage("§cУ этого игрока уже есть активное приглашение в клан!");
                    return true;
                }

                plugin.clanData.invites.put(target.getUniqueId(), clanName);
                player.sendMessage("§aПриглашение отправлено игроку §e" + target.getName() + "§a! Срок действия: 30 секунд.");
                target.sendMessage("§aВас пригласили в клан §e" + clanName + "§a! Введите §e/clan join §aчтобы принять. §7(Действует 30 секунд)");

                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (plugin.clanData.invites.containsKey(target.getUniqueId()) && plugin.clanData.invites.get(target.getUniqueId()).equals(clanName)) {
                        plugin.clanData.invites.remove(target.getUniqueId());
                        inviteTasks.remove(target.getUniqueId());

                        if (player.isOnline()) {
                            player.sendMessage("§cСрок приглашения игрока §e" + target.getName() + " §cв клан истек.");
                        }
                    }
                }, 600L);

                inviteTasks.put(target.getUniqueId(), task);
                break;
            }
            case "join": {
                String cName = plugin.clanData.invites.get(player.getUniqueId());
                if (cName == null) {
                    player.sendMessage("§cУ вас нет активных приглашений!");
                    return true;
                }
                ClanData.Clan clan = plugin.clanData.clans.get(cName);
                int maxMem = plugin.getConfig().getInt("settings.max-members", 10);
                if (clan.members.size() + 1 > maxMem) {
                    player.sendMessage("§cВ клане достигнут лимит участников!");
                    return true;
                }

                if (inviteTasks.containsKey(player.getUniqueId())) {
                    inviteTasks.get(player.getUniqueId()).cancel();
                    inviteTasks.remove(player.getUniqueId());
                }

                clan.members.add(player.getUniqueId());
                plugin.clanData.playerClans.put(player.getUniqueId(), cName);
                plugin.clanData.invites.remove(player.getUniqueId());
                plugin.clanData.save();

                player.sendMessage("§aВы вступили в клан §e" + clan.name + "§a!");

                String joinMsg = "§6Клан > §e" + player.getName() + " §aприсоединился к клану!";

                Player leaderPlayer = Bukkit.getPlayer(clan.leader);
                if (leaderPlayer != null && leaderPlayer.isOnline()) {
                    leaderPlayer.sendMessage(joinMsg);
                }

                for (UUID memberUuid : clan.members) {
                    if (memberUuid.equals(player.getUniqueId()) || memberUuid.equals(clan.leader)) continue;
                    Player memberPlayer = Bukkit.getPlayer(memberUuid);
                    if (memberPlayer != null && memberPlayer.isOnline()) {
                        memberPlayer.sendMessage(joinMsg);
                    }
                }
                break;
            }
            case "storage": {
                String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
                if (clanName == null) {
                    player.sendMessage("§cВы не в клане!");
                    return true;
                }
                ClanData.Clan clan = plugin.clanData.clans.get(clanName);
                if (!clan.hasPermission(player.getUniqueId(), "storage_open")) {
                    player.sendMessage("§cУ вас нет прав на открытие склада клана!");
                    return true;
                }
                player.openInventory(clan.getStorageInventory(plugin));
                break;
            }
            case "pvp": {
                String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
                if (clanName == null) return true;
                ClanData.Clan clan = plugin.clanData.clans.get(clanName);
                if (!clan.leader.equals(player.getUniqueId())) {
                    player.sendMessage("§cТолько лидер может менять настройки ПВП!");
                    return true;
                }
                clan.pvp = !clan.pvp;
                plugin.clanData.save();
                player.sendMessage("§aПВП между соклановцами теперь: " + (clan.pvp ? "§2ВКЛ" : "§cВЫКЛ"));
                break;
            }
            case "invest": {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /clan invest <сумма>");
                    return true;
                }
                String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
                if (clanName == null) {
                    player.sendMessage("§cВы не в клане!");
                    return true;
                }
                ClanData.Clan clan = plugin.clanData.clans.get(clanName);
                if (!clan.hasPermission(player.getUniqueId(), "invest")) {
                    player.sendMessage("§cУ вас нет прав на пополнение казны клана!");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cНеверная сумма!");
                    return true;
                }
                if (amount <= 0) {
                    player.sendMessage("§cСумма должна быть больше нуля!");
                    return true;
                }

                if (Clans.econ != null) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
                    if (!Clans.econ.has(offlinePlayer, amount)) {
                        player.sendMessage("§cУ вас недостаточно денег на личной счете!");
                        return true;
                    }
                    Clans.econ.withdrawPlayer(offlinePlayer, amount);
                } else {
                    player.sendMessage("§cОшибка: Экономика не подключена!");
                    return true;
                }

                clan.balance += amount;
                plugin.clanData.save();
                player.sendMessage("§aВы внесли §e" + amount + " §aна баланс клана!");
                break;
            }
            case "withdraw": {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /clan withdraw <сумма|max>");
                    return true;
                }
                String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
                if (clanName == null) {
                    player.sendMessage("§cВы не в клане!");
                    return true;
                }
                ClanData.Clan clan = plugin.clanData.clans.get(clanName);
                if (!clan.hasPermission(player.getUniqueId(), "withdraw")) {
                    player.sendMessage("§cУ вас нет прав на снятие денег с казны клана!");
                    return true;
                }

                double amount;
                if (args[1].equalsIgnoreCase("max")) {
                    amount = clan.balance;
                } else {
                    try {
                        amount = Double.parseDouble(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cНеверная сумма!");
                        return true;
                    }
                }

                if (amount <= 0) {
                    player.sendMessage("§cСумма должна быть больше нуля!");
                    return true;
                }
                if (clan.balance < amount) {
                    player.sendMessage("§cВ клане недостаточно средств!");
                    return true;
                }

                clan.balance -= amount;

                if (Clans.econ != null) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
                    Clans.econ.depositPlayer(offlinePlayer, amount);
                } else {
                    player.sendMessage("§cОшибка: Экономика не подключена!");
                    return true;
                }

                plugin.clanData.save();
                player.sendMessage("§aВы сняли §e" + amount + " §aс баланса клана!");
                break;
            }
            case "money": {
                String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
                if (clanName == null) {
                    player.sendMessage("§cВы не в клане!");
                    return true;
                }
                ClanData.Clan clan = plugin.clanData.clans.get(clanName);
                player.sendMessage("§6Баланс казны клана §e" + clan.name + "§6: §f" + clan.balance);
                break;
            }
            case "members": {
                String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
                if (clanName == null) {
                    player.sendMessage("§cВы не в клане!");
                    return true;
                }
                ClanData.Clan clan = plugin.clanData.clans.get(clanName);
                openMembersGui(player, clan, plugin);
                break;
            }
            case "info": {
                String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
                if (args.length > 1) clanName = args[1].toLowerCase();
                if (clanName == null || !plugin.clanData.clans.containsKey(clanName)) {
                    player.sendMessage("§cКлан не найден!");
                    return true;
                }
                ClanData.Clan c = plugin.clanData.clans.get(clanName);
                player.sendMessage("§6--- Клан: " + c.name + " ---");
                player.sendMessage("§eУровень: §f" + c.level);
                player.sendMessage("§eБаланс: §f" + c.balance);
                player.sendMessage("§eУчастников: §f" + (c.members.size() + 1));
                player.sendMessage("§eСломано/поставлено блоков: §f" + c.blocks);
                break;
            }
            default:
                player.sendMessage("§cНеизвестная подкоманда.");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(List.of("create", "delete", "invite", "join", "storage", "info", "pvp", "invest", "withdraw", "money", "members"));
            if (sender.hasPermission("clans.admin") || !sender.isOp()) {
                subCommands.add("reload");
            }
            String input = args[0].toLowerCase();
            for (String sub : subCommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && sender instanceof Player) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite")) {
                String input = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) {
                        completions.add(p.getName());
                    }
                }
            } else if (sub.equals("info")) {
                String input = args[1].toLowerCase();
                for (String cName : plugin.clanData.clans.keySet()) {
                    if (cName.startsWith(input)) {
                        completions.add(cName);
                    }
                }
            } else if (sub.equals("withdraw")) {
                completions.add("max");
            }
        }
        return completions;
    }

    public static void openMembersGui(Player player, ClanData.Clan clan, Clans plugin) {
        Inventory inv = Bukkit.createInventory(null, 27, "§1Участники клана");
        int slot = 0;
        int cursor = cursorIndex.getOrDefault(player.getUniqueId(), 0);

        OfflinePlayer leaderOp = Bukkit.getOfflinePlayer(clan.leader);
        ItemStack leaderHead = createMemberSkull(leaderOp, "§6Лидер: " + (leaderOp.getName() != null ? leaderOp.getName() : "Лидер"), true, true, true, true, true, true, true, 0);
        inv.setItem(slot++, leaderHead);

        for (UUID mUuid : clan.members) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(mUuid);
            boolean invite = clan.hasPermission(mUuid, "invite");
            boolean invest = clan.hasPermission(mUuid, "invest");
            boolean withdraw = clan.hasPermission(mUuid, "withdraw");
            boolean storageOpen = clan.hasPermission(mUuid, "storage_open");
            boolean storageDeposit = clan.hasPermission(mUuid, "storage_deposit");
            boolean storageWithdraw = clan.hasPermission(mUuid, "storage_withdraw");
            boolean kick = clan.hasPermission(mUuid, "kick");

            ItemStack head = createMemberSkull(op, "§e" + (op.getName() != null ? op.getName() : "Игрок"), invite, invest, withdraw, storageOpen, storageDeposit, storageWithdraw, kick, cursor);
            inv.setItem(slot++, head);
            if (slot >= 27) break;
        }
        player.openInventory(inv);
    }

    private static ItemStack createMemberSkull(OfflinePlayer op, String name, boolean invite, boolean invest, boolean withdraw, boolean storageOpen, boolean storageDeposit, boolean storageWithdraw, boolean kick, int cursor) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(op);
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Права:");

            lore.add((cursor == 0 ? "§e* " : "  ") + "§7Приглашения: " + (invite ? "§aВКЛ" : "§cВЫКЛ"));
            lore.add((cursor == 1 ? "§e* " : "  ") + "§7Пополнение казны: " + (invest ? "§aВКЛ" : "§cВЫКЛ"));
            lore.add((cursor == 2 ? "§e* " : "  ") + "§7Снятие с казны: " + (withdraw ? "§aВКЛ" : "§cВЫКЛ"));
            lore.add((cursor == 3 ? "§e* " : "  ") + "§7Открытие склада: " + (storageOpen ? "§aВКЛ" : "§cВЫКЛ"));
            lore.add((cursor == 4 ? "§e* " : "  ") + "§7Пополнение склада: " + (storageDeposit ? "§aВКЛ" : "§cВЫКЛ"));
            lore.add((cursor == 5 ? "§e* " : "  ") + "§7Забрать со склада: " + (storageWithdraw ? "§aВКЛ" : "§cВЫКЛ"));
            lore.add((cursor == 6 ? "§e* " : "  ") + "§7Кик игроков: " + (kick ? "§aВКЛ" : "§cВЫКЛ"));

            lore.add("");
            lore.add("§eПКМ §7- переместить звездочку (*)");
            lore.add("§eЛКМ §7- переключить право");
            lore.add("§cShift + ЛКМ §7- кикнуть");
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.startsWith("§1Склад клана:")) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.RED_STAINED_GLASS_PANE) {
                event.setCancelled(true);
                return;
            }

            String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
            if (clanName == null) {
                event.setCancelled(true);
                return;
            }
            ClanData.Clan clan = plugin.clanData.clans.get(clanName);

            if (!clan.hasPermission(player.getUniqueId(), "storage_open")) {
                event.setCancelled(true);
                player.sendMessage("§cУ вас нет прав на открытие склада клана!");
                return;
            }

            int slot = event.getRawSlot();
            if (slot >= 0 && slot < 54) {
                if (slot >= clan.getUnlockedSlots(plugin)) {
                    event.setCancelled(true);
                    player.sendMessage("§cЭтот слот заблокирован! Повысьте уровень клана.");
                    return;
                }

                boolean isPlacing = (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)
                        && (event.getCursor() != null && event.getCursor().getType() != Material.AIR);
                boolean isTaking = (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR);

                if (isPlacing && !clan.hasPermission(player.getUniqueId(), "storage_deposit")) {
                    event.setCancelled(true);
                    player.sendMessage("§cУ вас нет прав на пополнение склада клана!");
                } else if (isTaking && !clan.hasPermission(player.getUniqueId(), "storage_withdraw")) {
                    if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                        event.setCancelled(true);
                        player.sendMessage("§cУ вас нет прав забирать предметы со склада клана!");
                    }
                }
            }
            return;
        }

        if (title.equals("§1Участники клана")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
            if (clanName == null) return;
            ClanData.Clan clan = plugin.clanData.clans.get(clanName);

            boolean isLeader = clan.leader.equals(player.getUniqueId());
            boolean hasKickPerm = clan.hasPermission(player.getUniqueId(), "kick");

            if (!(clicked.getItemMeta() instanceof SkullMeta meta)) return;
            if (meta.getOwningPlayer() == null) return;
            OfflinePlayer targetOp = meta.getOwningPlayer();
            UUID targetUuid = targetOp.getUniqueId();

            if (targetUuid.equals(clan.leader)) {
                player.sendMessage("§cВы не можете изменять настройки лидера!");
                return;
            }

            if (!clan.members.contains(targetUuid)) {
                player.sendMessage("§cЭтот игрок не состоит в вашем клане!");
                return;
            }

            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                if (!isLeader && !hasKickPerm) {
                    player.sendMessage("§cУ вас нет прав на изгнание игроков из клана!");
                    return;
                }

                clan.members.remove(targetUuid);
                plugin.clanData.playerClans.remove(targetUuid);
                plugin.clanData.save();

                player.sendMessage("§cИгрок §e" + targetOp.getName() + " §cбыл изгнан из клана.");
                if (targetOp.isOnline() && targetOp.getPlayer() != null) {
                    targetOp.getPlayer().sendMessage("§cВы были изгнаны из клана §e" + clan.name + "§c!");
                }
                openMembersGui(player, clan, plugin);
                return;
            }

            if (!isLeader) {
                player.sendMessage("§cТолько лидер может изменять права участников!");
                return;
            }

            cursorIndex.putIfAbsent(player.getUniqueId(), 0);
            int currentCursor = cursorIndex.get(player.getUniqueId());

            if (event.getClick().isRightClick()) {
                currentCursor = (currentCursor + 1) % 7;
                cursorIndex.put(player.getUniqueId(), currentCursor);
                openMembersGui(player, clan, plugin);
            } else if (event.getClick().isLeftClick()) {
                String[] perms = {"invite", "invest", "withdraw", "storage_open", "storage_deposit", "storage_withdraw", "kick"};
                String selectedPerm = perms[currentCursor];

                if (!clan.permissions.containsKey(targetUuid) || clan.permissions.get(targetUuid) == null) {
                    clan.permissions.put(targetUuid, new HashMap<>());
                }

                Map<String, Boolean> playerPerms = clan.permissions.get(targetUuid);
                boolean currentVal = clan.hasPermission(targetUuid, selectedPerm);
                boolean nextVal = !currentVal;

                playerPerms.put(selectedPerm, nextVal);
                plugin.clanData.save();

                openMembersGui(player, clan, plugin);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.startsWith("§1Склад клана:")) {
            String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
            if (clanName == null) return;
            ClanData.Clan clan = plugin.clanData.clans.get(clanName);

            Inventory inv = event.getInventory();
            int unlocked = clan.getUnlockedSlots(plugin);

            for (int i = 0; i < 54; i++) {
                if (i < unlocked) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() == Material.RED_STAINED_GLASS_PANE) {
                        clan.storageContents[i] = null;
                    } else {
                        clan.storageContents[i] = item;
                    }
                }
            }
            plugin.clanData.save();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
        if (clanName == null) return;

        ClanData.Clan clan = plugin.clanData.clans.get(clanName);
        if (clan != null) {
            clan.blocks++;
            checkAndUpdateLevel(clan, player);
            plugin.clanData.save();
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String clanName = plugin.clanData.playerClans.get(player.getUniqueId());
        if (clanName == null) return;

        ClanData.Clan clan = plugin.clanData.clans.get(clanName);
        if (clan != null) {
            clan.blocks++;
            checkAndUpdateLevel(clan, player);
            plugin.clanData.save();
        }
    }

    private void checkAndUpdateLevel(ClanData.Clan clan, Player player) {
        ConfigurationSection levelsSection = plugin.getConfig().getConfigurationSection("levels");
        if (levelsSection == null) return;

        while (true) {
            int nextLevel = clan.level + 1;
            if (!levelsSection.contains(String.valueOf(nextLevel))) {
                break;
            }

            int requiredBlocks = plugin.getConfig().getInt("levels." + nextLevel + ".cost-blocks");
            if (clan.blocks >= requiredBlocks) {
                clan.level = nextLevel;

                String msg = "§6Клан > §aВаш клан был повышен до §e" + clan.level + "§а уровня!";
                player.sendMessage(msg);

                Player leaderPlayer = Bukkit.getPlayer(clan.leader);
                if (leaderPlayer != null && leaderPlayer.isOnline() && !leaderPlayer.equals(player)) {
                    leaderPlayer.sendMessage(msg);
                }

                for (UUID memberUuid : clan.members) {
                    if (memberUuid.equals(player.getUniqueId()) || memberUuid.equals(clan.leader)) continue;
                    Player memberPlayer = Bukkit.getPlayer(memberUuid);
                    if (memberPlayer != null && memberPlayer.isOnline()) {
                        memberPlayer.sendMessage(msg);
                    }
                }
            } else {
                break;
            }
        }
    }
}