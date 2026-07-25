package com.myserver.workbench.gui;

import com.myserver.workbench.crafting.CraftingManager;
import com.myserver.workbench.crafting.CraftingSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class QueueUpdateTask extends BukkitRunnable {
    private final CraftingManager manager;

    public QueueUpdateTask(CraftingManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (topInventory.getHolder() instanceof WorkbenchGUI) {
                updateQueueUI(player, topInventory);
            }
        }
    }

    private void updateQueueUI(Player player, Inventory inventory) {
        List<CraftingSession> queue = manager.getPlayerQueue(player.getUniqueId());
        int unlockedSlots = manager.getUnlockedSlots(player.getUniqueId());

        for (int i = 0; i < WorkbenchGUI.QUEUE_SLOTS.length; i++) {
            int slot = WorkbenchGUI.QUEUE_SLOTS[i];
            
            if (i >= unlockedSlots) {
                // Keep locked slots as they are (managed in WorkbenchGUI init)
                continue;
            }

            if (i < queue.size()) {
                CraftingSession session = queue.get(i);
                if (session.checkCompleted()) {
                    // Update to "Completed" state
                    ItemStack completedItem = session.getResultItem().clone();
                    ItemMeta meta = completedItem.getItemMeta();
                    List<Component> lore = meta.hasLore() ? meta.lore() : new java.util.ArrayList<>();
                    lore.add(Component.empty());
                    lore.add(Component.text("클릭하여 수령하기!").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    completedItem.setItemMeta(meta);
                    inventory.setItem(slot, completedItem);
                } else {
                    // Update ticking timer
                    ItemStack craftingItem = session.getResultItem().clone();
                    ItemMeta meta = craftingItem.getItemMeta();
                    
                    // Add timer lore
                    long remainingSeconds = session.getRemainingSeconds();
                    long minutes = remainingSeconds / 60;
                    long seconds = remainingSeconds % 60;
                    String timeStr = String.format("%02d:%02d", minutes, seconds);
                    
                    List<Component> lore = meta.hasLore() ? meta.lore() : new java.util.ArrayList<>();
                    lore.add(Component.empty());
                    lore.add(Component.text("⏳ 남은 시간: " + timeStr).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    craftingItem.setItemMeta(meta);
                    inventory.setItem(slot, craftingItem);
                }
            } else {
                // Empty but unlocked slot
                inventory.setItem(slot, null); // Or a transparent filler item
            }
        }
    }
}
