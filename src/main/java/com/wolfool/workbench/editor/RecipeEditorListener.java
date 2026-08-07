package com.wolfool.workbench.editor;

import com.wolfool.workbench.integration.CraftEngineBridge;
import com.wolfool.workbench.recipe.CustomRecipe;
import com.wolfool.workbench.recipe.RecipeItem;
import com.wolfool.workbench.recipe.RecipeManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 레시피 편집 GUI 의 조작을 받습니다.
 *
 * <p>격자와 결과 칸은 그냥 두고(아이템을 넣고 뺄 수 있게), 나머지 칸만 막습니다.
 * 창을 닫으면 넣어 둔 아이템을 전부 돌려드립니다.
 */
public final class RecipeEditorListener implements Listener {

    /** 이름을 채팅으로 받는 중인 사람. 값은 그때까지의 편집 내용입니다. */
    private final Map<UUID, RecipeDraft> awaitingName = new ConcurrentHashMap<>();
    /** 저장을 눌러 닫는 중인 사람. 이때는 아이템을 돌려주지 않습니다. */
    private final Set<UUID> savingNow = ConcurrentHashMap.newKeySet();

    private final Plugin plugin;
    private final RecipeManager recipes;
    private final CraftEngineBridge craftEngine;
    private final RecipeWriter writer;

    public RecipeEditorListener(Plugin plugin, RecipeManager recipes,
                                CraftEngineBridge craftEngine) {
        this.plugin = plugin;
        this.recipes = recipes;
        this.craftEngine = craftEngine;
        this.writer = new RecipeWriter(plugin, craftEngine);
    }

    // ------------------------------------------------------------ 열기

    /** 새 레시피를 만듭니다. */
    public void openNew(Player player) {
        List<String> cats = knownCategories();
        RecipeDraft draft = RecipeDraft.blank(cats.isEmpty() ? "기본" : cats.get(0));
        player.openInventory(new RecipeEditorGUI(draft, cats, knownSkills()).getInventory());
    }

    /** 이미 있는 레시피를 불러옵니다. */
    public boolean openExisting(Player player, String id) {
        CustomRecipe recipe = recipes.getRecipes().get(id);
        if (recipe == null) return false;

        ItemStack[] grid = new ItemStack[RecipeDraft.GRID * RecipeDraft.GRID];
        if (recipe.isShaped()) {
            List<String> shape = recipe.getShape();
            for (int r = 0; r < RecipeDraft.GRID && r < shape.size(); r++) {
                String row = shape.get(r);
                for (int c = 0; c < RecipeDraft.GRID && c < row.length(); c++) {
                    char ch = row.charAt(c);
                    RecipeItem ing = recipe.getKeys().get(ch);
                    if (ing == null) continue;
                    ItemStack shown = ing.display(craftEngine, player);
                    shown.setAmount(Math.max(1, ing.amount()));
                    grid[r * RecipeDraft.GRID + c] = shown;
                }
            }
        } else {
            // 모양이 없으면 왼쪽 위부터 차례로 채웁니다
            int i = 0;
            for (RecipeItem ing : recipe.getIngredients()) {
                if (i >= grid.length) break;
                ItemStack shown = ing.display(craftEngine, player);
                shown.setAmount(Math.max(1, ing.amount()));
                grid[i++] = shown;
            }
        }

        ItemStack result = recipe.getResult().display(craftEngine, player);
        result.setAmount(Math.max(1, recipe.getResult().amount()));

        RecipeDraft draft = RecipeDraft.of(recipe, grid, result);
        List<String> cats = knownCategories();
        if (!cats.contains(draft.category())) cats.add(draft.category());
        player.openInventory(new RecipeEditorGUI(draft, cats, knownSkills()).getInventory());
        return true;
    }

    /**
     * 고를 수 있는 분류.
     *
     * <p>config.yml 의 도감 탭에 적힌 것을 먼저 넣고, 레시피가 실제로 쓰는 것을
     * 덧붙입니다. 탭에 없는 분류를 고르면 '나머지' 탭으로 밀립니다.
     */
    private List<String> knownCategories() {
        Set<String> out = new LinkedHashSet<>();
        var tabs = plugin.getConfig().getMapList("gui.encyclopedia.tabs");
        for (Map<?, ?> tab : tabs) {
            Object cats = tab.get("categories");
            if (cats instanceof List<?> list) {
                for (Object c : list) out.add(String.valueOf(c));
            }
        }
        for (CustomRecipe r : recipes.getRecipes().values()) out.add(r.getCategory());
        return new ArrayList<>(out);
    }

    /**
     * 고를 수 있는 스킬 — 이 서버에서는 <b>Mastery 의 직업</b>입니다.
     *
     * <p><b>Mastery 에 실제로 있는 이름만 적습니다.</b> 없는 이름을 쓰면 그 레시피의
     * 요구 숙련도가 영영 안 채워집니다 — 조용히 잠긴 채로 있어서 눈치채기 어렵습니다.
     *
     * <p><b>한 사람은 직업을 하나만 가집니다.</b> 요구 숙련도를 0 보다 크게 걸면
     * 그 직업인 사람만 만들 수 있게 됩니다. AuraSkills 때와 다른 점입니다 —
     * 그때는 모두가 모든 스킬을 같이 올렸습니다.
     */
    private List<String> knownSkills() {
        Set<String> out = new LinkedHashSet<>(List.of(
                "farmer", "fisher", "miner", "cook"));
        for (CustomRecipe r : recipes.getRecipes().values()) out.add(r.getSkillType());
        return new ArrayList<>(out);
    }

    // ------------------------------------------------------------ 클릭

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeEditorGUI gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean top = event.getClickedInventory() == event.getInventory();

        // 내 가방 쪽 클릭. 쉬프트로 밀어 넣는 것만 막습니다 — 어느 칸에 들어갈지
        // 알 수 없어서 버튼 자리에 아이템이 박히기 때문입니다
        if (!top) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                player.sendMessage("§7[레시피] §f격자에 직접 놓아 주세요. §8(쉬프트로는 안 들어갑니다)");
            }
            return;
        }

        // 아이템을 놓을 수 있는 칸이면 그대로 둡니다
        if (RecipeEditorGUI.isEditableSlot(event.getSlot())) {
            // 다음 틱에 도움말·저장 버튼을 다시 그립니다 (놓인 뒤의 상태를 봐야 합니다)
            Bukkit.getScheduler().runTask(plugin, () -> {
                gui.pullFromInventory();
                gui.refreshControls();
            });
            return;
        }

        event.setCancelled(true);
        if (event.getAction() == InventoryAction.NOTHING) return;

        gui.pullFromInventory();
        handleButton(player, gui, event);
    }

    private void handleButton(Player player, RecipeEditorGUI gui, InventoryClickEvent event) {
        RecipeDraft draft = gui.draft();
        boolean right = event.isRightClick();
        boolean shift = event.isShiftClick();
        int slot = event.getSlot();

        switch (slot) {
            case RecipeEditorGUI.SLOT_MODE -> {
                draft.shaped(!draft.shaped());
                click(player);
            }
            case RecipeEditorGUI.SLOT_CATEGORY ->
                    draft.category(cycle(gui.categories(), draft.category(), right));
            case RecipeEditorGUI.SLOT_SKILL ->
                    draft.skillType(cycle(gui.skills(), draft.skillType(), right));
            case RecipeEditorGUI.SLOT_TIME -> {
                long step = shift ? 30 : 5;
                draft.craftingTimeSeconds(draft.craftingTimeSeconds() + (right ? -step : step));
            }
            case RecipeEditorGUI.SLOT_XP -> {
                double step = shift ? 25 : 5;
                draft.xpReward(draft.xpReward() + (right ? -step : step));
            }
            case RecipeEditorGUI.SLOT_PROFICIENCY ->
                    draft.requiredProficiency(draft.requiredProficiency() + (right ? -1 : 1));
            case RecipeEditorGUI.SLOT_ID -> {
                askName(player, draft);
                return;
            }
            case RecipeEditorGUI.SLOT_SAVE -> {
                doSave(player, gui);
                return;
            }
            case RecipeEditorGUI.SLOT_CANCEL -> {
                player.closeInventory();
                return;
            }
            case RecipeEditorGUI.SLOT_DELETE -> {
                if (draft.isNew()) return;
                if (!shift) {
                    player.sendMessage("§c[레시피] 지우시려면 쉬프트를 누른 채로 클릭해 주세요.");
                    return;
                }
                doDelete(player, gui);
                return;
            }
            default -> {
                return;
            }
        }
        gui.refreshControls();
        click(player);
    }

    private static String cycle(List<String> list, String now, boolean backwards) {
        if (list.isEmpty()) return now;
        int i = list.indexOf(now);
        if (i < 0) return list.get(0);
        int next = (i + (backwards ? -1 : 1) + list.size()) % list.size();
        return list.get(next);
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    // ------------------------------------------------------------ 저장·삭제

    private void doSave(Player player, RecipeEditorGUI gui) {
        RecipeDraft draft = gui.draft();
        String error = writer.save(draft);
        if (error != null) {
            player.sendMessage("§c[레시피] " + error);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            return;
        }

        // 저장했으면 아이템은 돌려주고 창을 닫습니다.
        // 격자에 놓은 것은 레시피를 적는 데만 쓰였고 소모되지 않습니다.
        savingNow.add(player.getUniqueId());
        returnItems(player, gui);
        player.closeInventory();
        savingNow.remove(player.getUniqueId());

        recipes.loadRecipes();
        recipes.validateCustomItems(craftEngine);
        player.sendMessage("§a[레시피] §f'" + draft.id() + "' §a을(를) 저장했습니다. "
                + "§7(" + draft.category() + " · " + (draft.shaped() ? "모양 있음" : "모양 없음") + ")");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
    }

    private void doDelete(Player player, RecipeEditorGUI gui) {
        String id = gui.draft().originalId();
        String error = writer.delete(id);
        if (error != null) {
            player.sendMessage("§c[레시피] " + error);
            return;
        }
        savingNow.add(player.getUniqueId());
        returnItems(player, gui);
        player.closeInventory();
        savingNow.remove(player.getUniqueId());

        recipes.loadRecipes();
        player.sendMessage("§c[레시피] §f'" + id + "' §c을(를) 지웠습니다.");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.8f);
    }

    // ------------------------------------------------------------ 이름 입력

    private void askName(Player player, RecipeDraft draft) {
        awaitingName.put(player.getUniqueId(), draft);
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage("§e[레시피] §f채팅으로 이름을 입력해 주세요.");
        player.sendMessage("§8영소문자 · 숫자 · 밑줄만 쓸 수 있습니다. 예: §7iron_sword");
        player.sendMessage("§8취소하시려면 §7취소§8 라고 쳐 주세요.");
    }

    // Paper 의 최신 채팅 이벤트입니다. 예전 AsyncPlayerChatEvent 는 사용 중단됐습니다
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        Player player = event.getPlayer();
        RecipeDraft draft = awaitingName.remove(player.getUniqueId());
        if (draft == null) return;

        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        if (raw.equalsIgnoreCase("취소") || raw.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7[레시피] 이름 입력을 그만두었습니다.");
            reopen(player, draft);
            return;
        }

        String id = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!id.matches("[a-z0-9_]+")) {
            player.sendMessage("§c[레시피] '" + raw + "' 는 쓸 수 없습니다. "
                    + "영소문자 · 숫자 · 밑줄만 됩니다.");
            awaitingName.put(player.getUniqueId(), draft);
            return;
        }
        if (recipes.getRecipes().containsKey(id) && !id.equals(draft.originalId())) {
            player.sendMessage("§c[레시피] '" + id + "' 는 이미 있습니다. 다른 이름을 써 주세요.");
            awaitingName.put(player.getUniqueId(), draft);
            return;
        }

        draft.id(id);
        player.sendMessage("§a[레시피] 이름을 §f" + id + " §a로 정했습니다.");
        reopen(player, draft);
    }

    /** 채팅 이벤트는 다른 스레드에서 옵니다. 창은 메인 스레드에서 열어야 합니다. */
    private void reopen(Player player, RecipeDraft draft) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<String> cats = knownCategories();
            if (!cats.contains(draft.category())) cats.add(draft.category());
            player.openInventory(
                    new RecipeEditorGUI(draft, cats, knownSkills()).getInventory());
        });
    }

    // ------------------------------------------------------------ 닫기

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeEditorGUI gui)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (savingNow.contains(player.getUniqueId())) return;
        // 이름을 채팅으로 받으려고 닫은 것이면 아이템을 그대로 둡니다 (곧 다시 엽니다)
        if (awaitingName.containsKey(player.getUniqueId())) return;
        returnItems(player, gui);
    }

    /** 격자와 결과 칸에 놓인 것을 돌려줍니다. 가방이 꽉 차면 발밑에 떨굽니다. */
    private void returnItems(Player player, RecipeEditorGUI gui) {
        List<ItemStack> back = new ArrayList<>();
        for (int slot : RecipeEditorGUI.GRID_SLOTS) {
            ItemStack it = gui.getInventory().getItem(slot);
            if (it != null && !it.getType().isAir()) back.add(it);
            gui.getInventory().setItem(slot, null);
        }
        ItemStack result = gui.getInventory().getItem(RecipeEditorGUI.SLOT_RESULT);
        if (result != null && !result.getType().isAir()) back.add(result);
        gui.getInventory().setItem(RecipeEditorGUI.SLOT_RESULT, null);

        for (ItemStack it : back) {
            for (ItemStack leftover : player.getInventory().addItem(it).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeEditorGUI)) return;
        // 버튼 자리에 걸치는 드래그만 막습니다
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize()
                    && !RecipeEditorGUI.isEditableSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingName.remove(event.getPlayer().getUniqueId());
        savingNow.remove(event.getPlayer().getUniqueId());
    }
}
