package com.wolfool.workbench.integration;

import com.wolfool.workbench.recipe.RecipeManager;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * CraftEngine 이 자기 설정을 다 읽은 시점에 레시피의 커스텀 아이템 ID 를 검사한다.
 *
 * <p>CraftEngine 은 우리 {@code onEnable} 은 물론 {@code ServerLoadEvent} 보다도
 * 뒤에 팩을 읽는다. 그 전에 물어보면 멀쩡한 ID 도 '없다' 고 나오므로 이 이벤트를
 * 기다려야 한다. {@code /ce reload} 로 아이템이 바뀐 경우도 여기서 같이 잡힌다.
 *
 * <p>CraftEngine 이 없는 서버에서는 이 클래스를 아예 건드리면 안 된다. 등록하는 쪽에서
 * {@link CraftEngineBridge#isAvailable()} 로 막는다.
 */
public class CraftEngineReloadListener implements Listener {

    private final RecipeManager recipeManager;
    private final CraftEngineBridge craftEngine;

    public CraftEngineReloadListener(RecipeManager recipeManager, CraftEngineBridge craftEngine) {
        this.recipeManager = recipeManager;
        this.craftEngine = craftEngine;
    }

    @EventHandler
    public void onCraftEngineReload(CraftEngineReloadEvent event) {
        recipeManager.validateCustomItems(craftEngine);
    }
}
