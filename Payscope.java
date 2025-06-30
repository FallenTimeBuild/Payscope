package dev.BEEPEX.Payscope;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Payscope extends JavaPlugin implements CommandExecutor, TabCompleter {

    private final HashMap<UUID, Double> balances = new HashMap<>();
    private File balancesFile;
    private FileConfiguration balancesConfig;

    @Override
    public void onEnable() {
        setupBalanceFile();

        PluginCommand command = this.getCommand("money");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        loadBalances();
        getLogger().info("Payscope enabled!");
    }

    @Override
    public void onDisable() {
        saveBalances();
        getLogger().info("Payscope disabled!");
    }

    private void setupBalanceFile() {
        balancesFile = new File(getDataFolder(), "balances.yml");
        if (!balancesFile.exists()) {
            balancesFile.getParentFile().mkdirs();
            try {
                balancesFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Failed to create balances.yml");
            }
        }
        balancesConfig = YamlConfiguration.loadConfiguration(balancesFile);
    }

    private void loadBalances() {
        for (String key : balancesConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                double balance = balancesConfig.getDouble(key);
                balances.put(uuid, balance);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid UUID in balances.yml: " + key);
            }
        }
    }

    private void saveBalances() {
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            balancesConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            balancesConfig.save(balancesFile);
        } catch (IOException e) {
            getLogger().severe("Could not save balances.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /money <view|pay|set> <player> [amount]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "view":
                return handleView(sender, args);
            case "pay":
                return handlePay(sender, args);
            case "set":
                return handleSet(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use view, pay, or set.");
                return true;
        }
    }

    private boolean handleView(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /money view <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        double balance = balances.getOrDefault(target.getUniqueId(), 0.0);
        sender.sendMessage(ChatColor.GREEN + target.getName() + "'s balance: $" + balance);
        return true;
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /money pay <player> <amount>");
            return true;
        }

        Player senderPlayer = (Player) sender;
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        if (target.getUniqueId().equals(senderPlayer.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You cannot pay yourself.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount.");
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be positive.");
            return true;
        }

        UUID senderUUID = senderPlayer.getUniqueId();
        UUID targetUUID = target.getUniqueId();
        double senderBalance = balances.getOrDefault(senderUUID, 0.0);

        if (senderBalance < amount) {
            sender.sendMessage(ChatColor.RED + "Insufficient funds.");
            return true;
        }

        balances.put(senderUUID, senderBalance - amount);
        balances.put(targetUUID, balances.getOrDefault(targetUUID, 0.0) + amount);
        saveBalances();

        sender.sendMessage(ChatColor.GREEN + "You sent $" + amount + " to " + target.getName() + ".");
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(ChatColor.GREEN + "You received $" + amount + " from " + senderPlayer.getName() + ".");
        }

        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("money.set")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /money set <player> <amount>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount.");
            return true;
        }

        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "Balance cannot be negative.");
            return true;
        }

        balances.put(target.getUniqueId(), amount);
        saveBalances();

        sender.sendMessage(ChatColor.GREEN + target.getName() + "'s balance set to $" + amount);
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(ChatColor.YELLOW + "Your balance was set to $" + amount);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("money")) return Collections.emptyList();
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("view", "pay", "set");
            for (String sub : subcommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() != null && player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!completions.contains(player.getName()) &&
                        player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("pay") || args[0].equalsIgnoreCase("set"))) {
            if ("100".startsWith(args[2])) completions.add("100");
            if ("1000".startsWith(args[2])) completions.add("1000");
        }

        return completions;
    }
}
