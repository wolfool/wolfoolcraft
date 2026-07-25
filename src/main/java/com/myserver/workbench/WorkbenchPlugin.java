package com.myserver.workbench;

import org.bukkit.plugin.java.JavaPlugin;

import com.myserver.workbench.installation.InstallationManager;
import com.myserver.workbench.installation.InstallationListener;

import com.myserver.workbench.gui.WorkbenchGUIListener;
import com.myserver.workbench.gui.QueueUpdateTask;
import com.myserver.workbench.crafting.CraftingManager;
import com.myserver.workbench.database.DatabaseManager;
import com.myserver.workbench.recipe.RecipeManager;

public class WorkbenchPlugin extends JavaPlugin {

    private InstallationManager installationManager;
    private CraftingManager craftingManager;
    private DatabaseManager databaseManager;
    private RecipeManager recipeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("CustomWorkbench has been enabled!");
        
        databaseManager = new DatabaseManager(this);
        databaseManager.connect();
        
        recipeManager = new RecipeManager(this);
        recipeManager.loadRecipes();
        
        installationManager = new InstallationManager(this, databaseManager);
        craftingManager = new CraftingManager(databaseManager);
        
        getServer().getPluginManager().registerEvents(new InstallationListener(installationManager), this);
        getServer().getPluginManager().registerEvents(new WorkbenchGUIListener(this, craftingManager, recipeManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(craftingManager), this);
        getServer().getPluginManager().registerEvents(new com.myserver.workbench.gui.AdminEncyclopediaListener(recipeManager), this);
        
        WorkbenchCommand cmd = new WorkbenchCommand(this, craftingManager, recipeManager, databaseManager);
        getCommand("wb").setExecutor(cmd);
        getCommand("wb").setTabCompleter(cmd);
        
        // Start queue update task (runs every 10 ticks = 0.5s)
        new QueueUpdateTask(craftingManager).runTaskTimer(this, 20L, 10L);
    }

    @Override
    public void onDisable() {
        getLogger().info("CustomWorkbench has been disabled!");
        
        // Save all online players' queues
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            craftingManager.savePlayer(player.getUniqueId());
        }
        
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
    }
}
