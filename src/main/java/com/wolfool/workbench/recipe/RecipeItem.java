package com.wolfool.workbench.recipe;

import com.wolfool.workbench.integration.CraftEngineBridge;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * 레시피에 적는 아이템 한 종류.
 *
 * <p>바닐라 재료와 CraftEngine 커스텀 아이템을 같이 다룬다. yml 에서는 이렇게 적는다.
 *
 * <pre>
 *   material: DIAMOND     # 바닐라
 *   amount: 2
 *
 *   item: "wood1:wood"    # CraftEngine 아이템
 *   amount: 50
 * </pre>
 *
 * <p>커스텀 아이템은 대부분 종이({@code paper}) 같은 흔한 재료를 바탕으로 만들어진다.
 * 그래서 <b>바닐라 재료를 셀 때는 커스텀 아이템을 빼야 한다.</b> 안 그러면 종이 기반
 * 커스텀 아이템 50개가 '종이 50개' 로 잡혀서 엉뚱하게 제작이 된다.
 */
public final class RecipeItem {

    /** CraftEngine 아이템 ID. 바닐라면 null. */
    private final @Nullable String craftEngineId;
    /** 바닐라 재료. 커스텀 아이템이면 그림을 못 만들었을 때 쓸 대체품이다. */
    private final Material material;
    private final int amount;

    private RecipeItem(@Nullable String craftEngineId, Material material, int amount) {
        this.craftEngineId = craftEngineId;
        this.material = material;
        this.amount = amount;
    }

    public int amount() {
        return amount;
    }

    public boolean isCustom() {
        return craftEngineId != null;
    }

    public @Nullable String craftEngineId() {
        return craftEngineId;
    }

    /** 수량을 합칠 때 쓰는 식별자. 같은 재료면 같은 값이 나온다. */
    public String key() {
        return craftEngineId != null ? craftEngineId : material.name();
    }

    // ---------------- 읽기 ----------------

    /**
     * 설정 한 덩어리를 재료로 읽는다.
     *
     * @param where 경고 메시지에 쓸 위치 표시 (예: {@code "wood_bundle 의 result"})
     */
    public static RecipeItem parse(Plugin plugin, Map<?, ?> map, String where) {
        Object custom = map.get("item");
        Object vanilla = map.get("material");
        Object amountRaw = map.get("amount");
        int amount = amountRaw instanceof Number n ? n.intValue() : 1;
        return build(plugin, str(custom), str(vanilla), amount, where);
    }

    /** {@code keys} 처럼 섹션으로 들어오는 자리에서 쓴다. */
    public static RecipeItem parse(Plugin plugin, org.bukkit.configuration.ConfigurationSection sec,
                                   String path, String where) {
        return build(plugin,
                sec.getString(path + ".item"),
                sec.getString(path + ".material"),
                sec.getInt(path + ".amount", 1),
                where);
    }

    private static RecipeItem build(Plugin plugin, @Nullable String custom, @Nullable String vanilla,
                                    int amount, String where) {
        if (amount < 1) amount = 1;

        if (custom != null && !custom.isBlank()) {
            // 커스텀 아이템도 바탕 재료를 적어두면 CraftEngine 이 없을 때 그거로 보여준다.
            Material fallback = material(plugin, vanilla, where, Material.PAPER);
            return new RecipeItem(custom.trim(), fallback, amount);
        }
        return new RecipeItem(null, material(plugin, vanilla, where, Material.STONE), amount);
    }

    private static Material material(Plugin plugin, @Nullable String name, String where, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material found = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        if (found != null) return found;
        plugin.getLogger().warning(where + " 의 재료 '" + name + "' 를 모르겠다. "
                + fallback + " 로 둔다.");
        return fallback;
    }

    private static @Nullable String str(@Nullable Object o) {
        return o == null ? null : String.valueOf(o);
    }

    // ---------------- 판별 ----------------

    /** 이 칸에 들어갈 수 있는 아이템인지. */
    public boolean matches(@Nullable ItemStack stack, CraftEngineBridge craftEngine) {
        if (stack == null || stack.getType().isAir()) return false;
        String stackId = craftEngine.itemId(stack);
        if (craftEngineId != null) {
            return craftEngineId.equals(stackId);
        }
        // 바닐라 재료 자리에는 커스텀 아이템을 넣을 수 없다 (위 주석 참고).
        return stackId == null && stack.getType() == material;
    }

    /** 가방에 몇 개나 있는지. */
    public int countIn(Player player, CraftEngineBridge craftEngine) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (matches(stack, craftEngine)) total += stack.getAmount();
        }
        return total;
    }

    /**
     * 가방에서 그만큼 뺀다.
     *
     * @return 실제로 뺀 개수. 모자라면 요청보다 적다
     */
    public int takeFrom(Player player, CraftEngineBridge craftEngine, int wanted) {
        int left = wanted;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (left <= 0) break;
            if (!matches(stack, craftEngine)) continue;
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            left -= take;
        }
        return wanted - left;
    }

    // ---------------- 보여주기 ----------------

    /**
     * 화면에 놓을 아이템.
     *
     * <p>{@code forPlayer} 는 반드시 있어야 한다. CraftEngine 이 아이템을 만들 때
     * 플레이어를 들여다보기 때문이다.
     */
    public ItemStack display(CraftEngineBridge craftEngine, Player forPlayer) {
        if (craftEngineId != null) {
            ItemStack made = craftEngine.createItem(craftEngineId, forPlayer);
            if (made != null) {
                made.setAmount(Math.min(amount, made.getMaxStackSize()));
                return made;
            }
        }
        return new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
    }
}
