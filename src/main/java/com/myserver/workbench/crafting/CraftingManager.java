package com.myserver.workbench.crafting;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import com.myserver.workbench.database.DatabaseManager;

public class CraftingManager {
    // Player UUID -> List of active crafting sessions
    private final Map<UUID, List<CraftingSession>> playerQueues = new HashMap<>();
    
    private final DatabaseManager databaseManager;

    public CraftingManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    public int getUnlockedSlots(UUID playerId) {
        return databaseManager.getUnlockedSlots(playerId);
    }
    
    public void unlockSlot(UUID playerId) {
        int current = getUnlockedSlots(playerId);
        if (current < 9) {
            databaseManager.updateUnlockedSlots(playerId, current + 1);
        }
    }
    
    public void loadPlayer(UUID playerId) {
        List<CraftingSession> loaded = databaseManager.loadSessions(playerId);
        playerQueues.put(playerId, loaded);
    }
    
    public void savePlayer(UUID playerId) {
        List<CraftingSession> queue = playerQueues.get(playerId);
        if (queue != null) {
            for (CraftingSession session : queue) {
                databaseManager.saveSession(playerId, session);
            }
            playerQueues.remove(playerId);
        }
    }

    public List<CraftingSession> getPlayerQueue(UUID playerId) {
        return playerQueues.computeIfAbsent(playerId, k -> new ArrayList<>());
    }

    public boolean startCrafting(Player player, ItemStack resultItem, long durationTicks) {
        List<CraftingSession> queue = getPlayerQueue(player.getUniqueId());
        
        // Count active items (not collected)
        long activeCount = queue.stream().filter(s -> !s.isCollected()).count();
        int limit = getUnlockedSlots(player.getUniqueId());
        
        if (activeCount >= limit) {
            player.sendMessage("§c[작업대] 대기열 슬롯이 가득 찼습니다.");
            return false;
        }
        
        CraftingSession session = new CraftingSession(resultItem, durationTicks);
        queue.add(session);
        return true;
    }

    public void collectItem(Player player, UUID sessionId) {
        List<CraftingSession> queue = getPlayerQueue(player.getUniqueId());
        for (CraftingSession session : queue) {
            if (session.getId().equals(sessionId) && session.checkCompleted() && !session.isCollected()) {
                session.setCollected(true);
                // Give item to player
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(session.getResultItem());
                if (!leftover.isEmpty()) {
                    player.getWorld().dropItem(player.getLocation(), leftover.get(0));
                }
                player.sendMessage("§a[작업대] 제작품을 수령했습니다!");
                break;
            }
        }
        // Cleanup collected items
        queue.removeIf(CraftingSession::isCollected);
    }
}
