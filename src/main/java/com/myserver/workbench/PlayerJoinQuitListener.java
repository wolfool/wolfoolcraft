package com.myserver.workbench;

import com.myserver.workbench.crafting.CraftingManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {
    private final CraftingManager craftingManager;

    public PlayerJoinQuitListener(CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Load crafting queue asynchronously if needed, but for SQLite local it's generally fast enough
        craftingManager.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        craftingManager.savePlayer(event.getPlayer().getUniqueId());
    }
}
