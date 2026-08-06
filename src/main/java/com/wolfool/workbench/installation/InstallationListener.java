package com.wolfool.workbench.installation;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 설치 모드 조작.
 *
 * <pre>
 *   F(다른 손과 아이템 맞바꾸기) : 설치 모드 들어가기 / 나오기
 *   좌클릭                      : 회전
 *   우클릭                      : 설치
 *   웅크리고 우클릭 (모드 밖)    : 설치된 제작대 회수
 * </pre>
 */
public class InstallationListener implements Listener {

    private final InstallationManager manager;

    public InstallationListener(InstallationManager manager) {
        this.manager = manager;
    }

    /**
     * F 키. 핫바에서 제작대를 든 채로 한 번 누르면 바로 설치 모드, 이미 모드면 나온다.
     *
     * <p>여기서 봐야 하는 건 <b>지금 주손에 든 것</b>이다.
     * {@code event.getMainHandItem()} 은 '맞바꾼 뒤에 주손에 올 것' 이라서 지금은
     * 보조손 아이템이다. 그걸 검사하면 제작대를 들고 눌러도 안 걸려서, 손만 바뀌고
     * 한 번 더 눌러야 반응하는 꼴이 된다.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        if (manager.isInstalling(player)) {
            event.setCancelled(true);
            manager.cancelInstallation(player, true);
            return;
        }
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (manager.isWorkbenchItem(inHand)) {
            event.setCancelled(true);   // 맞바꾸지 않는다. 제작대는 주손에 그대로 둔다
            manager.startInstallation(player, inHand);
        }
    }

    /**
     * 좌클릭 = 회전, 우클릭 = 설치.
     *
     * <p>설치 모드에서는 어떤 상호작용도 원래 동작을 하면 안 된다. 블록을 캐거나
     * 놓아버리면 미리보기와 실제 지형이 어긋난다.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInstalling(player)) {
            handlePickup(event, player);
            return;
        }
        // 오프핸드까지 두 번 들어온다. 메인핸드만 본다.
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        if (event.getAction().isLeftClick()) {
            manager.rotate(player, player.isSneaking()
                    ? -InstallationManager.ROTATION_STEP
                    : InstallationManager.ROTATION_STEP);
        } else if (event.getAction().isRightClick()) {
            manager.confirmInstallation(player);
        }
    }

    /** 웅크리고 우클릭으로 설치된 제작대를 회수한다. */
    private void handlePickup(PlayerInteractEvent event, Player player) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (!player.isSneaking()) return;
        if (manager.tryPickup(player)) {
            event.setCancelled(true);
        }
    }

    /** 설치 모드에서 슬롯을 바꾸면 손에 든 게 달라진다. 모드를 닫는다. */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (manager.isInstalling(event.getPlayer())) {
            manager.cancelInstallation(event.getPlayer(), true);
        }
    }

    /** 설치 모드에서 제작대를 버리면 놓을 게 없다. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isInstalling(event.getPlayer())) {
            manager.cancelInstallation(event.getPlayer(), true);
        }
    }

    /** 설치 모드 도중에 블록이 놓이면 안 된다. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (manager.isInstalling(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** 나갈 때 미리보기를 치운다. 안 그러면 엔티티가 남는다. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.cancelInstallation(event.getPlayer(), false);
    }
}
