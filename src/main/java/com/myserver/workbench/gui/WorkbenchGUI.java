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

    public WorkbenchGUI(org.bukkit.plugin.Plugin plugin, Player player, int unlockedQueueSlots, RecipeManager recipeManager, int playerProficiency, String category, CustomRecipe selectedRecipe) {
        this.category = category;
        this.selectedRecipe = selectedRecipe;
        
        String titleChar = plugin.getConfig().getString("gui.workbench.title-char", "\uF808\uE001");
        String titleText = plugin.getConfig().getString("gui.workbench.title-text", "작업대");
        this.inventory = Bukkit.createInventory(this, 54, Component.text(titleChar + " " + titleText).color(NamedTextColor.WHITE));
        
        initializeItems(plugin, player, unlockedQueueSlots);
    }

    private void initializeItems(org.bukkit.plugin.Plugin plugin, Player player, int unlockedQueueSlots) {
        // 0. 투명 유리 (image-slots)
        List<Integer> imageSlots = plugin.getConfig().getIntegerList("gui.image-slots.workbench");
        if (imageSlots != null) {
            ItemStack transparent = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = transparent.getItemMeta();
            meta.displayName(Component.empty());
            meta.setCustomModelData(9999);
            transparent.setItemMeta(meta);
            for (int slot : imageSlots) {
                if (slot >= 0 && slot < 54) inventory.setItem(slot, transparent);
            }
        }

        // 1. 홀로그램 렌더링
        if (selectedRecipe != null) {
            boolean allMet = true;
            
            if (selectedRecipe.isShaped()) {
                // 정형 레시피 렌더링
                List<String> shape = selectedRecipe.getShape();
                Map<Character, ItemStack> keys = selectedRecipe.getKeys();
                
                // 가방 내 재료 수량 파악 (재료별 전체 필요량 vs 전체 보유량)
                Map<Material, Integer> totalRequired = new java.util.HashMap<>();
                for (int row = 0; row < 5; row++) {
                    String shapeRow = (row < shape.size()) ? shape.get(row) : "     ";
                    for (int col = 0; col < 5; col++) {
                        char keyChar = (col < shapeRow.length()) ? shapeRow.charAt(col) : ' ';
                        if (keyChar != ' ') {
                            ItemStack req = keys.get(keyChar);
                            if (req != null) {
                                totalRequired.put(req.getType(), totalRequired.getOrDefault(req.getType(), 0) + req.getAmount());
                            }
                        }
                    }
                }
                
                // 각 재료별로 가방 검사
                for (Map.Entry<Material, Integer> entry : totalRequired.entrySet()) {
                    if (countMaterial(player, entry.getKey()) < entry.getValue()) {
                        allMet = false;
                        break;
                    }
                }
                
                // 그리드 시각적 표시
                for (int row = 0; row < 5; row++) {
                    String shapeRow = (row < shape.size()) ? shape.get(row) : "     ";
                    for (int col = 0; col < 5; col++) {
                        int slot = RECIPE_GRID[row * 5 + col];
                        char keyChar = (col < shapeRow.length()) ? shapeRow.charAt(col) : ' ';
                        
                        if (keyChar != ' ') {
                            ItemStack req = keys.get(keyChar);
                            if (req != null) {
                                int totalReqAmount = totalRequired.get(req.getType());
                                int playerHas = countMaterial(player, req.getType());
                                inventory.setItem(slot, createHologramIcon(req, totalReqAmount, playerHas));
                            }
                        }
                    }
                }
            } else {
                // 무형 레시피 렌더링
                List<ItemStack> ingredients = selectedRecipe.getIngredients();
                for (int i = 0; i < RECIPE_GRID.length; i++) {
                    if (i < ingredients.size()) {
                        ItemStack req = ingredients.get(i);
                        int playerHas = countMaterial(player, req.getType());
                        if (playerHas < req.getAmount()) allMet = false;
                        inventory.setItem(RECIPE_GRID[i], createHologramIcon(req, req.getAmount(), playerHas));
                    }
                }
            }
            this.canCraft = allMet;
        }

        // 2. 도감 버튼
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta bookMeta = book.getItemMeta();
        bookMeta.displayName(Component.text("레시피 도감").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        bookMeta.lore(List.of(
            Component.text("클릭하여 제작할 아이템을 선택합니다.").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        book.setItemMeta(bookMeta);
        inventory.setItem(BOOK_SLOT, book);

        // 3. 결과물(제작 버튼) 슬롯
        if (selectedRecipe != null) {
            if (canCraft) {
                ItemStack result = selectedRecipe.getResult().clone();
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

    private ItemStack createHologramIcon(ItemStack required, int requiredTotal, int playerHas) {
        boolean hasMaterial = playerHas >= requiredTotal;
        ItemStack icon = new ItemStack(required.getType(), required.getAmount());
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(formatMaterialName(required.getType()))
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

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
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
