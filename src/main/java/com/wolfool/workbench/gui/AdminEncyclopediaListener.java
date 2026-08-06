package com.wolfool.workbench.gui;

import com.wolfool.workbench.recipe.CustomRecipe;
import com.wolfool.workbench.recipe.RecipeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AdminEncyclopediaListener implements Listener {
    private final RecipeManager recipeManager;
    private final com.wolfool.workbench.integration.CraftEngineBridge craftEngine;

    public AdminEncyclopediaListener(RecipeManager recipeManager, com.wolfool.workbench.integration.CraftEngineBridge craftEngine) {
        this.recipeManager = recipeManager;
        this.craftEngine = craftEngine;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminEncyclopediaGUI)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        AdminEncyclopediaGUI gui = (AdminEncyclopediaGUI) event.getInventory().getHolder();
        int slot = event.getRawSlot();

        // 카테고리 탭 클릭
        for (int i = 0; i < AdminEncyclopediaGUI.TAB_SLOTS.length; i++) {
            if (slot == AdminEncyclopediaGUI.TAB_SLOTS[i]) {
                Set<String> categories = new LinkedHashSet<>();
                for (CustomRecipe r : recipeManager.getRecipes().values()) {
                    categories.add(r.getCategory());
                }
                List<String> catList = new ArrayList<>(categories);
                if (i < catList.size()) {
                    AdminEncyclopediaGUI newGui = new AdminEncyclopediaGUI(player, recipeManager, catList.get(i), craftEngine);
                    player.openInventory(newGui.getInventory());
                    return;
                }
            }
        }

        // 리로드 버튼 클릭
        if (slot == AdminEncyclopediaGUI.RELOAD_SLOT) {
            recipeManager.loadRecipes();
            recipeManager.validateCustomItems(craftEngine);
            player.sendMessage("§a[관리] 레시피 설정을 다시 불러왔습니다!");
            AdminEncyclopediaGUI newGui = new AdminEncyclopediaGUI(player, recipeManager, gui.getCurrentCategory(), craftEngine);
            player.openInventory(newGui.getInventory());
            return;
        }
    }
}
