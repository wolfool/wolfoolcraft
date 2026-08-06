package com.wolfool.workbench.installation;

import com.wolfool.workbench.crafting.CraftingManager;
import com.wolfool.workbench.gui.WorkbenchGUI;
import com.wolfool.workbench.integration.SkillBridge;
import com.wolfool.workbench.recipe.RecipeManager;
import net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent;
import net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * 설치된 제작대와의 상호작용.
 *
 * <p>제작대를 우클릭하면 제작 GUI 가 열린다. 웅크리고 우클릭하면 GUI 대신 회수한다
 * ({@link InstallationListener} 가 처리하므로 여기서는 비켜준다).
 *
 * <p>가구를 부수는 경로도 여기서 잡는다. 안 잡으면 설치 개수만 늘어난 채로 남아
 * 5개 제한에 금방 걸린다.
 */
public class FurnitureListener implements Listener {

    private final Plugin plugin;
    private final InstallationManager installation;
    private final CraftingManager crafting;
    private final RecipeManager recipes;
    private final SkillBridge skills;
    private final com.wolfool.workbench.database.DatabaseManager database;
    private final com.wolfool.workbench.integration.CraftEngineBridge craftEngine;

    public FurnitureListener(Plugin plugin, InstallationManager installation, CraftingManager crafting,
                             RecipeManager recipes, SkillBridge skills,
                             com.wolfool.workbench.database.DatabaseManager database,
                             com.wolfool.workbench.integration.CraftEngineBridge craftEngine) {
        this.plugin = plugin;
        this.installation = installation;
        this.crafting = crafting;
        this.recipes = recipes;
        this.skills = skills;
        this.database = database;
        this.craftEngine = craftEngine;
    }

    private String furnitureId() {
        return plugin.getConfig().getString("workbench.furniture-id", "wolfoolcraft:workbench");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        // 오프핸드로도 한 번 더 들어온다. 메인핸드만 본다.
        if (event.hand() != InteractionHand.MAIN_HAND) return;
        if (!furnitureId().equals(craftEngine.furnitureId(event.furniture()))) return;

        Player player = event.player();
        // 설치 모드 중에는 손대지 않는다.
        if (installation.isInstalling(player)) return;

        // 웅크리고 우클릭은 '회수' 다.
        //
        // 회수를 여기서 처리해야 한다. 가구 히트박스는 엔티티라서 웅크리고 눌러도
        // PlayerInteractEvent 가 안 오고, 그쪽에만 회수 코드를 두면 영영 안 불린다.
        if (player.isSneaking()) {
            event.setCancelled(true);
            installation.tryPickup(player, event.furniture());
            return;
        }

        event.setCancelled(true);
        WorkbenchGUI gui = new WorkbenchGUI(plugin, player,
                crafting.getUnlockedSlots(player.getUniqueId()), recipes, skills, null, null, craftEngine);
        player.openInventory(gui.getInventory());
    }

    /**
     * 제작대를 부쉈을 때. 설치 개수를 되돌린다.
     *
     * <p>{@link InstallationManager#tryPickup} 으로 회수할 때는 이 이벤트가 아니라
     * 그쪽에서 직접 줄이므로, 여기서 또 줄지 않게 주의해야 한다. CraftEngine 의
     * remove 는 이 이벤트를 발생시키지 않는다.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(FurnitureBreakEvent event) {
        if (!furnitureId().equals(craftEngine.furnitureId(event.furniture()))) return;
        database.decrementInstalledCount(event.player().getUniqueId());
    }
}
