package com.wolfool.workbench.recipe;

import java.util.List;
import java.util.Map;

public class CustomRecipe {
    private final String id;
    private final String category;
    private final String skillType;
    private final double xpReward;
    private final RecipeItem result;
    private final List<RecipeItem> ingredients;
    private final long craftingTimeTicks;
    private final int requiredProficiency;
    private final List<String> shape;
    private final Map<Character, RecipeItem> keys;

    public CustomRecipe(String id, String category, String skillType, double xpReward, RecipeItem result, List<RecipeItem> ingredients, long craftingTimeTicks, int requiredProficiency, List<String> shape, Map<Character, RecipeItem> keys) {
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

    public RecipeItem getResult() {
        return result;
    }

    public List<RecipeItem> getIngredients() {
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

    public Map<Character, RecipeItem> getKeys() {
        return keys;
    }

    public boolean isShaped() {
        return shape != null && !shape.isEmpty() && keys != null && !keys.isEmpty();
    }
}
