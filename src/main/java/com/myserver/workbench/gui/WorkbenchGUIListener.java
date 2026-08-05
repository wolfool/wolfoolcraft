package com.myserver.workbench.gui;

import com.myserver.workbench.crafting.CraftingManager;
import com.myserver.workbench.crafting.CraftingSession;
import com.myserver.workbench.recipe.CustomRecipe;
import com.myserver.workbench.recipe.RecipeItem;
import com.myserver.workbench.recipe.RecipeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkbenchGUIListener implements Listener {
    private final org.bukkit.plugin.Plugin plugin;
    private final CraftingManager manager;
    private final RecipeManager recipeManager;
    private final com.myserver.workbench.integration.CraftEngineBridge craftEngine;
    private final com.myserver.workbench.integration.SkillBridge skills;

    public WorkbenchGUIListener(org.bukkit.plugin.Plugin plugin, CraftingManager manager,
                                RecipeManager recipeManager,
                                com.myserver.workbench.integration.CraftEngineBridge craftEngine,
                                com.myserver.workbench.integration.SkillBridge skills) {
        this.plugin = plugin;
        this.manager = manager;
        this.recipeManager = recipeManager;
        this.craftEngine = craftEngine;
        this.skills = skills;
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

            // 탭 전환. 눌린 탭은 배경 그림이 바뀌면서 표시된다. 장은 처음으로 돌아간다.
            List<EncyclopediaTab> tabs = gui.getTabs();
            for (int i = 0; i < EncyclopediaGUI.TAB_SLOTS.length && i < tabs.size(); i++) {
                if (slot == EncyclopediaGUI.TAB_SLOTS[i]) {
                    openEncyclopedia(player, tabs.get(i).name(), 0);
                    return;
                }
            }

            // 장 넘기기. 넘길 데가 없으면 화살표를 안 놨으므로 무시한다.
            // (53번은 투명 아이템이 깔려 있어서 '아이템이 있나' 로는 구분이 안 된다)
            if (slot == EncyclopediaGUI.PREV_SLOT) {
                if (gui.getPage() > 0) {
                    openEncyclopedia(player, gui.getCurrentCategory(), gui.getPage() - 1);
                }
                return;
            }
            if (slot == EncyclopediaGUI.NEXT_SLOT) {
                if (gui.getPage() < gui.getPageCount() - 1) {
                    openEncyclopedia(player, gui.getCurrentCategory(), gui.getPage() + 1);
                }
                return;
            }

            int pageIndex = EncyclopediaGUI.PAGE_SLOTS.indexOf(slot);
            if (pageIndex != -1) {
                List<CustomRecipe> currentRecipes =
                        EncyclopediaGUI.recipesFor(tabs, gui.getTabIndex(), recipeManager);
                int index = gui.recipeIndexAt(pageIndex);
                if (index < currentRecipes.size()) {
                    CustomRecipe clickedRecipe = currentRecipes.get(index);
                    // 작업대 GUI를 열면서 레시피 타겟 지정 (가방 체크 후 자동 홀로그램 렌더링)
                    WorkbenchGUI wbGui = new WorkbenchGUI(plugin, player, manager.getUnlockedSlots(player.getUniqueId()), recipeManager, skills, gui.getCurrentCategory(), clickedRecipe, craftEngine);
                    player.openInventory(wbGui.getInventory());
                }
            }
            return;
        }

        // ===== 2. Workbench GUI =====
        if (topInventory.getHolder() instanceof WorkbenchGUI) {
            WorkbenchGUI gui = (WorkbenchGUI) topInventory.getHolder();
            int rawSlot = event.getRawSlot();
            boolean free = gui.isFreeMode();

            // 유저 인벤토리 클릭 시 (Shift 클릭 포함)
            if (rawSlot >= topInventory.getSize()) {
                if (event.isShiftClick()) {
                    // 바닐라에 맡기면 결과 칸이나 대기열로 들어가 버린다. 우리가 격자로만 옮긴다.
                    event.setCancelled(true);
                    ItemStack clicked = event.getCurrentItem();
                    if (free && clicked != null && !clicked.getType().isAir()) {
                        ItemStack left = clicked.clone();
                        if (moveIntoGrid(gui, left)) {
                            event.setCurrentItem(left.getAmount() <= 0 ? null : left);
                            scheduleFreeRefresh(gui, player);
                        }
                    }
                } else if (event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
                    event.setCancelled(true); // 더블클릭 모으기 방지
                }
                return; // 유저 인벤토리 내 일반 클릭 허용
            }

            // 도감에서 고르지 않고 그냥 열었으면 격자는 입력칸이다. 놓고 집을 수 있게 둔다.
            // 격자 내용은 이 이벤트가 끝나야 확정되므로 결과는 다음 틱에 다시 센다.
            if (free && WorkbenchGUI.isGridSlot(rawSlot)) {
                scheduleFreeRefresh(gui, player);
                return;
            }

            // 나머지 칸은 전부 차단 (청사진·투명칸 보호)
            event.setCancelled(true);

            // 도감 열기 버튼
            if (rawSlot == WorkbenchGUI.BOOK_SLOT) {
                openEncyclopedia(player, gui.getCategory(), 0);
                return;
            }

            // 격자에 직접 올려서 만드는 경우
            if (rawSlot == WorkbenchGUI.RESULT_SLOT && free) {
                craftFromGrid(gui, player);
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

                // 숙련도가 모자라면 재료만 날린다. 제작 직전에 한 번 더 막는다.
                if (!skills.meets(player, recipe.getSkillType(), recipe.getRequiredProficiency())) {
                    player.sendMessage("§c[작업대] 숙련도가 부족합니다. §7(요구 "
                            + recipe.getRequiredProficiency() + "레벨)");
                    return;
                }

                // 가방에서 재료 소모.
                // GUI 가 '부족/충족' 을 판단할 때 쓴 계산을 그대로 써야 보이는 것과
                // 실제로 빠지는 양이 어긋나지 않는다.
                Map<String, Integer> required = WorkbenchGUI.totalRequired(recipe);
                Map<String, RecipeItem> byKey = new HashMap<>();
                for (RecipeItem req : recipe.isShaped()
                        ? recipe.getKeys().values() : recipe.getIngredients()) {
                    byKey.put(req.key(), req);
                }
                for (Map.Entry<String, Integer> entry : required.entrySet()) {
                    RecipeItem req = byKey.get(entry.getKey());
                    if (req != null) req.takeFrom(player, craftEngine, entry.getValue());
                }

                // 대기열에 세션 추가
                ItemStack resultStack = recipe.getResult().display(craftEngine, player);
                CraftingSession session = new CraftingSession(resultStack, recipe.getCraftingTimeTicks());
                queue.add(session);

                // 숙련도 XP 는 완성이 아니라 '시작'할 때 준다.
                // 대기열은 재시작을 넘어 살아남는데 세션이 레시피 id 를 안 들고 있어서,
                // 수령 시점에는 어떤 스킬이었는지 알 방법이 없다. 재료를 이미 낸 시점이라
                // 보상 기준으로도 어색하지 않다.
                skills.grantXp(player, recipe.getSkillType(), recipe.getXpReward());
                manager.recordCraft(player.getUniqueId(), recipe.getId());

                player.sendMessage(Component.text("[작업대] ").color(NamedTextColor.GREEN)
                        .append(WorkbenchGUI.nameOf(resultStack, resultStack.getItemMeta())
                                .color(NamedTextColor.YELLOW))
                        .append(Component.text(" 제작을 시작했습니다! (⏱ "
                                + (recipe.getCraftingTimeTicks() / 20) + "초)")
                                .color(NamedTextColor.GREEN)));

                // GUI 새로고침 (가방 상태가 변했으므로 홀로그램 업데이트)
                WorkbenchGUI newGui = new WorkbenchGUI(plugin, player, manager.getUnlockedSlots(player.getUniqueId()), recipeManager, skills, gui.getCategory(), gui.getSelectedRecipe(), craftEngine);
                player.openInventory(newGui.getInventory());
                return;
            }

            // 대기열 슬롯 클릭
            for (int i = 0; i < WorkbenchGUI.QUEUE_SLOTS.length; i++) {
                if (rawSlot == WorkbenchGUI.QUEUE_SLOTS[i]) {
                    int unlocked = manager.getUnlockedSlots(player.getUniqueId());
                    if (i >= unlocked) {
                        if (tryUnlockWithKey(player, unlocked)) {
                            WorkbenchGUI newGui = new WorkbenchGUI(plugin, player, manager.getUnlockedSlots(player.getUniqueId()), recipeManager, skills, gui.getCategory(), gui.getSelectedRecipe(), craftEngine);
                            player.openInventory(newGui.getInventory());
                        }
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
                                
                                WorkbenchGUI newGui = new WorkbenchGUI(plugin, player, manager.getUnlockedSlots(player.getUniqueId()), recipeManager, skills, gui.getCategory(), gui.getSelectedRecipe(), craftEngine);
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
        if (!(event.getInventory().getHolder() instanceof WorkbenchGUI gui)) return;
        boolean free = gui.isFreeMode();

        for (int slot : event.getRawSlots()) {
            if (slot >= event.getInventory().getSize()) continue;  // 아래쪽 인벤토리는 상관없다
            if (free && WorkbenchGUI.isGridSlot(slot)) continue;   // 격자에 나눠 담는 건 허용
            event.setCancelled(true);
            return;
        }
        if (free && event.getWhoClicked() instanceof Player player) {
            scheduleFreeRefresh(gui, player);
        }
    }

    /**
     * 창을 닫을 때 격자에 두고 간 재료를 돌려준다.
     *
     * <p>이 인벤토리는 우리가 만든 임시 창이라, 닫히면 안에 있던 건 그대로 사라진다.
     * 조용히 없어지는 게 제일 나쁘다.
     */
    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof WorkbenchGUI gui)) return;
        if (!gui.isFreeMode()) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        for (int slot : WorkbenchGUI.RECIPE_GRID) {
            ItemStack left = event.getInventory().getItem(slot);
            if (left == null || left.getType().isAir()) continue;
            event.getInventory().setItem(slot, null);
            for (ItemStack rest : player.getInventory().addItem(left).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    // ===== 격자에 직접 올려서 만들기 =====

    /** 격자 내용이 확정되는 다음 틱에 결과 칸을 다시 계산한다. */
    private void scheduleFreeRefresh(WorkbenchGUI gui, Player player) {
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() == gui) {
                gui.refreshFreeResult(player, recipeManager, craftEngine);
            }
        });
    }

    /**
     * Shift 클릭한 아이템을 격자 빈칸으로 옮긴다.
     *
     * <p>바닐라 Shift 이동에 맡기면 결과 칸이나 대기열로도 들어가서 직접 옮긴다.
     *
     * @return 한 개라도 옮겼으면 true
     */
    private boolean moveIntoGrid(WorkbenchGUI gui, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) return false;
        Inventory inv = gui.getInventory();
        boolean moved = false;

        // 같은 아이템이 이미 올라와 있으면 거기에 먼저 얹는다.
        for (int slot : WorkbenchGUI.RECIPE_GRID) {
            if (clicked.getAmount() <= 0) break;
            ItemStack inCell = inv.getItem(slot);
            if (inCell == null || inCell.getType().isAir() || !inCell.isSimilar(clicked)) continue;
            int space = inCell.getMaxStackSize() - inCell.getAmount();
            if (space <= 0) continue;
            int take = Math.min(space, clicked.getAmount());
            inCell.setAmount(inCell.getAmount() + take);
            inv.setItem(slot, inCell);
            clicked.setAmount(clicked.getAmount() - take);
            moved = true;
        }
        // 남으면 빈칸에 넣는다.
        for (int slot : WorkbenchGUI.RECIPE_GRID) {
            if (clicked.getAmount() <= 0) break;
            ItemStack inCell = inv.getItem(slot);
            if (inCell != null && !inCell.getType().isAir()) continue;
            ItemStack put = clicked.clone();
            put.setAmount(Math.min(clicked.getAmount(), put.getMaxStackSize()));
            inv.setItem(slot, put);
            clicked.setAmount(clicked.getAmount() - put.getAmount());
            moved = true;
        }
        return moved;
    }

    /** 격자에 올려놓은 것으로 제작을 시작한다. 가방은 건드리지 않는다. */
    private void craftFromGrid(WorkbenchGUI gui, Player player) {
        // 결과 칸을 띄운 뒤 격자가 바뀌었을 수 있으니 지금 내용으로 다시 맞춰 본다.
        ItemStack[] grid = gui.readGrid();
        com.myserver.workbench.recipe.GridMatcher.Match match =
                com.myserver.workbench.recipe.GridMatcher.match(
                        grid, recipeManager.getRecipes().values(), craftEngine);
        if (match == null) {
            gui.refreshFreeResult(player, recipeManager, craftEngine);
            return;
        }
        CustomRecipe recipe = match.recipe();

        List<CraftingSession> queue = manager.getPlayerQueue(player.getUniqueId());
        if (queue.size() >= manager.getUnlockedSlots(player.getUniqueId())) {
            player.sendMessage("§c[작업대] 대기열이 꽉 찼습니다!");
            return;
        }
        if (!skills.meets(player, recipe.getSkillType(), recipe.getRequiredProficiency())) {
            player.sendMessage("§c[작업대] 숙련도가 부족합니다. §7(요구 "
                    + recipe.getRequiredProficiency() + "레벨)");
            return;
        }

        com.myserver.workbench.recipe.GridMatcher.consume(grid, match);
        gui.writeGrid(grid);

        ItemStack resultStack = recipe.getResult().display(craftEngine, player);
        queue.add(new CraftingSession(resultStack, recipe.getCraftingTimeTicks()));
        skills.grantXp(player, recipe.getSkillType(), recipe.getXpReward());
        manager.recordCraft(player.getUniqueId(), recipe.getId());

        player.sendMessage(Component.text("[작업대] ").color(NamedTextColor.GREEN)
                .append(WorkbenchGUI.nameOf(resultStack, resultStack.getItemMeta())
                        .color(NamedTextColor.YELLOW))
                .append(Component.text(" 제작을 시작했습니다! (⏱ "
                        + (recipe.getCraftingTimeTicks() / 20) + "초)")
                        .color(NamedTextColor.GREEN)));

        gui.refreshFreeResult(player, recipeManager, craftEngine);
    }

    // ===== Utility =====

    /** 도감을 연다. 장 번호가 범위를 벗어나면 GUI 쪽에서 가까운 쪽으로 붙인다. */
    private void openEncyclopedia(Player player, String tabName, int page) {
        EncyclopediaGUI gui = new EncyclopediaGUI(
                plugin, player, recipeManager, tabName, page, skills, craftEngine);
        player.openInventory(gui.getInventory());
    }

    /**
     * 잠긴 대기열 칸을 연다. 열쇠 하나를 소모한다.
     *
     * <p>문서상 기본 3칸이고 열쇠로 넓힌다. 공짜로 열리면 열쇠가 의미가 없어서
     * 여기서 반드시 소모시킨다.
     *
     * @return 열었으면 true
     */
    private boolean tryUnlockWithKey(Player player, int unlocked) {
        int max = plugin.getConfig().getInt("crafting.max-queue-slots", 9);
        if (unlocked >= max) {
            player.sendMessage("§c[작업대] 대기열을 더 넓힐 수 없습니다. (최대 " + max + "칸)");
            return false;
        }

        String keyId = plugin.getConfig().getString("workbench.queue-key-id", "wolfoolcraft:queue_key");
        if (!consumeOne(player, keyId)) {
            player.sendMessage("§c[작업대] 열쇠가 필요합니다.");
            player.sendMessage("§7열쇠는 상점과 도감 보상에서 얻을 수 있습니다.");
            return false;
        }

        manager.unlockSlot(player.getUniqueId());
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_LOCKED, 0.8f, 1.6f);
        player.sendMessage("§a[작업대] 열쇠를 사용해 대기열을 넓혔습니다. §7("
                + (unlocked + 1) + "/" + max + "칸)");
        return true;
    }

    /** 인벤토리에서 그 CraftEngine 아이템 하나를 뺀다. 없으면 false. */
    private boolean consumeOne(Player player, String craftEngineId) {
        if (craftEngine == null) return false;
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) continue;
            if (!craftEngineId.equals(craftEngine.itemId(item))) continue;
            item.setAmount(item.getAmount() - 1);
            return true;
        }
        return false;
    }

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
