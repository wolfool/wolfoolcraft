package com.myserver.workbench;

import com.myserver.workbench.crafting.CraftingManager;
import com.myserver.workbench.database.DatabaseManager;
import com.myserver.workbench.gui.WorkbenchGUI;
import com.myserver.workbench.recipe.RecipeManager;
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

    public WorkbenchCommand(org.bukkit.plugin.Plugin plugin, CraftingManager craftingManager, RecipeManager recipeManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.craftingManager = craftingManager;
        this.recipeManager = recipeManager;
        this.databaseManager = databaseManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : new String[]{"gui", "item", "admin", "reload"}) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
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
            player.sendMessage("§e/wb item §7- 설치 테스트용 아이템을 받습니다.");
            player.sendMessage("§e/wb reload §7- 설정 및 레시피를 다시 불러옵니다.");
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            int unlockedSlots = craftingManager.getUnlockedSlots(player.getUniqueId());
            // Fetch proficiency from DB (currently we can just pass 10 to test unlocked recipes)
            int testProficiency = 10; 
            
            WorkbenchGUI gui = new WorkbenchGUI(plugin, player, unlockedSlots, recipeManager, testProficiency, null, null);
            player.openInventory(gui.getInventory());
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("wolfoolcraft.admin")) {
                player.sendMessage("§c권한이 없습니다.");
                return true;
            }
            com.myserver.workbench.gui.AdminEncyclopediaGUI gui = new com.myserver.workbench.gui.AdminEncyclopediaGUI(player, recipeManager, null);
            player.openInventory(gui.getInventory());
            return true;
        }
        
        if (args[0].equalsIgnoreCase("item")) {
            ItemStack item = new ItemStack(Material.PAPER);
            item.editMeta(meta -> {
                meta.displayName(net.kyori.adventure.text.Component.text("§a[테스트용 작업대]"));
                meta.setCustomModelData(10001); // F키 감지용 CustomModelData
            });
            player.getInventory().addItem(item);
            player.sendMessage("§a설치 테스트용 아이템이 지급되었습니다! 들고 F키를 눌러보세요.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("wolfoolcraft.admin")) {
                player.sendMessage("§c권한이 없습니다.");
                return true;
            }
            plugin.reloadConfig();
            recipeManager.loadRecipes();
            player.sendMessage("§a[작업대] config.yml 및 recipes.yml을 다시 불러왔습니다!");
            return true;
        }

        return true;
    }
}
