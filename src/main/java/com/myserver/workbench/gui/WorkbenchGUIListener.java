package com.myserver.workbench.gui;

import com.myserver.workbench.crafting.CraftingManager;
import com.myserver.workbench.crafting.CraftingSession;
import com.myserver.workbench.recipe.CustomRecipe;
import com.myserver.workbench.recipe.RecipeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkbenchGUIListener implements Listener {
    private final org.bukkit.plugin.Plugin plugin;
    private final CraftingManager manager;
    private final RecipeManager recipeManager;

    public WorkbenchGUIListener(org.bukkit.plugin.Plugin plugin, CraftingManager manager, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.recipeManager = recipeManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();

        // ===== 1. Encyclopedia GUI =====
        if (topInventory.getHolder() instanceof EncyclopediaGUI) {
            event.setCancelled(true);
            EncyclopediaGUI gui = (EncyclopediaGUI) topInventory.getHolder();
            int slot = event.getRawSlot();

            if (slot == 45) { // Back button
                WorkbenchGUI wbGui = new WorkbenchGUI(plugin, player, manager.getUnlockedSlots(player.getUniqueId()), recipeManager, 10, gui.getCurrentCategory(), null);
                player.openInventory(wbGui.getInventory());
                return;
            }

            for (int i = 0; i < EncyclopediaGUI.TAB_SLOTS.length; i++) {
                if (slot == EncyclopediaGUI.TAB_SLOTS[i]) {
                    Set<String> categories = new LinkedHashSet<>();
                    for (CustomRecipe r : recipeManager.getRecipes().values()) categories.add(r.getCategory());
                    List<String> catList = new ArrayList<>(categories);
                    if (i < catList.size()) {
                        EncyclopediaGUI newGui = new EncyclopediaGUI(plugin, player, recipeManager, catList.get(i), 10);
                        player.openInventory(newGui.getInventory());
                        return;
                    }
                }
            }

            int pageIndex = EncyclopediaGUI.PAGE_SLOTS.indexOf(slot);
            if (pageIndex != -1) {
                List<CustomRecipe> currentRecipes = new ArrayList<>();
                for (CustomRecipe r : recipeManager.getRecipes().values()) {
                    if (r.getCategory().equals(gui.getCurrentCategory())) currentRecipes.add(r);
                }
                if (pageIndex < currentRecipes.size()) {
                    CustomRecipe clickedRecipe = currentRecipes.get(pageIndex);
                    // 작업대 GUI를 열면서 레시피 타겟 지정 (가방 체크 후 자동 홀로그램 렌더링)
                    WorkbenchGUI wbGui = new WorkbenchGUI(plugin, player, manager.getUnlockedSlots(player.getUniqueId()), recipeManager, 10, gui.getCurrentCategory(), clickedRecipe);
                    player.openInventory(wbGui.getInventory());
                }
            }
            return;
        }

        // ===== 2. Workbench GUI =====
        if (topInventory.getHolder() instanceof WorkbenchGUI) {
            WorkbenchGUI gui = (WorkbenchGUI) topInventory.getHolder();
            int rawSlot = event.getRawSlot();

            // 유저 인벤토리 클릭 시 (Shift 클릭 포함)
            if (rawSlot >= topInventory.getSize()) {
                if (event.isShiftClick()) {
                    event.setCancelled(true); // Shift 클릭 이동은 꼬일 수 있으므로 막습니다.
                } else if (event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
                    event.setCancelled(true); // 더블클릭 모으기 방지
                }
                return; // 유저 인벤토리 내 일반 클릭 허용
            }

            // 상단 인벤토리 (GUI) 내의 모든 클릭은 절대 차단 (홀로그램 및 유리 보호)
            event.setCancelled(true);

            // 도감 열기 버튼
            if (rawSlot == WorkbenchGUI.BOOK_SLOT) {
                EncyclopediaGUI encGui = new EncyclopediaGUI(plugin, player, recipeManager, gui.getCategory(), 10);
                player.openInventory(encGui.getInventory());
                return;
            }

            // 결과물(제작) 버튼 클릭
            if (rawSlot == WorkbenchGUI.RESULT_SLOT && gui.getSelectedRecipe() != null) {
                if (!gui.canCraft()) {
                    player.sendMessage("§c[작업대] 재료가 부족합니다!");
                    return;
                }

                List<CraftingSession> queue = manager.getPlayerQueue(player.getUniqueId());
                int unlocked = manager.getUnlockedSlots(player.getUniqueId());

                if (queue.size() >= unlocked) {
                    player.sendMessage("§c[작업대] 대기열이 꽉 찼습니다!");
                    return;
                }

                CustomRecipe recipe = gui.getSelectedRecipe();

                // 가방에서 재료 소모
                Map<Material, Integer> totalRequired = new HashMap<>();
                if (recipe.isShaped()) {
                    for (ItemStack req : recipe.getKeys().values()) {
                        totalRequired.put(req.getType(), totalRequired.getOrDefault(req.getType(), 0) + req.getAmount());
                    }
                } else {
                    for (ItemStack req : recipe.getIngredients()) {
                        totalRequired.put(req.getType(), totalRequired.getOrDefault(req.getType(), 0) + req.getAmount());
                    }
                }

                for (Map.Entry<Material, Integer> entry : totalRequired.entrySet()) {
                    removePlayerMaterial(player, entry.getKey(), entry.getValue());
                }

                // 대기열에 세션 추가
                CraftingSession session = new CraftingSession(recipe.getResult(), recipe.getCraftingTimeTicks());
                queue.add(session);

                player.sendMessage("§a[작업대] §e" + WorkbenchGUI.formatMaterialName(recipe.getResult().getType()) + " §a제작을 시작했습니다! (⏱ " + (recipe.getCraftingTimeTicks() / 20) + "초)");

                // GUI 새로고침 (가방 상태가 변했으므로 홀로그램 업데이트)
                WorkbenchGUI newGui = new WorkbenchGUI(plugin, player, manager.getUnlockedSlots(player.getUniqueId()), recipeManager, 10, gui.getCategory(), gui.getSelectedRecipe());
                player.openInventory(newGui.getInventory());
                return;
            }

            // 대기열 슬롯 클릭
            for (int i = 0; i < WorkbenchGUI.QUEUE_SLOTS.length; i++) {
                if (rawSlot == WorkbenchGUI.QUEUE_SLOTS[i]) {
                    int unlocked = manager.getUnlockedSlots(player.getUniqueId());
                    if (i >= unlocked) {
                        manager.unlockSlot(player.getUniqueId());
                        player.sendMessage("§a[작업대] 대기열 슬롯이 확장되었습니다!");
                        
                        WorkbenchGUI newGui = new WorkbenchGUI(plugin, player, manager.getUnlockedSlots(player.getUniqueId()), recipeManager, 10, gui.getCategory(), gui.getSelectedRecipe());
                        player.openInventory(newGui.getInventory());
                    } else {
                        List<CraftingSession> queue = manager.getPlayerQueue(player.getUniqueId());
                        if (i < queue.size()) {
                            CraftingSession session = queue.get(i);
                            if (session.isCompleted() && !session.isCollected()) {
                                session.setCollected(true);
                                queue.remove(i);
                                // 결과물 지급
                                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(session.getResultItem());
                                for (ItemStack drop : leftover.values()) {
                                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                }
                                player.sendMessage("§a[작업대] 아이템을 수령했습니다!");
                                
                                WorkbenchGUI newGui = new WorkbenchGUI(plugin, player, manager.getUnlockedSlots(player.getUniqueId()), recipeManager, 10, gui.getCategory(), gui.getSelectedRecipe());
                                player.openInventory(newGui.getInventory());
                            } else if (!session.isCompleted()) {
                                player.sendMessage("§c[작업대] 아직 제작 중입니다!");
                            }
                        }
                    }
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof WorkbenchGUI) {
            // 상단 인벤토리에 무언가 닿는다면 무조건 캔슬
            for (int slot : event.getRawSlots()) {
                if (slot < event.getInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ===== Utility =====

    private void removePlayerMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
            }
        }
    }
}
