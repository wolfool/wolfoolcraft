package com.myserver.workbench.installation;

import com.myserver.workbench.database.DatabaseManager;
import com.myserver.workbench.integration.CraftEngineBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 제작대 설치와 회수.
 *
 * <p>설치는 두 단계다. F 키로 <b>설치 모드</b>에 들어가면 반투명 미리보기가 시선을 따라다니고,
 * 그 상태에서 회전시켜 원하는 방향을 잡은 뒤 확정한다. 확정하는 순간에야 CraftEngine 가구가
 * 실제로 놓이고 아이템이 소모된다.
 */
public class InstallationManager {

    /** 미리보기를 놓을 수 있는 최대 거리 (블록) */
    private static final double REACH = 6.0;
    /** 회전 한 칸 */
    public static final float ROTATION_STEP = 45.0f;

    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final CraftEngineBridge craftEngine;
    private final Map<UUID, InstallationSession> activeSessions = new HashMap<>();

    public InstallationManager(Plugin plugin, DatabaseManager databaseManager, CraftEngineBridge craftEngine) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.craftEngine = craftEngine;
    }

    private String workbenchItemId() {
        return plugin.getConfig().getString("workbench.item-id", "wolfoolcraft:workbench");
    }

    private String workbenchFurnitureId() {
        return plugin.getConfig().getString("workbench.furniture-id", "wolfoolcraft:workbench");
    }

    private int maxPerPlayer() {
        return plugin.getConfig().getInt("crafting.max-workbenches-per-player", 5);
    }

    /**
     * 미리보기를 얼마나 키울지. 가구 yml 의 {@code scale} 과 같아야 한다.
     *
     * <p>다르면 미리보기와 실제로 놓인 물건의 크기가 어긋난다.
     */
    public float previewScale() {
        return (float) plugin.getConfig().getDouble("workbench.preview-scale", 1.5);
    }

    /**
     * 미리보기를 얼마나 띄울지. 가구 yml 의 {@code translation} Y 와 같아야 한다.
     *
     * <p>디스플레이 엔티티는 모델을 <b>엔티티 위치 중심</b>으로 그린다. 그래서 한 블록짜리
     * 모델을 바닥에 세우려면 크기의 절반만큼 올려야 한다. 이 값이 안 맞으면 미리보기는
     * 땅에 박혀 보이고 실제로는 공중에 뜬 채 놓인다.
     */
    public double previewYOffset() {
        return plugin.getConfig().getDouble("workbench.preview-y-offset", previewScale() / 2.0);
    }

    /** 손에 든 게 제작대 아이템인지 */
    public boolean isWorkbenchItem(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        String id = craftEngine.itemId(stack);
        return workbenchItemId().equals(id);
    }

    public boolean isInstalling(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public @Nullable InstallationSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    // ---------------- 설치 ----------------

    public void startInstallation(Player player, ItemStack item) {
        if (isInstalling(player)) return;
        if (!craftEngine.isAvailable()) {
            player.sendMessage(Component.text("[제작대] CraftEngine 이 없어 설치할 수 없다.")
                    .color(NamedTextColor.RED));
            return;
        }

        int installed = databaseManager.getInstalledCount(player.getUniqueId());
        int max = maxPerPlayer();
        if (max > 0 && installed >= max) {
            player.sendMessage(Component.text("[제작대] 더 설치할 수 없다. (최대 " + max + "개)")
                    .color(NamedTextColor.RED));
            return;
        }

        Location target = aimedLocation(player);
        if (target == null) {
            player.sendMessage(Component.text("[제작대] 놓을 자리를 찾지 못했다. 바닥을 보고 다시 시도해라.")
                    .color(NamedTextColor.RED));
            return;
        }

        InstallationSession session = InstallationSession.create(
                plugin, player, item, target, previewYOffset(), previewScale());
        if (session == null) {
            player.sendMessage(Component.text("[제작대] 미리보기를 띄우지 못했다.")
                    .color(NamedTextColor.RED));
            return;
        }
        activeSessions.put(player.getUniqueId(), session);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
    }

    /**
     * 미리보기를 시선 끝으로 옮긴다. 매 틱 부르는 용도.
     *
     * @return 놓을 수 있는 자리면 true
     */
    public boolean updatePreview(Player player) {
        InstallationSession session = getSession(player);
        if (session == null) return false;
        Location target = aimedLocation(player);
        if (target == null) {
            session.markBlocked();
            return false;
        }
        session.moveTo(target, previewYOffset());
        return true;
    }

    public void rotate(Player player, float deltaDegrees) {
        InstallationSession session = getSession(player);
        if (session != null) session.rotate(deltaDegrees);
    }

    /** 설치 확정. 여기서 처음으로 가구가 실제로 놓이고 아이템이 소모된다. */
    public void confirmInstallation(Player player) {
        InstallationSession session = getSession(player);
        if (session == null) return;

        Location target = aimedLocation(player);
        if (target == null) {
            player.sendMessage(Component.text("[제작대] 여기엔 놓을 수 없다.").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            return;
        }

        // 설치 모드에 들어간 뒤 아이템을 버렸을 수도 있다. 소모 직전에 다시 확인한다.
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!isWorkbenchItem(inHand)) {
            player.sendMessage(Component.text("[제작대] 제작대를 들고 있어야 한다.").color(NamedTextColor.RED));
            cancelInstallation(player, false);
            return;
        }

        int max = maxPerPlayer();
        if (max > 0 && databaseManager.getInstalledCount(player.getUniqueId()) >= max) {
            player.sendMessage(Component.text("[제작대] 더 설치할 수 없다. (최대 " + max + "개)")
                    .color(NamedTextColor.RED));
            cancelInstallation(player, false);
            return;
        }

        float yaw = session.getCurrentYaw();
        Object placed = craftEngine.place(workbenchFurnitureId(), target, yaw);
        if (placed == null) {
            player.sendMessage(Component.text("[제작대] 설치에 실패했다. 관리자에게 알려라.")
                    .color(NamedTextColor.RED));
            return;
        }

        inHand.setAmount(inHand.getAmount() - 1);
        databaseManager.incrementInstalledCount(player.getUniqueId());

        player.playSound(target, Sound.BLOCK_WOOD_PLACE, 0.9f, 1.0f);
        player.sendMessage(Component.text("[제작대] 설치했다.").color(NamedTextColor.GREEN));
        cancelInstallation(player, false);
    }

    public void cancelInstallation(Player player, boolean showMessage) {
        InstallationSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;
        session.cleanup();
        player.sendActionBar(Component.empty());
        if (showMessage) {
            player.sendMessage(Component.text("[제작대] 설치 모드를 종료했다.").color(NamedTextColor.GRAY));
        }
    }

    public void cancelAll() {
        for (Map.Entry<UUID, InstallationSession> e : activeSessions.entrySet()) {
            e.getValue().cleanup();
        }
        activeSessions.clear();
    }

    // ---------------- 회수 ----------------

    /**
     * 웅크리고 우클릭한 제작대를 회수한다.
     *
     * @return 회수했으면 true
     */
    public boolean tryPickup(Player player) {
        return tryPickup(player, craftEngine.lookingAt(player, REACH));
    }

    /**
     * 그 제작대를 회수한다.
     *
     * <p>가구를 직접 누른 경우에는 시선으로 다시 찾을 필요가 없다. 게다가 히트박스는
     * 엔티티라서 <b>블록 상호작용 이벤트가 아예 안 온다.</b> 그래서 회수는
     * CraftEngine 의 가구 이벤트에서 이 메서드로 들어와야 한다.
     */
    public boolean tryPickup(Player player, @Nullable BukkitFurniture furniture) {
        if (furniture == null) return false;
        if (!workbenchFurnitureId().equals(craftEngine.furnitureId(furniture))) return false;

        Location loc = craftEngine.furnitureLocation(furniture);
        ItemStack give = craftEngine.createItem(workbenchItemId(), player);
        if (!craftEngine.remove(furniture, give == null)) {
            player.sendMessage(Component.text("[제작대] 회수하지 못했다.").color(NamedTextColor.RED));
            return true;
        }

        databaseManager.decrementInstalledCount(player.getUniqueId());
        if (give != null) {
            // 인벤토리가 꽉 찼으면 발밑에 떨군다. 조용히 사라지는 게 제일 나쁘다.
            for (ItemStack rest : player.getInventory().addItem(give).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        player.playSound(loc != null ? loc : player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.9f, 1.0f);
        player.sendMessage(Component.text("[제작대] 회수했다.").color(NamedTextColor.YELLOW));
        return true;
    }

    /** 보고 있는 제작대. GUI 를 열 때 쓴다. */
    public @Nullable BukkitFurniture workbenchInSight(Player player) {
        BukkitFurniture furniture = craftEngine.lookingAt(player, REACH);
        if (furniture == null) return null;
        return workbenchFurnitureId().equals(craftEngine.furnitureId(furniture)) ? furniture : null;
    }

    // ---------------- 좌표 ----------------

    /**
     * 지금 바라보는 곳에서 가구를 놓을 자리.
     *
     * <p>블록을 보고 있으면 맞은 면의 바깥쪽 블록 중앙, 허공을 보고 있으면 null.
     * 가구는 바닥에 놓이는 물건이라 자리를 못 찾으면 설치를 막는 편이 낫다.
     */
    private @Nullable Location aimedLocation(Player player) {
        RayTraceResult hit = player.rayTraceBlocks(REACH);
        if (hit == null || hit.getHitBlock() == null) return null;

        BlockFace face = hit.getHitBlockFace();
        Location loc = hit.getHitBlock().getLocation();
        if (face != null) loc = loc.add(face.getModX(), face.getModY(), face.getModZ());

        if (!loc.getBlock().isEmpty() && !loc.getBlock().isPassable()) return null;
        return loc.add(0.5, 0, 0.5);
    }
}
