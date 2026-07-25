package com.myserver.workbench.installation;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class InstallationSession {
    private final ItemDisplay displayEntity;
    private final ItemStack item;
    private float currentYaw; // in degrees: 0, 45, 90, 135, etc.

    public InstallationSession(ItemDisplay displayEntity, ItemStack item) {
        this.displayEntity = displayEntity;
        this.item = item;
        this.currentYaw = 0.0f;
    }

    public ItemDisplay getDisplayEntity() {
        return displayEntity;
    }

    public ItemStack getItem() {
        return item;
    }

    public float getCurrentYaw() {
        return currentYaw;
    }

    public void rotate(float deltaDegrees) {
        this.currentYaw = (this.currentYaw + deltaDegrees) % 360;
        if (this.currentYaw < 0) this.currentYaw += 360;
        
        // Update transformation
        Location loc = displayEntity.getLocation();
        loc.setYaw(currentYaw);
        displayEntity.teleport(loc);
        
        // Alternatively using Display.Transformation if you prefer JOML rotations, 
        // but teleporting with yaw is easier for simple rotation.
    }
    
    public void updateLocation(Location newLoc) {
        newLoc.setYaw(currentYaw);
        displayEntity.teleport(newLoc);
    }

    public void cleanup() {
        if (displayEntity != null && !displayEntity.isDead()) {
            displayEntity.remove();
        }
    }
}
