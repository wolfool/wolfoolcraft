package com.wolfool.workbench.editor;

import com.wolfool.workbench.recipe.CustomRecipe;
import com.wolfool.workbench.recipe.RecipeItem;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 편집 중인 레시피 하나.
 *
 * <p>GUI 가 들고 있는 임시 상태입니다. 저장을 눌러야 recipes.yml 로 넘어갑니다.
 *
 * <p>재료는 {@link ItemStack} 그대로 들고 있습니다. 저장할 때
 * {@link RecipeWriter} 가 CraftEngine id 인지 바닐라 재질인지 판단해 적습니다.
 * 편집 중에 미리 변환해 두면 되돌려 보여줄 때 원본을 잃습니다.
 */
public final class RecipeDraft {

    /** 5칸 × 5줄. recipes.yml 의 shape 와 같은 크기입니다. */
    public static final int GRID = 5;

    /** 편집 중인 레시피 id. 새로 만드는 중이면 null 입니다. */
    private @Nullable String id;
    /** 처음 열 때의 id. 이름을 바꿔 저장하면 옛 항목을 지워야 해서 들고 있습니다. */
    private final @Nullable String originalId;

    private String category;
    private String skillType;
    private double xpReward;
    private long craftingTimeSeconds;
    private int requiredProficiency;

    /** true 면 모양까지 맞아야 합니다 (shape + keys). false 면 재료만 맞으면 됩니다. */
    private boolean shaped;

    /** 5×5 격자. 빈 칸은 null 입니다. */
    private final ItemStack[] grid = new ItemStack[GRID * GRID];
    private @Nullable ItemStack result;

    private RecipeDraft(@Nullable String originalId) {
        this.originalId = originalId;
        this.id = originalId;
        this.category = "기본";
        this.skillType = "miner";
        this.xpReward = 10.0;
        this.craftingTimeSeconds = 10;
        this.requiredProficiency = 0;
        this.shaped = false;
    }

    /** 빈 레시피를 새로 만듭니다. */
    public static RecipeDraft blank(String defaultCategory) {
        RecipeDraft d = new RecipeDraft(null);
        d.category = defaultCategory;
        return d;
    }

    /**
     * 이미 있는 레시피를 불러옵니다.
     *
     * <p>재료를 화면에 놓으려면 실제 {@link ItemStack} 이 필요한데, CraftEngine 이
     * 아이템을 만들 때 플레이어를 들여다봅니다. 그래서 부르는 쪽에서 만든 아이템을
     * 받아 옵니다.
     *
     * @param gridItems 5×5 격자에 놓을 아이템. 길이 25 여야 합니다
     */
    public static RecipeDraft of(CustomRecipe recipe, ItemStack[] gridItems,
                                 @Nullable ItemStack resultItem) {
        RecipeDraft d = new RecipeDraft(recipe.getId());
        d.category = recipe.getCategory();
        d.skillType = recipe.getSkillType();
        d.xpReward = recipe.getXpReward();
        d.craftingTimeSeconds = Math.max(1, recipe.getCraftingTimeTicks() / 20L);
        d.requiredProficiency = recipe.getRequiredProficiency();
        d.shaped = recipe.isShaped();
        System.arraycopy(gridItems, 0, d.grid, 0, Math.min(gridItems.length, d.grid.length));
        d.result = resultItem;
        return d;
    }

    // ---------------- 격자 ----------------

    public ItemStack[] gridView() {
        return grid.clone();
    }

    public @Nullable ItemStack gridAt(int index) {
        return index < 0 || index >= grid.length ? null : grid[index];
    }

    public void gridAt(int index, @Nullable ItemStack stack) {
        if (index < 0 || index >= grid.length) return;
        grid[index] = stack == null || stack.getType().isAir() ? null : stack.clone();
    }

    /** 격자에 놓인 아이템 개수 (칸 수). */
    public int filledCells() {
        int n = 0;
        for (ItemStack s : grid) if (s != null) n++;
        return n;
    }

    // ---------------- 나머지 값 ----------------

    public @Nullable String id() {
        return id;
    }

    public void id(@Nullable String value) {
        this.id = value == null || value.isBlank() ? null : value.trim();
    }

    public @Nullable String originalId() {
        return originalId;
    }

    public boolean isNew() {
        return originalId == null;
    }

    public String category() {
        return category;
    }

    public void category(String value) {
        this.category = value;
    }

    public String skillType() {
        return skillType;
    }

    public void skillType(String value) {
        this.skillType = value;
    }

    public double xpReward() {
        return xpReward;
    }

    public void xpReward(double value) {
        this.xpReward = Math.max(0, value);
    }

    public long craftingTimeSeconds() {
        return craftingTimeSeconds;
    }

    public void craftingTimeSeconds(long value) {
        this.craftingTimeSeconds = Math.max(1, value);
    }

    public int requiredProficiency() {
        return requiredProficiency;
    }

    public void requiredProficiency(int value) {
        this.requiredProficiency = Math.max(0, value);
    }

    public boolean shaped() {
        return shaped;
    }

    public void shaped(boolean value) {
        this.shaped = value;
    }

    public @Nullable ItemStack result() {
        return result;
    }

    public void result(@Nullable ItemStack stack) {
        this.result = stack == null || stack.getType().isAir() ? null : stack.clone();
    }

    // ---------------- 검사 ----------------

    /**
     * 저장할 수 있는 상태인지 봅니다.
     *
     * @return 문제가 없으면 빈 목록
     */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        if (id == null) {
            out.add("id 를 정해 주세요.");
        } else if (!id.matches("[a-z0-9_]+")) {
            out.add("id 는 영소문자·숫자·밑줄만 쓸 수 있습니다: " + id);
        }
        if (result == null) out.add("결과물을 놓아 주세요.");
        if (filledCells() == 0) out.add("재료를 하나 이상 놓아 주세요.");
        if (shaped && filledCells() > 25) out.add("격자를 넘었습니다.");
        return out;
    }

    /**
     * 재료를 종류별로 합칩니다. 모양 없는 레시피를 저장할 때 씁니다.
     *
     * @return 재료 → 개수. 놓인 순서를 지킵니다
     */
    public Map<ItemStack, Integer> mergedIngredients(RecipeWriter writer) {
        return writer.merge(grid);
    }

    /** 화면에 쓸 요약 한 줄. */
    public String summary() {
        return (id == null ? "(이름 없음)" : id)
                + " · " + category
                + " · " + (shaped ? "모양 있음" : "모양 없음")
                + " · 재료 " + filledCells() + "칸";
    }

    /** 저장하지 않고 닫을 때 돌려줄 아이템. */
    public List<ItemStack> allItems() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : grid) if (s != null) out.add(s);
        if (result != null) out.add(result);
        return out;
    }

    /** {@link RecipeItem} 으로 바꾸기 전 원본 아이템. 테스트·로그용입니다. */
    @Override
    public String toString() {
        return "RecipeDraft{" + summary() + "}";
    }
}
