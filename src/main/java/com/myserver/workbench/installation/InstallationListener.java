package com.myserver.workbench.installation;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class InstallationListener implements Listener {
    private final InstallationManager manager;

    public InstallationListener(InstallationManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getMainHandItem(); // Item that was in main hand
        
        // TODO: Check if 'item' is a custom workbench item
        boolean isWorkbenchItem = item != null && item.hasItemMeta() && item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == 10001;
        
        if (manager.isInstalling(player)) {
            // Cancel installation on second F press
            event.setCancelled(true);
            manager.cancelInstallation(player, true);
        } else if (isWorkbenchItem) {
            // Enter installation mode
            event.setCancelled(true);
            manager.startInstallation(player, item);
        }
    }

    @EventHandler
    public void onScroll(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (manager.isInstalling(player)) {
            event.setCancelled(true);
            
            // Calculate scroll direction
            int prev = event.getPreviousSlot();
            int current = event.getNewSlot();
            
            // Handle wrap-around (0 to 8, 8 to 0)
            int diff = current - prev;
            if (diff == 8) diff = -1;
            else if (diff == -8) diff = 1;
            
            float rotationDelta = diff > 0 ? 45.0f : -45.0f;
            manager.rotate(player, rotationDelta);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (manager.isInstalling(player)) {
            event.setCancelled(true); // Prevent placing vanilla blocks or interacting
            
            if (player.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                // Confirm installation
                manager.confirmInstallation(player);
            }
        }
    }
}
