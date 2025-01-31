package org.kray.glowItem;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.EulerAngle;

import java.util.HashMap;
import java.util.UUID;

public class GlowItem extends JavaPlugin implements Listener {

    private final HashMap<UUID, ArmorStand> armorStandMap = new HashMap<>();
    private Scoreboard scoreboard;
    private Material destroyItem;

    @Override
    public void onEnable() {
        getLogger().info("GlowingItemPlugin включен!");
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        destroyItem = Material.matchMaterial(config.getString("destroy-item", "SHEARS"));
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("GlowingItemPlugin выключен!");
        for (ArmorStand armorStand : armorStandMap.values()) {
            if (armorStand != null && !armorStand.isDead()) {
                armorStand.remove();
            }
        }
        armorStandMap.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("glowitem")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эту команду могут использовать только игроки!");
                return true;
            }

            Player player = (Player) sender;

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                player.sendMessage(ChatColor.RED + "Вы должны держать предмет в руке!");
                return true;
            }

            double angle = -10;
            double size = 1.0;
            String color = "WHITE";

            if (args.length >= 1) {
                try {
                    angle = Double.parseDouble(args[0]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Наклон должен быть числом!");
                    return true;
                }
            }

            if (args.length >= 2) {
                try {
                    size = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Размер должен быть числом!");
                    return true;
                }
            }

            if (args.length >= 3) {
                color = args[2].toUpperCase();
                if (!isValidColor(color)) {
                    player.sendMessage(ChatColor.RED + "Недопустимый цвет! Доступные цвета: RED, GREEN, BLUE, YELLOW, WHITE, PURPLE.");
                    return true;
                }
            }

            Location location = player.getLocation();
            ArmorStand armorStand = spawnGlowingArmorStand(location, item, angle, size, color);

            if (armorStand != null) {
                removeItemFromPlayerHand(player);
                armorStandMap.put(player.getUniqueId(), armorStand);
                player.sendMessage(ChatColor.GREEN + "Эффект свечения создан! Наклон: " + angle + ", Размер: " + size + ", Цвет: " + color);
            } else {
                player.sendMessage(ChatColor.RED + "Произошла ошибка при добавлении свечения.");
            }

            return true;
        } else if (label.equalsIgnoreCase("glowclear")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эту команду могут использовать только игроки!");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("glowitem.clear")) {
                player.sendMessage(ChatColor.RED + "У вас нет прав для использования этой команды!");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Использование: /glowclear <радиус>");
                return true;
            }

            double radius;
            try {
                radius = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Радиус должен быть числом!");
                return true;
            }

            int clearedCount = clearArmorStands(player.getLocation(), radius);
            player.sendMessage(ChatColor.GREEN + "Очищено " + clearedCount + " ArmorStand в радиусе " + radius + " блоков.");
            return true;
        } else if (label.equalsIgnoreCase("destroyglow")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эту команду могут использовать только игроки!");
                return true;
            }

            Player player = (Player) sender;

            ArmorStand targetStand = getTargetArmorStand(player);
            if (targetStand == null) {
                player.sendMessage(ChatColor.RED + "Вы не смотрите на ArmorStand!");
                return true;
            }

            dropItemFromArmorStand(targetStand);
            targetStand.remove();
            player.sendMessage(ChatColor.GREEN + "ArmorStand успешно удалён!");
            return true;
        }

        return false;
    }

    private int clearArmorStands(Location location, double radius) {
        int clearedCount = 0;
        for (ArmorStand armorStand : location.getWorld().getEntitiesByClass(ArmorStand.class)) {
            if (armorStand.getLocation().distance(location) <= radius) {
                armorStand.remove();
                clearedCount++;
            }
        }
        return clearedCount;
    }


    private ArmorStand spawnGlowingArmorStand(Location location, ItemStack item, double angle, double size, String color) {
        ArmorStand armorStand = location.getWorld().spawn(location, ArmorStand.class);
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setSmall(size <= 1.0);
        armorStand.setMarker(true);
        armorStand.setItemInHand(item.clone());
        armorStand.setGlowing(true);

        EulerAngle eulerAngle = new EulerAngle(Math.toRadians(angle), 0, 0);
        armorStand.setRightArmPose(eulerAngle);
        setGlowingColor(armorStand, color);
        armorStand.setCustomName("owner_" + armorStand.getUniqueId());

        return armorStand;
    }

    private void removeItemFromPlayerHand(Player player) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private void setGlowingColor(ArmorStand armorStand, String color) {
        Team team = scoreboard.getTeam(color);
        if (team == null) {
            team = scoreboard.registerNewTeam(color);
            team.setColor(ChatColor.valueOf(color));
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        team.addEntry(armorStand.getUniqueId().toString());
    }

    private boolean isValidColor(String color) {
        try {
            ChatColor.valueOf(color);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private ArmorStand getTargetArmorStand(Player player) {
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof ArmorStand) {
                ArmorStand armorStand = (ArmorStand) entity;
                if (armorStand.getLocation().distance(player.getEyeLocation()) <= 5 &&
                        armorStand.getLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize()
                                .dot(player.getEyeLocation().getDirection()) > 0.99) {
                    return armorStand;
                }
            }
        }
        return null;
    }
    private void dropItemFromArmorStand(ArmorStand armorStand) {
        ItemStack item = armorStand.getItemInHand();
        if (item != null && !item.getType().isAir()) {
            ItemStack singleItem = item.clone();
            singleItem.setAmount(1);
            armorStand.getWorld().dropItemNaturally(armorStand.getLocation(), singleItem);
        }
    }
}
