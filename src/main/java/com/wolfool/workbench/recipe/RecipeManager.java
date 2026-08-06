package com.wolfool.workbench.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecipeManager {
    private final Plugin plugin;
    // 도감에 뜨는 순서가 recipes.yml 에 적은 순서와 같도록 순서를 지키는 맵을 쓴다.
    private final Map<String, CustomRecipe> recipes = new LinkedHashMap<>();
    /** 같은 검사 결과를 두 번 찍지 않으려고 들고 있는다. */
    private String lastValidationReport;

    public RecipeManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadRecipes() {
        recipes.clear();
        lastValidationReport = null;   // 다시 읽었으면 검사 결과도 다시 알려준다
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

            // 결과물. material 대신 item 에 CraftEngine ID 를 적어도 된다.
            RecipeItem result = RecipeItem.parse(plugin, recipeSec, "result", key + " 의 result");

            // 재료 (무형)
            List<RecipeItem> ingredients = new ArrayList<>();
            for (Map<?, ?> ingMap : recipeSec.getMapList("ingredients")) {
                ingredients.add(RecipeItem.parse(plugin, ingMap, key + " 의 ingredients"));
            }

            // Load settings
            String category = recipeSec.getString("category", "기본");
            String skillType = recipeSec.getString("skill-type", "forging");
            double xpReward = recipeSec.getDouble("xp-reward", 10.0);
            long timeSec = recipeSec.getLong("crafting-time", 10); // in seconds
            int proficiency = recipeSec.getInt("required-proficiency", 0);

            // Load Shaped
            List<String> shape = recipeSec.getStringList("shape");
            Map<Character, RecipeItem> keys = new HashMap<>();
            ConfigurationSection keysSec = recipeSec.getConfigurationSection("keys");
            if (keysSec != null) {
                for (String keyChar : keysSec.getKeys(false)) {
                    if (keyChar.isEmpty()) continue;
                    keys.put(keyChar.charAt(0),
                            RecipeItem.parse(plugin, keysSec, keyChar, key + " 의 keys." + keyChar));
                }
            }

            CustomRecipe recipe = new CustomRecipe(key, category, skillType, xpReward, result, ingredients, timeSec * 20L, proficiency, shape, keys);
            recipes.put(key, recipe);
        }
        
        plugin.getLogger().info("Loaded " + recipes.size() + " custom recipes.");
    }

    /**
     * 레시피에 적힌 CraftEngine 아이템이 실제로 있는지 본다.
     *
     * <p>없는 ID 는 GUI 를 열어야 티가 나고, 그때는 재료가 종이로 보일 뿐 이유를
     * 알 수 없다. 오타를 서버 켤 때 바로 알려준다.
     */
    public void validateCustomItems(com.wolfool.workbench.integration.CraftEngineBridge craftEngine) {
        if (!craftEngine.isAvailable()) return;

        java.util.Set<String> checked = new java.util.LinkedHashSet<>();
        java.util.Set<String> missing = new java.util.LinkedHashSet<>();
        for (CustomRecipe recipe : recipes.values()) {
            List<RecipeItem> all = new ArrayList<>(recipe.getIngredients());
            all.addAll(recipe.getKeys().values());
            all.add(recipe.getResult());
            for (RecipeItem item : all) {
                String id = item.craftEngineId();
                if (id == null) continue;
                checked.add(id);
                if (!craftEngine.hasItem(id)) {
                    missing.add(recipe.getId() + " -> " + id);
                }
            }
        }

        if (checked.isEmpty()) return;

        // CraftEngine 은 켜질 때 리로드 이벤트를 두 번 날린다. 결과가 그대로면 잠자코 있는다.
        String report = checked.size() + "/" + missing;
        if (report.equals(lastValidationReport)) return;
        lastValidationReport = report;

        for (String entry : missing) {
            plugin.getLogger().warning("레시피가 없는 CraftEngine 아이템을 가리킨다: " + entry);
        }
        if (missing.isEmpty()) {
            plugin.getLogger().info("레시피가 쓰는 CraftEngine 아이템 " + checked.size() + "종 확인 완료.");
        }
    }

    public Map<String, CustomRecipe> getRecipes() {
        return recipes;
    }
}
