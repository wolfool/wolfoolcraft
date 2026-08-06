package com.wolfool.workbench.integration;

import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * CraftEngine 연동을 한 곳에 모은다.
 *
 * <p>나머지 코드는 CraftEngine 클래스를 직접 건드리지 않는다. 플러그인이 없거나
 * API 가 바뀌어도 여기만 고치면 되고, 없을 때는 조용히 실패하고 넘어간다.
 */
public final class CraftEngineBridge {

    private final Plugin plugin;
    private final boolean available;

    public CraftEngineBridge(Plugin plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("CraftEngine");
        if (!available) {
            plugin.getLogger().severe("CraftEngine 이 없다. 제작대를 설치할 수 없다.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** 이 아이템의 CraftEngine ID. 커스텀 아이템이 아니면 null. */
    public @Nullable String itemId(@Nullable ItemStack stack) {
        if (!available || stack == null) return null;
        try {
            Key key = CraftEngineItems.getCustomItemId(stack);
            return key == null ? null : key.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 그 ID 의 아이템이 CraftEngine 에 있는지. 설정을 검사할 때 쓴다. */
    public boolean hasItem(String id) {
        if (!available) return false;
        try {
            return CraftEngineItems.byId(id) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 설정에 적힌 ID 로 아이템을 만든다. 없으면 null.
     *
     * <p><b>{@code forPlayer} 에 null 을 주면 안 된다.</b> CraftEngine 이 아이템을
     * 만들면서 플레이어를 들여다보기 때문에 그 자리에서 예외가 난다. 받는 사람이
     * 정해지지 않은 GUI 장식용 아이템이라도 창을 연 사람을 넘겨야 한다.
     */
    public @Nullable ItemStack createItem(String id, Player forPlayer) {
        if (!available) return null;
        try {
            BukkitItemDefinition def = CraftEngineItems.byId(id);
            if (def == null) {
                plugin.getLogger().warning("CraftEngine 에 '" + id + "' 아이템이 없다.");
                return null;
            }
            return def.buildBukkitItem(forPlayer);
        } catch (Throwable t) {
            plugin.getLogger().warning("아이템 생성 실패 (" + id + "): " + t.getMessage());
            return null;
        }
    }

    /**
     * 가구를 놓는다.
     *
     * @param yaw 바라볼 방향. 가구는 위치의 yaw 를 따라간다
     * @return 실패하면 null
     */
    public @Nullable Object place(String furnitureId, Location location, float yaw) {
        if (!available) return null;
        Location at = location.clone();
        at.setYaw(yaw);
        at.setPitch(0);
        try {
            BukkitFurniture furniture = CraftEngineFurniture.place(at, Key.of(furnitureId), "ground", true);
            if (furniture == null) {
                plugin.getLogger().warning("가구를 놓지 못했다: " + furnitureId);
            }
            return furniture;
        } catch (Throwable t) {
            plugin.getLogger().warning("가구 설치 실패 (" + furnitureId + "): " + t.getMessage());
            return null;
        }
    }

    /** 플레이어가 보고 있는 가구. 없으면 null. */
    public @Nullable BukkitFurniture lookingAt(Player player, double distance) {
        if (!available) return null;
        try {
            return CraftEngineFurniture.rayTrace(player, distance);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 그 가구의 ID. 판별용. */
    public @Nullable String furnitureId(@Nullable BukkitFurniture furniture) {
        if (furniture == null) return null;
        try {
            return furniture.id().toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public @Nullable Location furnitureLocation(@Nullable BukkitFurniture furniture) {
        if (furniture == null) return null;
        try {
            return furniture.location();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 가구를 없앤다.
     *
     * @param dropItem true 면 CraftEngine 이 알아서 아이템을 떨군다.
     *                 우리는 인벤토리로 돌려주므로 보통 false 로 부른다.
     */
    public boolean remove(@Nullable BukkitFurniture furniture, boolean dropItem) {
        if (!available || furniture == null) return false;
        try {
            CraftEngineFurniture.remove(furniture, dropItem, false);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("가구 제거 실패: " + t.getMessage());
            return false;
        }
    }
}
