package com.wolfool.workbench;

import org.bukkit.plugin.java.JavaPlugin;

import com.wolfool.workbench.installation.InstallationManager;
import com.wolfool.workbench.installation.InstallationListener;

import com.wolfool.workbench.gui.WorkbenchGUIListener;
import com.wolfool.workbench.gui.QueueUpdateTask;
import com.wolfool.workbench.crafting.CraftingManager;
import com.wolfool.workbench.database.DatabaseManager;
import com.wolfool.workbench.recipe.RecipeManager;

public class WorkbenchPlugin extends JavaPlugin {

    private InstallationManager installationManager;
    private CraftingManager craftingManager;
    private DatabaseManager databaseManager;
    private RecipeManager recipeManager;
    private com.wolfool.workbench.integration.CraftEngineBridge craftEngine;
    private com.wolfool.workbench.integration.SkillBridge skills;
    private com.wolfool.workbench.editor.RecipeEditorListener recipeEditor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("CustomWorkbench has been enabled!");
        
        databaseManager = new DatabaseManager(this);
        databaseManager.connect();
        
        recipeManager = new RecipeManager(this);
        recipeManager.loadRecipes();
        
        craftEngine = new com.wolfool.workbench.integration.CraftEngineBridge(this);
        skills = com.wolfool.workbench.integration.SkillBridge.create(this);
        installationManager = new InstallationManager(this, databaseManager, craftEngine);
        craftingManager = new CraftingManager(databaseManager);

        getServer().getPluginManager().registerEvents(new InstallationListener(installationManager), this);
        getServer().getPluginManager().registerEvents(new WorkbenchGUIListener(this, craftingManager, recipeManager, craftEngine, skills), this);
        getServer().getPluginManager().registerEvents(
                new com.wolfool.workbench.installation.FurnitureListener(
                        this, installationManager, craftingManager, recipeManager, skills,
                        databaseManager, craftEngine), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(craftingManager), this);

        // 레시피에 적힌 CraftEngine 아이템 검사는 CraftEngine 이 자기 팩을 다 읽은
        // 뒤에야 뜻이 있다. 그 시점이 우리 onEnable 은 물론 ServerLoadEvent 보다도
        // 늦어서, CraftEngine 이 알려주는 이벤트를 기다린다.
        if (craftEngine.isAvailable()) {
            getServer().getPluginManager().registerEvents(
                    new com.wolfool.workbench.integration.CraftEngineReloadListener(
                            recipeManager, craftEngine), this);
        }
        getServer().getPluginManager().registerEvents(new com.wolfool.workbench.gui.AdminEncyclopediaListener(recipeManager, craftEngine), this);

        // 레시피를 게임 안에서 만들고 고치는 GUI
        recipeEditor = new com.wolfool.workbench.editor.RecipeEditorListener(
                this, recipeManager, craftEngine);
        getServer().getPluginManager().registerEvents(recipeEditor, this);

        WorkbenchCommand cmd = new WorkbenchCommand(this, craftingManager, recipeManager, databaseManager, skills, craftEngine, recipeEditor);
        getCommand("wb").setExecutor(cmd);
        getCommand("wb").setTabCompleter(cmd);
        
        // Start queue update task (runs every 10 ticks = 0.5s)
        new QueueUpdateTask(craftingManager).runTaskTimer(this, 20L, 10L);
        // 설치 모드 미리보기를 시선에 따라 옮긴다
        new com.wolfool.workbench.installation.InstallationTask(installationManager)
                .runTaskTimer(this, 2L, 2L);
    }

    @Override
    public void onDisable() {
        getLogger().info("CustomWorkbench has been disabled!");
        
        // 설치 모드 미리보기는 저장 대상이 아니다. 남겨두면 유령 엔티티가 된다.
        if (installationManager != null) {
            installationManager.cancelAll();
        }

        // Save all online players' queues
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            craftingManager.savePlayer(player.getUniqueId());
        }
        
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
    }
}
