package com.wolfool.workbench;

import com.wolfool.workbench.crafting.CraftingManager;
import com.wolfool.workbench.database.DatabaseManager;
import com.wolfool.workbench.gui.WorkbenchGUI;
import com.wolfool.workbench.recipe.RecipeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.List;

public class WorkbenchCommand implements CommandExecutor, TabCompleter {
    private final org.bukkit.plugin.Plugin plugin;
    private final CraftingManager craftingManager;
    private final RecipeManager recipeManager;
    private final DatabaseManager databaseManager;
    private final com.wolfool.workbench.integration.SkillBridge skills;
    private final com.wolfool.workbench.integration.CraftEngineBridge craftEngine;
    private final com.wolfool.workbench.editor.RecipeEditorListener recipeEditor;

    public WorkbenchCommand(org.bukkit.plugin.Plugin plugin, CraftingManager craftingManager, RecipeManager recipeManager, DatabaseManager databaseManager, com.wolfool.workbench.integration.SkillBridge skills, com.wolfool.workbench.integration.CraftEngineBridge craftEngine, com.wolfool.workbench.editor.RecipeEditorListener recipeEditor) {
        this.plugin = plugin;
        this.craftingManager = craftingManager;
        this.recipeManager = recipeManager;
        this.databaseManager = databaseManager;
        this.skills = skills;
        this.craftEngine = craftEngine;
        this.recipeEditor = recipeEditor;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : new String[]{"gui", "item", "key", "xp", "lock", "admin", "edit", "reload"}) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            // 고칠 레시피 이름. 인자를 안 주면 새로 만드는 화면이 열립니다
            for (String id : recipeManager.getRecipes().keySet()) {
                if (id.startsWith(args[1].toLowerCase())) completions.add(id);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("lock")) {
            for (int i = 0; i <= plugin.getConfig().getInt("crafting.max-queue-slots", 9); i++) {
                String option = String.valueOf(i);
                if (option.startsWith(args[1])) completions.add(option);
            }
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("§e/wb gui §7- 작업대 UI를 테스트로 엽니다.");
            player.sendMessage("§e/wb item §7- 제작대를 받습니다.");
            player.sendMessage("§e/wb key §7- 대기열 열쇠를 받습니다.");
            player.sendMessage("§e/wb xp §7- 숙련도와 제작 횟수를 봅니다.");
            player.sendMessage("§e/wb lock [칸수] §7- 대기열을 다시 잠급니다. (기본값으로 되돌림)");
            player.sendMessage("§e/wb edit [이름] §7- 레시피를 만들거나 고칩니다. (GUI)");
            player.sendMessage("§e/wb reload §7- 설정 및 레시피를 다시 불러옵니다.");
            return true;
        }

        if (args[0].equalsIgnoreCase("edit")) {
            if (!player.hasPermission("workbench.admin")) {
                player.sendMessage("§c권한이 없습니다.");
                return true;
            }
            if (args.length >= 2) {
                if (!recipeEditor.openExisting(player, args[1].toLowerCase())) {
                    player.sendMessage("§c'" + args[1] + "' 레시피가 없습니다. "
                            + "§7새로 만드시려면 §f/wb edit §7만 치세요.");
                }
            } else {
                recipeEditor.openNew(player);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("xp")) {
            showSkillStatus(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("lock")) {
            lockQueue(player, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            int unlockedSlots = craftingManager.getUnlockedSlots(player.getUniqueId());
            WorkbenchGUI gui = new WorkbenchGUI(plugin, player, unlockedSlots, recipeManager, skills, null, null, craftEngine);
            player.openInventory(gui.getInventory());
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("wolfoolcraft.admin")) {
                player.sendMessage("§c권한이 없습니다.");
                return true;
            }
            com.wolfool.workbench.gui.AdminEncyclopediaGUI gui = new com.wolfool.workbench.gui.AdminEncyclopediaGUI(player, recipeManager, null, craftEngine);
            player.openInventory(gui.getInventory());
            return true;
        }
        
        if (args[0].equalsIgnoreCase("item") || args[0].equalsIgnoreCase("key")) {
            boolean wantKey = args[0].equalsIgnoreCase("key");
            String id = wantKey
                    ? plugin.getConfig().getString("workbench.queue-key-id", "wolfoolcraft:queue_key")
                    : plugin.getConfig().getString("workbench.item-id", "wolfoolcraft:workbench");

            ItemStack item = craftEngine.createItem(id, player);
            if (item == null) {
                player.sendMessage("§c[작업대] CraftEngine 에 '" + id + "' 아이템이 없습니다.");
                player.sendMessage("§7리소스팩 설정을 확인하고 /ce reload 를 해보세요.");
                return true;
            }
            for (ItemStack rest : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            player.sendMessage(wantKey
                    ? "§a[작업대] 대기열 열쇠를 지급했습니다."
                    : "§a[작업대] 제작대를 지급했습니다. 들고 §fF§a 키를 눌러 설치하세요.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("wolfoolcraft.admin")) {
                player.sendMessage("§c권한이 없습니다.");
                return true;
            }
            plugin.reloadConfig();
            recipeManager.loadRecipes();
            recipeManager.validateCustomItems(craftEngine);
            player.sendMessage("§a[작업대] config.yml 및 recipes.yml을 다시 불러왔습니다!");
            return true;
        }

        return true;
    }

    /**
     * 대기열 칸을 다시 잠근다.
     *
     * <p>열쇠로 여는 걸 확인하려면 되돌릴 방법이 있어야 한다. 칸 수를 안 적으면
     * config 의 기본값으로 돌아간다.
     */
    private void lockQueue(Player player, String[] args) {
        int max = plugin.getConfig().getInt("crafting.max-queue-slots", 9);
        int target = plugin.getConfig().getInt("crafting.default-queue-slots", 3);

        if (args.length >= 2) {
            try {
                target = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c[작업대] '" + args[1] + "' 는 숫자가 아닙니다. §7/wb lock [0~" + max + "]");
                return;
            }
            if (target < 0 || target > max) {
                player.sendMessage("§c[작업대] 0 에서 " + max + " 사이여야 합니다.");
                return;
            }
        }

        int before = craftingManager.getUnlockedSlots(player.getUniqueId());
        craftingManager.setUnlockedSlots(player.getUniqueId(), target);

        // 잠근 칸에 제작 중인 게 남아 있으면 수령을 못 하게 된다. 미리 알려준다.
        int queued = craftingManager.getPlayerQueue(player.getUniqueId()).size();
        player.sendMessage("§a[작업대] 대기열을 " + before + "칸 → §f" + target + "칸§a 으로 바꿨습니다.");
        if (queued > target) {
            player.sendMessage("§e대기열에 " + queued + "개가 있어 " + (queued - target)
                    + "개는 잠긴 칸에 가려집니다. §7칸을 다시 늘리면 그대로 보입니다.");
        }
        player.sendMessage("§7열쇠로 여는 걸 확인하려면 §f/wb key §7로 열쇠를 받으세요.");
    }

    /**
     * 숙련도와 제작 횟수.
     *
     * <p>레시피가 왜 잠겨 있는지 볼 방법이 없었다. 숙련도를 물어볼 곳이 아예 없는
     * 상태인 것도 여기서 분명히 알려준다.
     *
     * <p><b>제작으로는 숙련도가 안 오른다.</b> Mastery 는 직업 활동으로만 오른다 —
     * 레시피의 {@code xp-reward} 는 지금 아무 데도 안 쌓인다.
     */
    private void showSkillStatus(Player player) {
        player.sendMessage("§6=== 제작 숙련도 ===");

        if (!skills.isAvailable()) {
            player.sendMessage("§cMastery 가 없습니다. §7요구 숙련도가 있는 레시피는");
            player.sendMessage("§7계속 잠긴 채로 있습니다.");
        }

        // 레시피에 실제로 쓰인 스킬만 모은다. 순서는 recipes.yml 순서를 따른다.
        java.util.Map<String, java.util.List<com.wolfool.workbench.recipe.CustomRecipe>> bySkill =
                new java.util.LinkedHashMap<>();
        int totalCrafts = 0;
        for (com.wolfool.workbench.recipe.CustomRecipe recipe : recipeManager.getRecipes().values()) {
            String skill = recipe.getSkillType() == null ? "(없음)" : recipe.getSkillType();
            bySkill.computeIfAbsent(skill, k -> new ArrayList<>()).add(recipe);
            totalCrafts += craftingManager.getCraftCount(player.getUniqueId(), recipe.getId());
        }

        for (var entry : bySkill.entrySet()) {
            String skill = entry.getKey();
            int level = skills.levelOf(player, skill);

            int locked = 0;
            int crafted = 0;
            for (com.wolfool.workbench.recipe.CustomRecipe recipe : entry.getValue()) {
                if (!skills.meets(player, skill, recipe.getRequiredProficiency())) locked++;
                crafted += craftingManager.getCraftCount(player.getUniqueId(), recipe.getId());
            }

            StringBuilder line = new StringBuilder("§f" + skills.displayName(skill) + " §7- ");
            if (skills.isAvailable() && !skills.knowsSkill(skill)) {
                // 레시피에 적은 이름이 Mastery 에 없다. 오타면 요구 숙련도가 영영 안 채워진다.
                line.append("§c그런 직업이 없습니다 §8(farmer·fisher·miner·cook)");
            } else {
                line.append("§b레벨 ").append(level);
            }
            line.append(" §7| 레시피 ").append(entry.getValue().size()).append("개");
            if (locked > 0) line.append(" §c(잠김 ").append(locked).append(")");
            line.append(" §7| 제작 ").append(crafted).append("회");
            player.sendMessage(line.toString());
        }

        player.sendMessage("§7총 제작 횟수: §f" + totalCrafts + "회");
        player.sendMessage("§8제작 횟수는 완성이 아니라 '제작을 시작한' 시점에 셉니다.");
        player.sendMessage("§8숙련도는 제작이 아니라 직업 활동으로 오릅니다. §7/숙련");
    }
}
