package com.wolfool.workbench.recipe;

import com.wolfool.workbench.integration.CraftEngineBridge;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 5×5 격자에 직접 올려놓은 재료로 레시피를 찾는다.
 *
 * <p>도감에서 고르면 가방을 훑어서 제작하지만, 그냥 작업대를 열고 재료를 올려놓고
 * 만드는 길도 있어야 한다. 이쪽은 <b>격자에 올린 것만</b> 소모한다.
 */
public final class GridMatcher {

    public static final int SIZE = 5;
    public static final int CELLS = SIZE * SIZE;

    /**
     * 찾은 레시피와, 어느 칸에서 몇 개를 뺄지.
     *
     * @param consume 격자 번호(0~24) → 뺄 개수
     */
    public record Match(CustomRecipe recipe, Map<Integer, Integer> consume) {
    }

    private GridMatcher() {
    }

    /** 격자와 맞는 레시피. 없으면 null. 격자가 비어 있어도 null. */
    public static @Nullable Match match(ItemStack[] grid, Collection<CustomRecipe> recipes,
                                        CraftEngineBridge craftEngine) {
        List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < CELLS; i++) {
            if (!isEmpty(grid[i])) filled.add(i);
        }
        if (filled.isEmpty()) return null;

        for (CustomRecipe recipe : recipes) {
            Match found = recipe.isShaped()
                    ? matchShaped(grid, filled, recipe, craftEngine)
                    : matchShapeless(grid, filled, recipe, craftEngine);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * 정형 레시피.
     *
     * <p>모양은 격자 어디에 놓아도 된다. 레시피가 차지하는 네모와 실제로 올려놓은 것이
     * 차지하는 네모의 크기가 같을 때만, 그 차이만큼 밀어서 칸끼리 맞춰 본다.
     */
    private static @Nullable Match matchShaped(ItemStack[] grid, List<Integer> filled,
                                               CustomRecipe recipe, CraftEngineBridge craftEngine) {
        List<String> shape = recipe.getShape();
        int rMin = SIZE, rMax = -1, cMin = SIZE, cMax = -1;
        for (int r = 0; r < SIZE; r++) {
            String row = r < shape.size() ? shape.get(r) : "";
            for (int c = 0; c < SIZE; c++) {
                if (charAt(row, c) == ' ') continue;
                rMin = Math.min(rMin, r);
                rMax = Math.max(rMax, r);
                cMin = Math.min(cMin, c);
                cMax = Math.max(cMax, c);
            }
        }
        if (rMax < 0) return null;   // 모양이 비었다

        int gRMin = SIZE, gRMax = -1, gCMin = SIZE, gCMax = -1;
        for (int index : filled) {
            int r = index / SIZE, c = index % SIZE;
            gRMin = Math.min(gRMin, r);
            gRMax = Math.max(gRMax, r);
            gCMin = Math.min(gCMin, c);
            gCMax = Math.max(gCMax, c);
        }
        if (rMax - rMin != gRMax - gRMin || cMax - cMin != gCMax - gCMin) return null;

        int dr = gRMin - rMin, dc = gCMin - cMin;
        Map<Integer, Integer> consume = new HashMap<>();
        for (int r = rMin; r <= rMax; r++) {
            String row = r < shape.size() ? shape.get(r) : "";
            for (int c = cMin; c <= cMax; c++) {
                int index = (r + dr) * SIZE + (c + dc);
                char key = charAt(row, c);
                ItemStack inCell = grid[index];

                if (key == ' ') {
                    if (!isEmpty(inCell)) return null;   // 비어 있어야 할 칸에 뭔가 있다
                    continue;
                }
                RecipeItem need = recipe.getKeys().get(key);
                if (need == null) return null;
                if (!need.matches(inCell, craftEngine) || inCell.getAmount() < need.amount()) return null;
                consume.put(index, need.amount());
            }
        }
        return new Match(recipe, consume);
    }

    /** 무형 레시피. 자리는 상관없고 종류와 개수만 본다. */
    private static @Nullable Match matchShapeless(ItemStack[] grid, List<Integer> filled,
                                                  CustomRecipe recipe, CraftEngineBridge craftEngine) {
        if (recipe.getIngredients().isEmpty()) return null;

        // 칸마다 아직 안 쓴 개수를 들고 가면서 재료를 하나씩 채운다.
        Map<Integer, Integer> left = new HashMap<>();
        for (int index : filled) left.put(index, grid[index].getAmount());

        Map<Integer, Integer> consume = new HashMap<>();
        for (RecipeItem need : recipe.getIngredients()) {
            int remaining = need.amount();
            for (int index : filled) {
                if (remaining <= 0) break;
                if (!need.matches(grid[index], craftEngine)) continue;
                int take = Math.min(remaining, left.get(index));
                if (take <= 0) continue;
                left.put(index, left.get(index) - take);
                consume.merge(index, take, Integer::sum);
                remaining -= take;
            }
            if (remaining > 0) return null;   // 재료가 모자라다
        }

        // 레시피와 상관없는 게 하나라도 올라와 있으면 안 된다.
        for (int index : filled) {
            if (!consume.containsKey(index)) return null;
        }
        return new Match(recipe, consume);
    }

    /** 찾은 대로 격자에서 재료를 뺀다. */
    public static void consume(ItemStack[] grid, Match match) {
        for (Map.Entry<Integer, Integer> entry : match.consume().entrySet()) {
            ItemStack stack = grid[entry.getKey()];
            if (stack == null) continue;
            stack.setAmount(Math.max(0, stack.getAmount() - entry.getValue()));
        }
    }

    private static char charAt(String row, int col) {
        return col < row.length() ? row.charAt(col) : ' ';
    }

    private static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }
}
