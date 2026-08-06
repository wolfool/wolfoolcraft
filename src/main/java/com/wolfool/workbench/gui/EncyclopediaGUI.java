package com.wolfool.workbench.gui;

import com.wolfool.workbench.recipe.CustomRecipe;
import com.wolfool.workbench.recipe.RecipeManager;
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
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 레시피 도감.
 *
 * <p>탭 5개는 배경 그림이 통째로 갈린다({@link EncyclopediaTab}). 눌린 탭은 그림이
 * 보여주므로 탭 자리에는 투명한 아이템만 놓고, 색깔 배너 같은 건 쓰지 않는다.
 *
 * <p>한 장에 35개까지 놓고 넘치면 아래 양쪽 끝 화살표로 넘긴다.
 */
public class EncyclopediaGUI implements InventoryHolder {

    /**
     * 오른쪽 탭 띠. 위에서부터 배경 그림의 탭 순서와 같다.
     *
     * <p>그림에서 탭 아이콘은 GUI 좌표 x 132~155 에 있어서 <b>7열</b>에 걸친다.
     * 8열은 띠의 오른쪽 꼬리라 거기를 누르게 두면 아이콘을 눌러도 안 먹는다.
     */
    public static final int[] TAB_SLOTS = {7, 16, 25, 34, 43};

    /** 책 아래 양쪽 끝. 넘길 장이 있을 때만 화살표가 놓인다. */
    public static final int PREV_SLOT = 45;
    public static final int NEXT_SLOT = 53;

    /**
     * 레시피가 놓이는 칸. 배경 그림의 책 칸이 가로 7칸이라 0~6열만 쓴다.
     * 7열은 탭, 8열은 탭 띠의 꼬리다.
     */
    public static final List<Integer> PAGE_SLOTS = new ArrayList<>();

    static {
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 7; col++) {
                PAGE_SLOTS.add(row * 9 + col);
            }
        }
    }

    private final Inventory inventory;
    private final List<EncyclopediaTab> tabs;
    private final int tabIndex;
    private final int page;
    private final int pageCount;

    /**
     * @param currentTab 지금 열 탭의 이름. null 이거나 모르는 이름이면 첫 탭
     * @param page       0부터. 범위를 벗어나면 가까운 쪽으로 붙는다
     */
    public EncyclopediaGUI(Plugin plugin, Player player, RecipeManager recipeManager,
                           String currentTab, int page,
                           com.wolfool.workbench.integration.SkillBridge skills,
                           com.wolfool.workbench.integration.CraftEngineBridge craftEngine) {
        this.tabs = EncyclopediaTab.load(plugin);
        this.tabIndex = indexOf(tabs, currentTab);

        List<CustomRecipe> shown = recipesFor(tabs, tabIndex, recipeManager);
        int perPage = PAGE_SLOTS.size();
        this.pageCount = Math.max(1, (shown.size() + perPage - 1) / perPage);
        this.page = Math.max(0, Math.min(page, pageCount - 1));

        String glyph = tabIndex >= 0 ? tabs.get(tabIndex).glyph() : null;
        this.inventory = Bukkit.createInventory(this, 54,
                GuiTitle.of(plugin, "encyclopedia", "레시피 도감", glyph));

        initializeItems(plugin, player, skills, craftEngine, shown);
    }

    /** 이름으로 탭을 찾는다. 못 찾으면 첫 탭, 탭 설정이 아예 없으면 -1. */
    private static int indexOf(List<EncyclopediaTab> tabs, String name) {
        if (tabs.isEmpty()) return -1;
        if (name != null) {
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).name().equals(name)) return i;
            }
        }
        return 0;
    }

    /**
     * 그 탭에 보일 레시피 <b>전부</b>. 장 나누기는 하지 않는다.
     *
     * <p>GUI 를 그릴 때와 클릭을 받을 때 같은 순서가 나와야 몇 번째를 눌렀는지가
     * 맞아떨어진다. 그래서 양쪽 다 이 메서드만 쓴다.
     */
    public static List<CustomRecipe> recipesFor(List<EncyclopediaTab> tabs, int tabIndex,
                                                RecipeManager recipeManager) {
        List<CustomRecipe> all = new ArrayList<>(recipeManager.getRecipes().values());
        if (tabIndex < 0 || tabIndex >= tabs.size()) return all;

        // REST 탭 판단용. 다른 탭이 이름으로 집어간 카테고리를 모은다.
        Set<String> claimed = new HashSet<>();
        for (EncyclopediaTab tab : tabs) {
            if (tab.mode() == EncyclopediaTab.Mode.LIST) claimed.addAll(tab.categories());
        }

        EncyclopediaTab selected = tabs.get(tabIndex);
        List<CustomRecipe> shown = new ArrayList<>();
        for (CustomRecipe recipe : all) {
            if (selected.accepts(recipe.getCategory(), claimed)) shown.add(recipe);
        }
        return shown;
    }

    /** 화면에 뜬 n 번째 칸이 전체 목록의 몇 번째인지. */
    public int recipeIndexAt(int pageSlotIndex) {
        return page * PAGE_SLOTS.size() + pageSlotIndex;
    }

    private void initializeItems(Plugin plugin, Player player,
                                 com.wolfool.workbench.integration.SkillBridge skills,
                                 com.wolfool.workbench.integration.CraftEngineBridge craftEngine,
                                 List<CustomRecipe> shown) {
        // 0. 그림만 보여야 하는 칸을 투명 아이템으로 막는다.
        for (int slot : plugin.getConfig().getIntegerList("gui.image-slots.encyclopedia")) {
            if (slot >= 0 && slot < 54) {
                inventory.setItem(slot, GuiFiller.create(plugin, craftEngine, player));
            }
        }

        // 1. 탭. 눌린 모습은 배경 그림이 이미 그리고 있으므로 여기선 누를 자리만 만든다.
        for (int i = 0; i < TAB_SLOTS.length && i < tabs.size(); i++) {
            Component name = Component.text(tabs.get(i).name())
                    .color(i == tabIndex ? NamedTextColor.GREEN : NamedTextColor.WHITE);
            inventory.setItem(TAB_SLOTS[i], GuiFiller.create(plugin, craftEngine, player, name));
        }

        // 2. 이 장의 레시피
        int from = page * PAGE_SLOTS.size();
        for (int i = 0; i < PAGE_SLOTS.size() && from + i < shown.size(); i++) {
            inventory.setItem(PAGE_SLOTS.get(i), iconFor(player, shown.get(from + i), skills, craftEngine));
        }

        // 3. 장 넘기기. 넘길 데가 없으면 화살표도 안 놓는다.
        if (page > 0) {
            inventory.setItem(PREV_SLOT, pageArrow("이전 장"));
        }
        if (page < pageCount - 1) {
            inventory.setItem(NEXT_SLOT, pageArrow("다음 장"));
        }
    }

    private ItemStack pageArrow(String label) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.displayName(Component.text(label)
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text((page + 1) + " / " + pageCount + " 장")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        arrow.setItemMeta(meta);
        return arrow;
    }

    /** 숙련도가 모자라면 내용을 가린 아이콘을 준다. */
    private ItemStack iconFor(Player player, CustomRecipe recipe,
                              com.wolfool.workbench.integration.SkillBridge skills,
                              com.wolfool.workbench.integration.CraftEngineBridge craftEngine) {
        if (!skills.meets(player, recipe.getSkillType(), recipe.getRequiredProficiency())) {
            ItemStack locked = new ItemStack(Material.GRAY_DYE);
            ItemMeta meta = locked.getItemMeta();
            meta.displayName(Component.text("???")
                    .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("요구 숙련도: " + recipe.getRequiredProficiency())
                    .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
            locked.setItemMeta(meta);
            return locked;
        }

        ItemStack icon = recipe.getResult().display(craftEngine, player);
        ItemMeta meta = icon.getItemMeta();
        meta.lore(List.of(
                Component.empty(),
                Component.text("클릭하여 작업대에 올리기")
                        .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));
        icon.setItemMeta(meta);
        return icon;
    }

    public List<EncyclopediaTab> getTabs() {
        return tabs;
    }

    public int getTabIndex() {
        return tabIndex;
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return pageCount;
    }

    /** 지금 열린 탭의 이름. 작업대 GUI 를 오갈 때 이 값으로 탭을 기억한다. */
    public String getCurrentCategory() {
        return tabIndex >= 0 && tabIndex < tabs.size() ? tabs.get(tabIndex).name() : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
