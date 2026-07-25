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

public class EncyclopediaGUI implements InventoryHolder {
    private final Inventory inventory;
    private final String currentCategory;
    
    // Right column slots for tabs
    public static final int[] TAB_SLOTS = {8, 17, 26, 35, 44};
    
    // Grid slots for recipes (0-7 per row, up to row 4) -> 8 * 5 = 40 slots
    public static final List<Integer> PAGE_SLOTS = new ArrayList<>();
    
    static {
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 8; c++) {
                PAGE_SLOTS.add(r * 9 + c);
            }
        }
    }

    public EncyclopediaGUI(org.bukkit.plugin.Plugin plugin, Player player, RecipeManager recipeManager, String currentCategory, int playerProficiency) {
        String titleChar = plugin.getConfig().getString("gui.encyclopedia.title-char", "\uF808\uE002");
        String titleText = plugin.getConfig().getString("gui.encyclopedia.title-text", "레시피 도감");
        this.inventory = Bukkit.createInventory(this, 54, Component.text(titleChar + " " + titleText).color(NamedTextColor.WHITE));
        
        // Find all unique categories
        Set<String> categories = new LinkedHashSet<>();
        for (CustomRecipe r : recipeManager.getRecipes().values()) {
            categories.add(r.getCategory());
        }
        List<String> catList = new ArrayList<>(categories);
        
        this.currentCategory = currentCategory == null && !catList.isEmpty() ? catList.get(0) : currentCategory;
        
        initializeItems(plugin, recipeManager, playerProficiency, catList);
    }

    private void initializeItems(org.bukkit.plugin.Plugin plugin, RecipeManager recipeManager, int playerProficiency, List<String> categories) {
        // 0. 이미지 전용 슬롯 설정 (클릭 불가, 투명)
        List<Integer> imageSlots = plugin.getConfig().getIntegerList("gui.image-slots.encyclopedia");
        if (imageSlots != null) {
            ItemStack transparent = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = transparent.getItemMeta();
            meta.displayName(Component.empty());
            meta.setCustomModelData(9999);
            transparent.setItemMeta(meta);
            
            for (int slot : imageSlots) {
                if (slot >= 0 && slot < 54) {
                    inventory.setItem(slot, transparent);
                }
            }
        }
        // Render Tabs
        for (int i = 0; i < TAB_SLOTS.length; i++) {
            if (i < categories.size()) {
                String cat = categories.get(i);
                ItemStack tabItem = new ItemStack(cat.equals(currentCategory) ? Material.LIME_BANNER : Material.RED_BANNER);
                ItemMeta meta = tabItem.getItemMeta();
                meta.displayName(Component.text(cat).color(cat.equals(currentCategory) ? NamedTextColor.GREEN : NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                if (cat.equals(currentCategory)) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
                tabItem.setItemMeta(meta);
                inventory.setItem(TAB_SLOTS[i], tabItem);
            }
        }
        
        // Render recipes for current category
        List<CustomRecipe> currentRecipes = new ArrayList<>();
        for (CustomRecipe r : recipeManager.getRecipes().values()) {
            if (r.getCategory().equals(this.currentCategory)) {
                currentRecipes.add(r);
            }
        }
        
        for (int i = 0; i < PAGE_SLOTS.size(); i++) {
            if (i < currentRecipes.size()) {
                CustomRecipe recipe = currentRecipes.get(i);
                if (playerProficiency >= recipe.getRequiredProficiency()) {
                    ItemStack icon = recipe.getResult().clone();
                    ItemMeta meta = icon.getItemMeta();
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.empty());
                    lore.add(Component.text("클릭하여 작업대에 올리기").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    icon.setItemMeta(meta);
                    inventory.setItem(PAGE_SLOTS.get(i), icon);
                } else {
                    ItemStack lockedIcon = new ItemStack(Material.GRAY_DYE);
                    ItemMeta meta = lockedIcon.getItemMeta();
                    meta.displayName(Component.text("???").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
                    meta.lore(List.of(
                        Component.text("요구 숙련도: " + recipe.getRequiredProficiency()).color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                    ));
                    lockedIcon.setItemMeta(meta);
                    inventory.setItem(PAGE_SLOTS.get(i), lockedIcon);
                }
            }
        }
        
        // Return button at bottom left
        ItemStack returnBtn = new ItemStack(Material.ARROW);
        ItemMeta returnMeta = returnBtn.getItemMeta();
        returnMeta.displayName(Component.text("작업대로 돌아가기").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        returnBtn.setItemMeta(returnMeta);
        inventory.setItem(45, returnBtn);
    }

    public String getCurrentCategory() {
        return currentCategory;
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
