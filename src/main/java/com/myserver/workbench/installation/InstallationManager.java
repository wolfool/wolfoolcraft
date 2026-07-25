package com.myserver.workbench.installation;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.myserver.workbench.database.DatabaseManager;

public class InstallationManager {
    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, InstallationSession> activeSessions = new HashMap<>();

    public InstallationManager(Plugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public boolean isInstalling(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public void startInstallation(Player player, ItemStack item) {
        if (isInstalling(player)) return;
        
        int installed = databaseManager.getInstalledCount(player.getUniqueId());
        if (installed >= 5) {
            player.sendMessage("§c[작업대] 최대 설치 개수(5개)를 초과할 수 없습니다.");
            return;
        }

        // Find target block location (e.g. up to 5 blocks away)
        Location targetLoc = player.getTargetBlockExact(5) != null 
            ? player.getTargetBlockExact(5).getLocation().add(0.5, 1.0, 0.5) 
            : player.getLocation().add(player.getLocation().getDirection().multiply(2));
        
        targetLoc.setYaw(0);
        targetLoc.setPitch(0);

        ItemDisplay display = (ItemDisplay) player.getWorld().spawnEntity(targetLoc, EntityType.ITEM_DISPLAY);
        display.setItemStack(item);
        display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED); // We want it fixed, not facing player
        
        // Disable interactions
        display.setInvulnerable(true);
        display.setGravity(false);
        
        InstallationSession session = new InstallationSession(display, item);
        activeSessions.put(player.getUniqueId(), session);
        
        player.sendMessage("§a[작업대] 설치 모드에 진입했습니다.");
        player.sendMessage("§7- 휠 스크롤: 45도 회전");
        player.sendMessage("§7- Shift + 우클릭: 설치 확정");
        player.sendMessage("§7- F키: 설치 취소");
    }

    public void rotate(Player player, float deltaDegrees) {
        InstallationSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.rotate(deltaDegrees);
            // Removed rotation angle message per user request
        }
    }

    public void confirmInstallation(Player player) {
        InstallationSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            Location loc = session.getDisplayEntity().getLocation();
            float yaw = session.getCurrentYaw();
            
            // TODO: Call CraftEngine API to place the furniture at 'loc' with 'yaw'
            // CraftEngineAPI.placeFurniture("myserver:crafting_workbench", loc, yaw);
            
            player.sendMessage("§a[작업대] 설치가 완료되었습니다.");
            
            // Consume item
            player.getInventory().removeItem(session.getItem().asOne());
            
            databaseManager.incrementInstalledCount(player.getUniqueId());
            
            cancelInstallation(player, false);
        }
    }

    public void cancelInstallation(Player player, boolean showMessage) {
        InstallationSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.cleanup();
            if (showMessage) {
                player.sendMessage("§c[작업대] 설치 모드가 취소되었습니다.");
            }
        }
    }
    
    public InstallationSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }
}
