package com.myserver.workbench.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import com.myserver.workbench.recipe.RecipeManager;
import com.myserver.workbench.recipe.CustomRecipe;
import com.myserver.workbench.recipe.RecipeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 작업대 GUI (54칸) - 홀로그램(청사진) 스캔 방식
 */
public class WorkbenchGUI implements InventoryHolder {
    private final Inventory inventory;

    public static final int BOOK_SLOT = 7;
    public static final int RESULT_SLOT = 25;

    public static final int[] RECIPE_GRID = {
        1, 2, 3, 4, 5,
        10, 11, 12, 13, 14,
        19, 20, 21, 22, 23,
        28, 29, 30, 31, 32,
        37, 38, 39, 40, 41
    };

    public static final int[] QUEUE_SLOTS = {45, 46, 47, 48, 49, 50, 51, 52, 53};

    private String category;
    private CustomRecipe selectedRecipe;
    private boolean canCraft = false;
    /** 격자에 직접 올려서 맞은 레시피. 자유 제작일 때만 채워진다. */
    private com.myserver.workbench.recipe.GridMatcher.Match freeMatch;

    /**
     * 도감에서 고른 레시피 없이 연 상태인지.
     *
     * <p>이때는 격자가 <b>입력칸</b>이 된다. 재료를 직접 올려놓으면 맞는 레시피를 찾아
     * 결과 칸에 띄우고, 누르면 올려놓은 것만 소모해서 만든다. 도감에서 고르고 들어오면
     * 격자가 청사진으로 채워지므로 손댈 수 없다.
     */
    public boolean isFreeMode() {
        return selectedRecipe == null;
    }

    public com.myserver.workbench.recipe.GridMatcher.Match getFreeMatch() {
        return freeMatch;
    }

    /** 그 슬롯이 5×5 격자 칸인지. */
    public static boolean isGridSlot(int slot) {
        for (int gridSlot : RECIPE_GRID) {
            if (gridSlot == slot) return true;
        }
        return false;
    }

    /** 격자에 올려진 것을 5×5 순서대로 읽는다. */
    public ItemStack[] readGrid() {
        ItemStack[] grid = new ItemStack[RECIPE_GRID.length];
        for (int i = 0; i < RECIPE_GRID.length; i++) {
            grid[i] = inventory.getItem(RECIPE_GRID[i]);
        }
        return grid;
    }

    /** 읽어서 고친 격자를 도로 넣는다. 개수가 0이 된 칸은 비운다. */
    public void writeGrid(ItemStack[] grid) {
        for (int i = 0; i < RECIPE_GRID.length; i++) {
            ItemStack stack = grid[i];
            inventory.setItem(RECIPE_GRID[i],
                    (stack == null || stack.getAmount() <= 0) ? null : stack);
        }
    }

    /**
     * 격자를 다시 훑어 맞는 레시피를 결과 칸에 띄운다.
     *
     * <p>클릭 이벤트가 끝나야 격자 내용이 확정되므로 <b>다음 틱에</b> 불러야 한다.
     */
    public void refreshFreeResult(Player player, RecipeManager recipeManager,
                                  com.myserver.workbench.integration.CraftEngineBridge craftEngine) {
        if (!isFreeMode()) return;

        freeMatch = com.myserver.workbench.recipe.GridMatcher.match(
                readGrid(), recipeManager.getRecipes().values(), craftEngine);

        if (freeMatch == null) {
            inventory.setItem(RESULT_SLOT, null);
            return;
        }
        CustomRecipe recipe = freeMatch.recipe();
        ItemStack result = recipe.getResult().display(craftEngine, player);
        ItemMeta meta = result.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("⏱ 제작 시간: " + (recipe.getCraftingTimeTicks() / 20) + "초")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("XP 보상: " + recipe.getXpReward())
                .color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("클릭하여 제작!")
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.lore(lore);
        result.setItemMeta(meta);
        inventory.setItem(RESULT_SLOT, result);
    }

    public WorkbenchGUI(org.bukkit.plugin.Plugin plugin, Player player, int unlockedQueueSlots, RecipeManager recipeManager, com.myserver.workbench.integration.SkillBridge skills, String category, CustomRecipe selectedRecipe, com.myserver.workbench.integration.CraftEngineBridge craftEngine) {
        this.category = category;
        this.selectedRecipe = selectedRecipe;
        this.inventory = Bukkit.createInventory(this, 54, GuiTitle.of(plugin, "workbench", "제작대"));
        
        initializeItems(plugin, player, unlockedQueueSlots, craftEngine);
    }

    private void initializeItems(org.bukkit.plugin.Plugin plugin, Player player, int unlockedQueueSlots, com.myserver.workbench.integration.CraftEngineBridge craftEngine) {
        // 0. 투명 유리 (image-slots)
        List<Integer> imageSlots = plugin.getConfig().getIntegerList("gui.image-slots.workbench");
        if (imageSlots != null) {
            ItemStack transparent = GuiFiller.create(plugin, craftEngine, player);
            for (int slot : imageSlots) {
                if (slot >= 0 && slot < 54) inventory.setItem(slot, transparent);
            }
        }

        // 1. 홀로그램 렌더링
        if (selectedRecipe != null) {
            boolean allMet = true;
            // 같은 재료가 여러 칸에 나오면 합쳐서 따진다. 보유량은 재료당 한 번만 센다.
            Map<String, Integer> required = totalRequired(selectedRecipe);
            Map<String, Integer> owned = new java.util.HashMap<>();

            if (selectedRecipe.isShaped()) {
                List<String> shape = selectedRecipe.getShape();
                Map<Character, RecipeItem> keys = selectedRecipe.getKeys();

                for (int row = 0; row < 5; row++) {
                    String shapeRow = (row < shape.size()) ? shape.get(row) : "     ";
                    for (int col = 0; col < 5; col++) {
                        char keyChar = (col < shapeRow.length()) ? shapeRow.charAt(col) : ' ';
                        if (keyChar == ' ') continue;
                        RecipeItem req = keys.get(keyChar);
                        if (req == null) continue;

                        int need = required.getOrDefault(req.key(), req.amount());
                        int has = owned.computeIfAbsent(req.key(), k -> req.countIn(player, craftEngine));
                        if (has < need) allMet = false;
                        inventory.setItem(RECIPE_GRID[row * 5 + col],
                                createHologramIcon(req, need, has, craftEngine, player));
                    }
                }
            } else {
                List<RecipeItem> ingredients = selectedRecipe.getIngredients();
                for (int i = 0; i < RECIPE_GRID.length && i < ingredients.size(); i++) {
                    RecipeItem req = ingredients.get(i);
                    int need = required.getOrDefault(req.key(), req.amount());
                    int has = owned.computeIfAbsent(req.key(), k -> req.countIn(player, craftEngine));
                    if (has < need) allMet = false;
                    inventory.setItem(RECIPE_GRID[i],
                            createHologramIcon(req, need, has, craftEngine, player));
                }
            }
            this.canCraft = allMet;
        }

        // 2. 도감 버튼.
        // 배경 그림이 이미 그 자리에 책을 그려놨다. 여기에 아이템을 또 얹으면 그림을
        // 가리므로, 투명한 아이템으로 '누를 자리' 만 만든다.
        ItemStack book = GuiFiller.create(plugin, craftEngine, player,
                Component.text("레시피 도감").color(NamedTextColor.GREEN));
        ItemMeta bookMeta = book.getItemMeta();
        bookMeta.lore(List.of(
            Component.text("클릭하여 제작할 아이템을 선택합니다.").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        book.setItemMeta(bookMeta);
        inventory.setItem(BOOK_SLOT, book);

        // 3. 결과물(제작 버튼) 슬롯
        if (selectedRecipe != null) {
            if (canCraft) {
                ItemStack result = selectedRecipe.getResult().display(craftEngine, player);
                ItemMeta meta = result.getItemMeta();
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("⏱ 제작 시간: " + (selectedRecipe.getCraftingTimeTicks() / 20) + "초").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("XP 보상: " + selectedRecipe.getXpReward()).color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("클릭하여 즉시 제작!").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                meta.lore(lore);
                result.setItemMeta(meta);
                inventory.setItem(RESULT_SLOT, result);
            } else {
                ItemStack locked = new ItemStack(Material.BARRIER);
                ItemMeta meta = locked.getItemMeta();
                meta.displayName(Component.text("재료가 부족합니다").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                    Component.text("가방에 필요한 재료가 부족합니다.").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ));
                locked.setItemMeta(meta);
                inventory.setItem(RESULT_SLOT, locked);
            }
        }

        // 4. 대기열 슬롯
        for (int i = 0; i < QUEUE_SLOTS.length; i++) {
            if (i >= unlockedQueueSlots) {
                ItemStack lockedSlot = new ItemStack(Material.IRON_BARS);
                ItemMeta lockedMeta = lockedSlot.getItemMeta();
                lockedMeta.displayName(Component.text("잠긴 대기열").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                lockedMeta.lore(List.of(
                    Component.text("열쇠를 사용하여 확장할 수 있습니다.").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ));
                lockedSlot.setItemMeta(lockedMeta);
                inventory.setItem(QUEUE_SLOTS[i], lockedSlot);
            }
        }
    }

    /**
     * 레시피 한 칸에 필요한 재료의 전체 필요량.
     *
     * <p>정형 레시피는 같은 재료가 여러 칸에 나올 수 있어서 다 더해야 한다.
     * 무형도 같은 재료를 두 줄에 적었을 수 있으니 똑같이 더한다.
     */
    public static Map<String, Integer> totalRequired(CustomRecipe recipe) {
        Map<String, Integer> total = new java.util.HashMap<>();
        if (recipe.isShaped()) {
            List<String> shape = recipe.getShape();
            for (int row = 0; row < 5; row++) {
                String shapeRow = (row < shape.size()) ? shape.get(row) : "     ";
                for (int col = 0; col < 5; col++) {
                    char keyChar = (col < shapeRow.length()) ? shapeRow.charAt(col) : ' ';
                    if (keyChar == ' ') continue;
                    RecipeItem req = recipe.getKeys().get(keyChar);
                    if (req != null) total.merge(req.key(), req.amount(), Integer::sum);
                }
            }
        } else {
            for (RecipeItem req : recipe.getIngredients()) {
                total.merge(req.key(), req.amount(), Integer::sum);
            }
        }
        return total;
    }

    private ItemStack createHologramIcon(RecipeItem required, int requiredTotal, int playerHas,
                                         com.myserver.workbench.integration.CraftEngineBridge craftEngine,
                                         Player player) {
        boolean hasMaterial = playerHas >= requiredTotal;
        ItemStack icon = required.display(craftEngine, player);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(nameOf(icon, meta)
            .color(hasMaterial ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("총 필요량: " + requiredTotal + "개").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("가방 보유: " + playerHas + "개").color(hasMaterial ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));

        if (hasMaterial) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            lore.add(Component.text("✔ 충족").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("✘ 부족").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * 아이템에 붙은 이름. 커스텀 아이템은 자기 이름을 들고 오고, 바닐라는 재료 이름을 쓴다.
     */
    public static Component nameOf(ItemStack stack, ItemMeta meta) {
        if (meta.hasItemName()) return meta.itemName();
        if (meta.hasDisplayName()) return meta.displayName();
        return Component.text(formatMaterialName(stack.getType()));
    }

    public boolean canCraft() {
        return canCraft;
    }

    public String getCategory() {
        return category;
    }

    public CustomRecipe getSelectedRecipe() {
        return selectedRecipe;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}
