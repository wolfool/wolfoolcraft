package com.wolfool.workbench.crafting;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class CraftingSession {
    private final UUID id;
    private final ItemStack resultItem;
    private final long startTime;
    private final long endTime;
    private boolean isCompleted;
    private boolean isCollected;

    public CraftingSession(ItemStack resultItem, long durationTicks) {
        this.id = UUID.randomUUID();
        this.resultItem = resultItem;
        this.startTime = System.currentTimeMillis();
        // 20 ticks = 1 second
        this.endTime = this.startTime + (durationTicks * 50L);
        this.isCompleted = false;
        this.isCollected = false;
    }
    
    public CraftingSession(UUID id, ItemStack resultItem, long endTime) {
        this.id = id;
        this.resultItem = resultItem;
        this.startTime = System.currentTimeMillis(); // Irrelevant when loaded from DB
        this.endTime = endTime;
        this.isCompleted = false;
        this.isCollected = false;
    }

    public UUID getId() {
        return id;
    }

    public ItemStack getResultItem() {
        return resultItem;
    }

    public long getEndTime() {
        return endTime;
    }

    public boolean checkCompleted() {
        if (!isCompleted && System.currentTimeMillis() >= endTime) {
            isCompleted = true;
        }
        return isCompleted;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public boolean isCollected() {
        return isCollected;
    }

    public void setCollected(boolean collected) {
        isCollected = collected;
    }

    public long getRemainingSeconds() {
        long remainingMillis = endTime - System.currentTimeMillis();
        if (remainingMillis < 0) return 0;
        return remainingMillis / 1000L;
    }
}
