package com.myserver.workbench.recipe;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Map;

public class CustomRecipe {
    private final String id;
    private final String category;
    private final String skillType;
    private final double xpReward;
    private final ItemStack result;
    private final List<ItemStack> ingredients;
    private final long craftingTimeTicks;
    private final int requiredProficiency;
    private final List<String> shape;
    private final Map<Character, ItemStack> keys;

    public CustomRecipe(String id, String category, String skillType, double xpReward, ItemStack result, List<ItemStack> ingredients, long craftingTimeTicks, int requiredProficiency, List<String> shape, Map<Character, ItemStack> keys) {
        this.id = id;
        this.category = category;
        this.skillType = skillType;
        this.xpReward = xpReward;
        this.result = result;
        this.ingredients = ingredients;
        this.craftingTimeTicks = craftingTimeTicks;
        this.requiredProficiency = requiredProficiency;
        this.shape = shape;
        this.keys = keys;
    }

    public String getId() {
        return id;
    }
    
    public String getCategory() {
        return category;
    }

    public String getSkillType() {
        return skillType;
    }

    public double getXpReward() {
        return xpReward;
    }

    public ItemStack getResult() {
        return result;
    }

    public List<ItemStack> getIngredients() {
        return ingredients;
    }

    public long getCraftingTimeTicks() {
        return craftingTimeTicks;
    }

    public int getRequiredProficiency() {
        return requiredProficiency;
    }

    public List<String> getShape() {
        return shape;
    }

    public Map<Character, ItemStack> getKeys() {
        return keys;
    }

    public boolean isShaped() {
        return shape != null && !shape.isEmpty() && keys != null && !keys.isEmpty();
    }
}
