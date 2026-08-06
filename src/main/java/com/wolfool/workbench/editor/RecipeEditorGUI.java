package com.wolfool.workbench.editor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 레시피 편집 GUI.
 *
 * <p>가운데 5×5 격자에 재료를 놓고, 오른쪽에서 이름·카테고리 같은 값을 정한 뒤
 * 저장을 누르면 recipes.yml 에 들어갑니다.
 *
 * <pre>
 *   0  1  2  3  4  |  모드  결과  스킬
 *   9 10 11 12 13  |  분류   .    .
 *  18 19 20 21 22  |  시간   XP  숙련
 *  27 28 29 30 31  |  이름   .    .
 *  36 37 38 39 40  |   .     .    .
 *  안내 . . . .    |  저장  취소  삭제
 * </pre>
 *
 * <p><b>격자와 결과 칸에는 아이템을 실제로 넣습니다.</b> 창을 닫으면 전부
 * 돌려드립니다 — 레시피를 적는 데 쓰일 뿐 소모되지 않습니다.
 */
public final class RecipeEditorGUI implements InventoryHolder {

    /** 5×5 격자가 놓이는 칸. 왼쪽 다섯 열입니다. */
    public static final int[] GRID_SLOTS = new int[RecipeDraft.GRID * RecipeDraft.GRID];

    static {
        int i = 0;
        for (int r = 0; r < RecipeDraft.GRID; r++) {
            for (int c = 0; c < RecipeDraft.GRID; c++) {
                GRID_SLOTS[i++] = r * 9 + c;
            }
        }
    }

    public static final int SLOT_MODE = 6;
    public static final int SLOT_RESULT = 7;
    public static final int SLOT_SKILL = 8;
    public static final int SLOT_CATEGORY = 15;
    public static final int SLOT_TIME = 24;
    public static final int SLOT_XP = 25;
    public static final int SLOT_PROFICIENCY = 26;
    public static final int SLOT_ID = 33;
    public static final int SLOT_HELP = 45;
    public static final int SLOT_SAVE = 51;
    public static final int SLOT_CANCEL = 52;
    public static final int SLOT_DELETE = 53;

    /** 칸을 나누는 장식. 클릭이 막힙니다. */
    private static final int[] SEPARATORS = {5, 14, 23, 32, 41, 50};

    private final Inventory inventory;
    private final RecipeDraft draft;
    private final List<String> categories;
    private final List<String> skills;

    public RecipeEditorGUI(RecipeDraft draft, List<String> categories, List<String> skills) {
        this.draft = draft;
        this.categories = categories;
        this.skills = skills;
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("[레시피 편집] ").color(NamedTextColor.DARK_AQUA)
                        .append(Component.text(draft.isNew() ? "새로 만들기" : draft.originalId())
                                .color(NamedTextColor.WHITE)));
        render();
    }

    public RecipeDraft draft() {
        return draft;
    }

    public List<String> categories() {
        return categories;
    }

    public List<String> skills() {
        return skills;
    }

    /** 격자 칸인지. 아이템을 넣고 뺄 수 있는 칸입니다. */
    public static boolean isGridSlot(int slot) {
        for (int s : GRID_SLOTS) if (s == slot) return true;
        return false;
    }

    /** 그 칸이 격자에서 몇 번째인지. 격자가 아니면 -1. */
    public static int gridIndex(int slot) {
        for (int i = 0; i < GRID_SLOTS.length; i++) if (GRID_SLOTS[i] == slot) return i;
        return -1;
    }

    /** 아이템을 넣을 수 있는 칸인지 (격자 + 결과). */
    public static boolean isEditableSlot(int slot) {
        return isGridSlot(slot) || slot == SLOT_RESULT;
    }

    /** 지금 화면에 놓인 것을 draft 로 옮깁니다. 저장·닫기 전에 부릅니다. */
    public void pullFromInventory() {
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            draft.gridAt(i, inventory.getItem(GRID_SLOTS[i]));
        }
        draft.result(inventory.getItem(SLOT_RESULT));
    }

    /** draft 를 화면에 다시 그립니다. 격자와 결과 칸은 건드리지 않습니다. */
    public void refreshControls() {
        drawControls();
    }

    private void render() {
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            inventory.setItem(GRID_SLOTS[i], draft.gridAt(i));
        }
        inventory.setItem(SLOT_RESULT, draft.result());
        drawControls();
    }

    private void drawControls() {
        ItemStack pane = simple(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int s : SEPARATORS) inventory.setItem(s, pane);
        for (int s : new int[]{16, 17, 34, 35, 42, 43, 44, 46, 47, 48, 49}) {
            inventory.setItem(s, pane);
        }

        // ---- 모드
        inventory.setItem(SLOT_MODE, simple(
                draft.shaped() ? Material.CRAFTING_TABLE : Material.BUNDLE,
                (draft.shaped() ? "§b모양 있는 레시피" : "§a모양 없는 레시피"),
                draft.shaped()
                        ? List.of("§7놓은 자리까지 맞아야 만들어집니다.",
                                  "§8격자 모양 그대로 shape 로 저장됩니다.",
                                  "", "§e클릭 §7모양 없는 쪽으로 바꾸기")
                        : List.of("§7재료만 맞으면 자리는 상관없습니다.",
                                  "§8격자에 놓은 것을 종류별로 합쳐 저장합니다.",
                                  "", "§e클릭 §7모양 있는 쪽으로 바꾸기")));

        // ---- 스킬
        inventory.setItem(SLOT_SKILL, simple(Material.EXPERIENCE_BOTTLE,
                "§d스킬 §f" + draft.skillType(),
                List.of("§7이 레시피로 오르는 숙련도 종류입니다.",
                        "§8" + String.join(" · ", skills),
                        "", "§e좌클릭 §7다음 §8/ §e우클릭 §7이전")));

        // ---- 분류
        inventory.setItem(SLOT_CATEGORY, simple(Material.BOOKSHELF,
                "§6분류 §f" + draft.category(),
                catLore()));

        // ---- 시간
        inventory.setItem(SLOT_TIME, simple(Material.CLOCK,
                "§b제작 시간 §f" + draft.craftingTimeSeconds() + "초",
                List.of("§7제작을 걸어 두고 기다리는 시간입니다.",
                        "", "§e좌클릭 §7+5초 §8/ §e우클릭 §7-5초",
                        "§e쉬프트 §7±30초")));

        // ---- XP
        inventory.setItem(SLOT_XP, simple(Material.EXPERIENCE_BOTTLE,
                "§aXP 보상 §f" + (long) draft.xpReward(),
                List.of("§7만들었을 때 주는 숙련도 경험치입니다.",
                        "", "§e좌클릭 §7+5 §8/ §e우클릭 §7-5",
                        "§e쉬프트 §7±25")));

        // ---- 숙련도
        List<String> profLore = new ArrayList<>();
        profLore.add("§7이 값 이상이어야 만들 수 있습니다.");
        if (draft.requiredProficiency() > 0) {
            profLore.add("§c AuraSkills 가 없으면 영영 잠깁니다.");
        }
        profLore.add("");
        profLore.add("§e좌클릭 §7+1 §8/ §e우클릭 §7-1");
        inventory.setItem(SLOT_PROFICIENCY, simple(Material.IRON_BARS,
                "§c요구 숙련도 §f" + draft.requiredProficiency(), profLore));

        // ---- 이름
        inventory.setItem(SLOT_ID, simple(Material.NAME_TAG,
                "§f이름 §7" + (draft.id() == null ? "§8(정해 주세요)" : "§e" + draft.id()),
                List.of("§7recipes.yml 에 들어갈 키입니다.",
                        "§8영소문자 · 숫자 · 밑줄만 쓸 수 있습니다.",
                        "", "§e클릭 §7채팅으로 입력")));

        // ---- 안내
        List<String> help = new ArrayList<>();
        help.add("§7왼쪽 격자에 §f재료§7를, 위쪽 칸에 §f결과물§7을 놓으세요.");
        help.add("§8넣은 아이템은 창을 닫을 때 전부 돌려드립니다.");
        help.add("");
        help.add("§7지금 상태 §f" + draft.summary());
        List<String> problems = currentProblems();
        if (!problems.isEmpty()) {
            help.add("");
            help.add("§c저장하려면");
            for (String p : problems) help.add("§c · " + p);
        }
        inventory.setItem(SLOT_HELP, simple(Material.BOOK, "§e도움말", help));

        // ---- 저장 / 취소 / 삭제
        boolean ok = problems.isEmpty();
        inventory.setItem(SLOT_SAVE, simple(
                ok ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                ok ? "§a§l저장" : "§8저장 (아직 안 됩니다)",
                ok ? List.of("§7recipes.yml 에 쓰고 다시 읽습니다.")
                   : List.of("§7위 도움말의 빨간 줄을 먼저 해결해 주세요.")));

        inventory.setItem(SLOT_CANCEL, simple(Material.RED_CONCRETE, "§c§l취소",
                List.of("§7저장하지 않고 닫습니다.",
                        "§8놓은 아이템은 돌려드립니다.")));

        if (draft.isNew()) {
            inventory.setItem(SLOT_DELETE, pane);
        } else {
            inventory.setItem(SLOT_DELETE, simple(Material.BARRIER, "§4§l삭제",
                    List.of("§7'" + draft.originalId() + "' 를 recipes.yml 에서 지웁니다.",
                            "", "§c쉬프트 + 클릭 §7해야 지워집니다.")));
        }
    }

    private List<String> catLore() {
        List<String> lore = new ArrayList<>();
        lore.add("§7도감에서 어느 탭에 뜰지 정합니다.");
        for (String c : categories) {
            lore.add((c.equals(draft.category()) ? "§a ▶ " : "§8   ") + c);
        }
        lore.add("");
        lore.add("§e좌클릭 §7다음 §8/ §e우클릭 §7이전");
        return lore;
    }

    /** 화면에 놓인 것까지 반영한 검사 결과. */
    private List<String> currentProblems() {
        List<String> out = new ArrayList<>();
        if (draft.id() == null) {
            out.add("이름을 정해 주세요.");
        } else if (!draft.id().matches("[a-z0-9_]+")) {
            out.add("이름은 영소문자·숫자·밑줄만 됩니다.");
        }
        if (inventory.getItem(SLOT_RESULT) == null) out.add("결과물을 놓아 주세요.");
        boolean any = false;
        for (int s : GRID_SLOTS) {
            ItemStack it = inventory.getItem(s);
            if (it != null && !it.getType().isAir()) {
                any = true;
                break;
            }
        }
        if (!any) out.add("재료를 하나 이상 놓아 주세요.");
        return out;
    }

    private static ItemStack simple(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(legacy(name));
            List<Component> lines = new ArrayList<>(lore.size());
            for (String l : lore) lines.add(legacy(l));
            meta.lore(lines);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** §코드가 섞인 문자열을 기울임 없이 그립니다. */
    private static Component legacy(String s) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(s)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
