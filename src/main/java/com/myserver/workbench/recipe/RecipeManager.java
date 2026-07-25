package com.myserver.workbench.recipe;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeManager {
    private final Plugin plugin;
    private final Map<String, CustomRecipe> recipes = new HashMap<>();

    public RecipeManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadRecipes() {
        recipes.clear();
        File file = new File(plugin.getDataFolder(), "recipes.yml");
        if (!file.exists()) {
            plugin.saveResource("recipes.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("recipes");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection recipeSec = section.getConfigurationSection(key);
            if (recipeSec == null) continue;

            // Load Result
            String resultMatName = recipeSec.getString("result.material", "STONE");
            int amount = recipeSec.getInt("result.amount", 1);
            ItemStack result = new ItemStack(Material.valueOf(resultMatName.toUpperCase()), amount);
            // TODO: Support CraftEngine items via API instead of standard Material

            // Load Ingredients
            List<ItemStack> ingredients = new ArrayList<>();
            List<Map<?, ?>> ingList = recipeSec.getMapList("ingredients");
            for (Map<?, ?> ingMap : ingList) {
                String mat = ingMap.containsKey("material") ? (String) ingMap.get("material") : "STONE";
                int amt = ingMap.containsKey("amount") ? (Integer) ingMap.get("amount") : 1;
                ingredients.add(new ItemStack(Material.valueOf(mat.toUpperCase()), amt));
            }

            // Load settings
            String category = recipeSec.getString("category", "기본");
            String skillType = recipeSec.getString("skill-type", "forging");
            double xpReward = recipeSec.getDouble("xp-reward", 10.0);
            long timeSec = recipeSec.getLong("crafting-time", 10); // in seconds
            int proficiency = recipeSec.getInt("required-proficiency", 0);

            // Load Shaped
            List<String> shape = recipeSec.getStringList("shape");
            Map<Character, ItemStack> keys = new HashMap<>();
            ConfigurationSection keysSec = recipeSec.getConfigurationSection("keys");
            if (keysSec != null) {
                for (String keyChar : keysSec.getKeys(false)) {
                    if (keyChar.length() > 0) {
                        String matName = keysSec.getString(keyChar + ".material", "STONE");
                        int amt = keysSec.getInt(keyChar + ".amount", 1);
                        keys.put(keyChar.charAt(0), new ItemStack(Material.valueOf(matName.toUpperCase()), amt));
                    }
                }
            }

            CustomRecipe recipe = new CustomRecipe(key, category, skillType, xpReward, result, ingredients, timeSec * 20L, proficiency, shape, keys);
            recipes.put(key, recipe);
        }
        
        plugin.getLogger().info("Loaded " + recipes.size() + " custom recipes.");
    }

    public Map<String, CustomRecipe> getRecipes() {
        return recipes;
    }
}
