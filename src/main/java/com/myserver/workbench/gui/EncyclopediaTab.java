package com.myserver.workbench.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 도감의 탭 하나.
 *
 * <p>배경 그림이 탭마다 통째로 다르다. 원본이 {@code tab1_selected.png} 처럼
 * '그 탭이 눌린 상태' 그림으로 와 있어서, 선택된 탭에 맞는 글리프를 깔면
 * 눌린 탭이 저절로 표시된다. 그래서 탭 자리에는 색깔 배너 대신 투명한 아이템만 놓는다.
 *
 * <p>정의는 {@code config.yml} 의 {@code gui.encyclopedia.tabs} 에 있다.
 * 목록 순서가 그림의 탭 순서(위 → 아래)이자 {@link EncyclopediaGUI#TAB_SLOTS} 순서다.
 */
public final class EncyclopediaTab {

    /** 이 탭이 어떤 레시피를 담을지 정하는 방식. */
    public enum Mode {
        /** 카테고리와 무관하게 전부. */
        ALL,
        /** {@link #categories} 에 적힌 것만. */
        LIST,
        /** 다른 탭이 안 가져간 나머지 전부. */
        REST
    }

    private final String name;
    private final String glyph;
    private final Mode mode;
    private final List<String> categories;

    private EncyclopediaTab(String name, String glyph, Mode mode, List<String> categories) {
        this.name = name;
        this.glyph = glyph;
        this.mode = mode;
        this.categories = categories;
    }

    public String name() {
        return name;
    }

    /** 이 탭이 선택됐을 때 깔 배경 글리프. 비어 있으면 배경 없이 연다. */
    public String glyph() {
        return glyph;
    }

    public Mode mode() {
        return mode;
    }

    public List<String> categories() {
        return categories;
    }

    /**
     * 이 레시피 카테고리를 이 탭에 보여줄지.
     *
     * @param claimed 다른 탭들이 이미 가져간 카테고리. {@link Mode#REST} 판단에만 쓴다
     */
    public boolean accepts(String category, Set<String> claimed) {
        return switch (mode) {
            case ALL -> true;
            case LIST -> categories.contains(category);
            case REST -> !claimed.contains(category);
        };
    }

    /**
     * config 에서 탭 목록을 읽는다.
     *
     * <p>설정이 없거나 비어 있으면 빈 목록을 준다. 그 경우 도감은 탭 없이
     * 모든 레시피를 한 장에 보여준다 — 설정이 잘못돼도 도감 자체는 열려야 한다.
     */
    public static List<EncyclopediaTab> load(Plugin plugin) {
        List<EncyclopediaTab> tabs = new ArrayList<>();
        List<?> raw = plugin.getConfig().getList("gui.encyclopedia.tabs");
        if (raw == null) return tabs;

        for (Object entry : raw) {
            ConfigurationSection sec = toSection(entry);
            if (sec == null) continue;

            String name = sec.getString("name", "");
            String glyph = sec.getString("char", "");
            List<String> cats = sec.getStringList("categories");
            Mode mode = parseMode(plugin, sec.getString("mode"), cats);
            tabs.add(new EncyclopediaTab(name, glyph, mode, cats));
        }
        return tabs;
    }

    /**
     * YAML 목록의 한 항목을 섹션으로 본다.
     *
     * <p>Bukkit 은 목록 안의 맵을 {@code Map} 으로 줄 때도 있고
     * {@code ConfigurationSection} 으로 줄 때도 있어서 양쪽을 다 받는다.
     */
    private static ConfigurationSection toSection(Object entry) {
        if (entry instanceof ConfigurationSection sec) return sec;
        if (entry instanceof java.util.Map<?, ?> map) {
            org.bukkit.configuration.MemoryConfiguration wrapper =
                    new org.bukkit.configuration.MemoryConfiguration();
            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                wrapper.set(String.valueOf(e.getKey()), e.getValue());
            }
            return wrapper;
        }
        return null;
    }

    /** {@code mode} 를 안 적었으면 categories 유무로 짐작한다. */
    private static Mode parseMode(Plugin plugin, String raw, List<String> categories) {
        if (raw == null || raw.isBlank()) {
            return categories.isEmpty() ? Mode.ALL : Mode.LIST;
        }
        try {
            return Mode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("도감 탭의 mode 값 '" + raw
                    + "' 를 모르겠다. all / list / rest 중 하나여야 한다. list 로 둔다.");
            return Mode.LIST;
        }
    }
}
