package com.wolfool.workbench.installation;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 설치 모드 하나. 미리보기 엔티티와 지금 잡은 방향을 들고 있다.
 *
 * <p>미리보기는 {@link ItemDisplay} 다. 놓을 수 있으면 초록, 못 놓으면 빨강으로 빛나서
 * 확정하기 전에 결과를 알 수 있다.
 */
public class InstallationSession {

    private static final Color OK = Color.fromRGB(0x55FF55);
    private static final Color BLOCKED = Color.fromRGB(0xFF5555);

    private final ItemDisplay displayEntity;
    private final ItemStack item;
    private float currentYaw;
    private boolean blocked;

    private InstallationSession(ItemDisplay displayEntity, ItemStack item, float yaw) {
        this.displayEntity = displayEntity;
        this.item = item;
        this.currentYaw = yaw;
    }

    /**
     * 미리보기를 띄우고 세션을 만든다.
     *
     * <p>처음 방향은 플레이어가 보는 쪽으로 잡는다. 대부분 그대로 놓기 때문에
     * 매번 돌리게 하는 것보다 낫다.
     */
    public static @Nullable InstallationSession create(org.bukkit.plugin.Plugin plugin, Player player,
                                                       ItemStack item, Location where,
                                                       double yOffset, float scale) {
        float yaw = snap(player.getLocation().getYaw() + 180f);
        Location at = where.clone().add(0, yOffset, 0);
        at.setYaw(yaw);
        at.setPitch(0);

        ItemDisplay display;
        try {
            display = player.getWorld().spawn(at, ItemDisplay.class, e -> {
                ItemStack preview = item.clone();
                preview.setAmount(1);
                e.setItemStack(preview);
                e.setBillboard(Display.Billboard.FIXED);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setGlowing(true);
                e.setGlowColorOverride(OK);
                e.setPersistent(false);   // 서버가 꺼져도 유령으로 남지 않게
                e.setInvulnerable(true);
                e.setGravity(false);
                e.setVisibleByDefault(false);
                // 가구 yml 의 scale 과 같은 값이어야 미리보기와 실제 크기가 맞는다
                org.bukkit.util.Transformation t = e.getTransformation();
                t.getScale().set(scale, scale, scale);
                e.setTransformation(t);
            });
        } catch (RuntimeException e) {
            return null;
        }
        // 설치 중인 본인에게만 보인다. 남의 화면에 유령 가구가 떠다니지 않게.
        player.showEntity(plugin, display);
        return new InstallationSession(display, item, yaw);
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

    public boolean isBlocked() {
        return blocked;
    }

    /** 45도 단위로 맞춘다. 가구가 그 각도로만 놓이기 때문이다. */
    private static float snap(float yaw) {
        float v = Math.round(yaw / InstallationManager.ROTATION_STEP) * InstallationManager.ROTATION_STEP;
        v %= 360f;
        return v < 0 ? v + 360f : v;
    }

    public void rotate(float deltaDegrees) {
        currentYaw = snap(currentYaw + deltaDegrees);
        apply(displayEntity.getLocation());
    }

    public void moveTo(Location newLoc, double yOffset) {
        if (blocked) {
            blocked = false;
            displayEntity.setGlowColorOverride(OK);
        }
        apply(newLoc.clone().add(0, yOffset, 0));
    }

    /** 놓을 수 없는 곳을 보고 있다. 자리는 그대로 두고 색만 바꾼다. */
    public void markBlocked() {
        if (blocked) return;
        blocked = true;
        displayEntity.setGlowColorOverride(BLOCKED);
    }

    private void apply(Location loc) {
        if (displayEntity.isDead()) return;
        Location at = loc.clone();
        at.setYaw(currentYaw);
        at.setPitch(0);
        displayEntity.teleport(at);
    }

    public void cleanup() {
        if (displayEntity != null && !displayEntity.isDead()) {
            displayEntity.remove();
        }
    }
}
