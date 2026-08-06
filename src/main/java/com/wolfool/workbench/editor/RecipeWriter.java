package com.wolfool.workbench.editor;

import com.wolfool.workbench.integration.CraftEngineBridge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 편집한 레시피를 recipes.yml 에 씁니다.
 *
 * <p>사람이 손으로 적은 주석이 많은 파일이라 통째로 다시 쓰지 않습니다.
 * Bukkit 의 {@link YamlConfiguration} 이 해당 항목만 갈아 끼우고 나머지는
 * 그대로 둡니다.
 *
 * <p><b>주의.</b> YamlConfiguration 으로 저장하면 <b>파일 전체의 주석이
 * 사라집니다.</b> 그래서 저장하기 전에 원본을 {@code recipes.yml.bak-editor} 로
 * 복사해 둡니다. 주석이 필요하시면 그 파일에서 되살리시면 됩니다.
 */
public final class RecipeWriter {

    private final Plugin plugin;
    private final CraftEngineBridge craftEngine;

    public RecipeWriter(Plugin plugin, CraftEngineBridge craftEngine) {
        this.plugin = plugin;
        this.craftEngine = craftEngine;
    }

    private File file() {
        return new File(plugin.getDataFolder(), "recipes.yml");
    }

    /**
     * 아이템 하나를 레시피 한 칸으로 적습니다.
     *
     * <p>CraftEngine 아이템이면 {@code item:} 에 id 를, 아니면 {@code material:} 에
     * 재질을 적습니다. CraftEngine 아이템에도 {@code material:} 을 같이 적어 두면
     * CraftEngine 이 없을 때 그 재질로 보여 줍니다.
     */
    private void writeItem(ConfigurationSection sec, ItemStack stack, int amount) {
        String id = craftEngine.itemId(stack);
        if (id != null) {
            sec.set("item", id);
            sec.set("material", stack.getType().name());
        } else {
            sec.set("material", stack.getType().name());
        }
        sec.set("amount", Math.max(1, amount));
    }

    /** 같은 재료끼리 개수를 합칩니다. 놓인 순서를 지킵니다. */
    public Map<ItemStack, Integer> merge(ItemStack[] grid) {
        Map<String, ItemStack> byKey = new LinkedHashMap<>();
        Map<String, Integer> count = new LinkedHashMap<>();
        for (ItemStack s : grid) {
            if (s == null || s.getType().isAir()) continue;
            String key = keyOf(s);
            byKey.putIfAbsent(key, s);
            count.merge(key, s.getAmount(), Integer::sum);
        }
        Map<ItemStack, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, ItemStack> e : byKey.entrySet()) {
            out.put(e.getValue(), count.get(e.getKey()));
        }
        return out;
    }

    /** 같은 재료인지 판단하는 값. CraftEngine 아이템은 id 로, 나머지는 재질로 봅니다. */
    public String keyOf(ItemStack stack) {
        String id = craftEngine.itemId(stack);
        return id != null ? id : stack.getType().name();
    }

    /**
     * 저장합니다.
     *
     * @return 문제가 있으면 그 내용, 없으면 null
     */
    public @Nullable String save(RecipeDraft draft) {
        List<String> problems = draft.problems();
        if (!problems.isEmpty()) return String.join(" / ", problems);

        File f = file();
        if (!f.exists()) plugin.saveResource("recipes.yml", false);

        // 주석이 날아가므로 먼저 원본을 남겨 둡니다
        backup(f);

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = yml.getConfigurationSection("recipes");
        if (root == null) root = yml.createSection("recipes");

        // 이름을 바꿔 저장하면 옛 항목을 지웁니다
        String oldId = draft.originalId();
        if (oldId != null && !oldId.equals(draft.id())) {
            root.set(oldId, null);
        }

        String id = draft.id();
        ConfigurationSection sec = root.createSection(id);
        sec.set("category", draft.category());
        sec.set("skill-type", draft.skillType());
        sec.set("xp-reward", draft.xpReward());
        sec.set("crafting-time", draft.craftingTimeSeconds());
        sec.set("required-proficiency", draft.requiredProficiency());

        ItemStack result = draft.result();
        writeItem(sec.createSection("result"), result, result.getAmount());

        if (draft.shaped()) {
            writeShaped(sec, draft);
        } else {
            writeShapeless(sec, draft);
        }

        try {
            yml.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("recipes.yml 저장 실패: " + e.getMessage());
            return "파일을 저장하지 못했습니다: " + e.getMessage();
        }
        return null;
    }

    /** 모양 없는 레시피 — ingredients 목록으로 적습니다. */
    private void writeShapeless(ConfigurationSection sec, RecipeDraft draft) {
        sec.set("shape", null);
        sec.set("keys", null);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<ItemStack, Integer> e : merge(draft.gridView()).entrySet()) {
            Map<String, Object> one = new LinkedHashMap<>();
            String id = craftEngine.itemId(e.getKey());
            if (id != null) one.put("item", id);
            one.put("material", e.getKey().getType().name());
            one.put("amount", e.getValue());
            list.add(one);
        }
        sec.set("ingredients", list);
    }

    /**
     * 모양 있는 레시피 — shape 5줄과 keys 를 적습니다.
     *
     * <p>같은 재료에는 같은 글자를 줍니다. 글자는 A 부터 순서대로 붙이고,
     * 빈 칸은 공백입니다.
     */
    private void writeShaped(ConfigurationSection sec, RecipeDraft draft) {
        sec.set("ingredients", null);

        Map<String, Character> letter = new LinkedHashMap<>();
        Map<Character, ItemStack> sample = new LinkedHashMap<>();
        Map<Character, Integer> amount = new LinkedHashMap<>();
        char next = 'A';

        StringBuilder[] rows = new StringBuilder[RecipeDraft.GRID];
        for (int r = 0; r < RecipeDraft.GRID; r++) rows[r] = new StringBuilder();

        for (int r = 0; r < RecipeDraft.GRID; r++) {
            for (int c = 0; c < RecipeDraft.GRID; c++) {
                ItemStack s = draft.gridAt(r * RecipeDraft.GRID + c);
                if (s == null) {
                    rows[r].append(' ');
                    continue;
                }
                String key = keyOf(s);
                Character ch = letter.get(key);
                if (ch == null) {
                    ch = next++;
                    letter.put(key, ch);
                    sample.put(ch, s);
                    // 같은 글자가 여러 칸에 있으면 칸마다 이 개수를 요구합니다
                    amount.put(ch, s.getAmount());
                }
                rows[r].append(ch);
            }
        }

        List<String> shape = new ArrayList<>();
        for (StringBuilder sb : rows) shape.add(sb.toString());
        sec.set("shape", shape);

        ConfigurationSection keys = sec.createSection("keys");
        for (Map.Entry<Character, ItemStack> e : sample.entrySet()) {
            writeItem(keys.createSection(String.valueOf(e.getKey())),
                    e.getValue(), amount.get(e.getKey()));
        }
    }

    /** 레시피 하나를 지웁니다. */
    public @Nullable String delete(String id) {
        File f = file();
        if (!f.exists()) return "recipes.yml 이 없습니다.";
        backup(f);

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = yml.getConfigurationSection("recipes");
        if (root == null || !root.contains(id)) return "'" + id + "' 레시피가 없습니다.";
        root.set(id, null);
        try {
            yml.save(f);
        } catch (IOException e) {
            return "파일을 저장하지 못했습니다: " + e.getMessage();
        }
        return null;
    }

    /**
     * 저장 직전 원본을 복사해 둡니다.
     *
     * <p>YamlConfiguration 은 저장할 때 주석을 지웁니다. recipes.yml 은 손으로 적은
     * 설명이 많아서, 되살릴 수 있게 사본을 남깁니다. 사본은 한 개만 유지합니다 —
     * 저장할 때마다 쌓이면 폴더가 지저분해집니다.
     */
    private void backup(File f) {
        try {
            File bak = new File(f.getParentFile(), "recipes.yml.bak-editor");
            java.nio.file.Files.copy(f.toPath(), bak.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("recipes.yml 백업 실패: " + e.getMessage());
        }
    }
}
