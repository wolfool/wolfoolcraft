package com.myserver.workbench.gui;

import com.myserver.workbench.recipe.CustomRecipe;
import com.myserver.workbench.recipe.RecipeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자 전용 도감 관리 GUI
 * - 전체 레시피 조회
 * - 카테고리별 필터링
 * - 레시피 상세 정보 확인 (재료, 시간, 스킬, XP)
 * - 레시피 활성화/비활성화 (TODO: 추후 확장)
 */
public class AdminEncyclopediaGUI implements InventoryHolder {
    private final Inventory inventory;
    private final String currentCategory;

    // 탭 슬롯 (우측)
    public static final int[] TAB_SLOTS = {8, 17, 26, 35, 44};
    // 레시피 그리드 (좌측 8열 x 5행)
    public static final List<Integer> PAGE_SLOTS = new ArrayList<>();

    static {
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 8; c++) {
                PAGE_SLOTS.add(r * 9 + c);
            }
        }
    }

    // 하단 네비게이션
    public static final int RELOAD_SLOT = 49;
    public static final int INFO_SLOT = 45;

    public AdminEncyclopediaGUI(Player player, RecipeManager recipeManager, String currentCategory) {
        this.inventory = Bukkit.createInventory(this, 54, Component.text("§4[관리] 도감 관리").color(NamedTextColor.DARK_RED));

        Set<String> categories = new LinkedHashSet<>();
        for (CustomRecipe r : recipeManager.getRecipes().values()) {
            categories.add(r.getCategory());
        }
        List<String> catList = new ArrayList<>(categories);
        this.currentCategory = currentCategory == null && !catList.isEmpty() ? catList.get(0) : currentCategory;

        initializeItems(recipeManager, catList);
    }

    private void initializeItems(RecipeManager recipeManager, List<String> categories) {
        // 1. 카테고리 탭
        for (int i = 0; i < TAB_SLOTS.length; i++) {
            if (i < categories.size()) {
                String cat = categories.get(i);
                boolean isActive = cat.equals(currentCategory);
                ItemStack tabItem = new ItemStack(isActive ? Material.LIME_BANNER : Material.RED_BANNER);
                ItemMeta meta = tabItem.getItemMeta();
                meta.displayName(Component.text(cat).color(isActive ? NamedTextColor.GREEN : NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                if (isActive) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
                tabItem.setItemMeta(meta);
                inventory.setItem(TAB_SLOTS[i], tabItem);
            }
        }

        // 2. 현재 카테고리의 레시피 (상세 정보 포함)
        List<CustomRecipe> currentRecipes = new ArrayList<>();
        for (CustomRecipe r : recipeManager.getRecipes().values()) {
            if (r.getCategory().equals(this.currentCategory)) {
                currentRecipes.add(r);
            }
        }

        for (int i = 0; i < PAGE_SLOTS.size(); i++) {
            if (i < currentRecipes.size()) {
                CustomRecipe recipe = currentRecipes.get(i);
                ItemStack icon = recipe.getResult().clone();
                ItemMeta meta = icon.getItemMeta();

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("ID: " + recipe.getId()).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("카테고리: " + recipe.getCategory()).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("--- 재료 ---").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                for (ItemStack ing : recipe.getIngredients()) {
                    lore.add(Component.text("  • " + WorkbenchGUI.formatMaterialName(ing.getType()) + " x" + ing.getAmount())
                        .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                }
                lore.add(Component.empty());
                lore.add(Component.text("⏱ 제작 시간: " + (recipe.getCraftingTimeTicks() / 20) + "초").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("🎯 요구 숙련도: " + recipe.getRequiredProficiency()).color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("⚔ 스킬: " + recipe.getSkillType()).color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("✨ XP 보상: " + recipe.getXpReward()).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));

                meta.lore(lore);
                icon.setItemMeta(meta);
                inventory.setItem(PAGE_SLOTS.get(i), icon);
            }
        }

        // 3. 하단 버튼들
        // Reload 버튼
        ItemStack reload = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta reloadMeta = reload.getItemMeta();
        reloadMeta.displayName(Component.text("§c레시피 리로드").decoration(TextDecoration.ITALIC, false));
        reloadMeta.lore(List.of(
            Component.text("클릭 시 recipes.yml을 다시 읽어옵니다.").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        reload.setItemMeta(reloadMeta);
        inventory.setItem(RELOAD_SLOT, reload);

        // Info 버튼
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("§e도감 관리 안내").decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(List.of(
            Component.text("총 레시피: " + recipeManager.getRecipes().size() + "개").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
            Component.text("총 카테고리: " + categories.size() + "개").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);
    }

    public String getCurrentCategory() {
        return currentCategory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
