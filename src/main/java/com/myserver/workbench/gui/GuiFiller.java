package com.myserver.workbench.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * GUI 빈칸을 막는 아이템.
 *
 * <p>배경을 창 제목 그림으로 그리기 때문에, 칸을 회색 유리로 막으면 그림이 가려진다.
 * 그래서 완전히 투명한 CraftEngine 아이템({@code wolfoolcraft:empty})을 쓴다.
 * 클릭은 리스너가 어차피 전부 막으므로 여기서는 '안 보이는 것' 만 하면 된다.
 *
 * <p>CraftEngine 이 없거나 그 아이템이 없으면 예전처럼 회색 유리로 떨어진다.
 * 배경은 가려지지만 GUI 자체는 열린다.
 */
public final class GuiFiller {

    private GuiFiller() {
    }

    public static ItemStack create(Plugin plugin,
                                   @Nullable com.myserver.workbench.integration.CraftEngineBridge craftEngine,
                                   Player forPlayer) {
        return create(plugin, craftEngine, forPlayer, Component.empty());
    }

    /**
     * 이름이 붙은 투명 아이템.
     *
     * <p>도감 탭처럼 '보이지는 않지만 눌리는 자리' 에 쓴다. 눌린 상태는 배경 그림이
     * 보여주므로 아이템은 마우스를 올렸을 때 이름만 알려주면 된다.
     *
     * <p>{@code forPlayer} 는 반드시 있어야 한다. CraftEngine 이 아이템을 만들 때
     * 플레이어를 들여다보기 때문에, null 을 주면 예외가 나서 회색 유리로 떨어진다.
     */
    public static ItemStack create(Plugin plugin,
                                   @Nullable com.myserver.workbench.integration.CraftEngineBridge craftEngine,
                                   Player forPlayer,
                                   Component name) {
        String id = plugin.getConfig().getString("gui.filler-item-id", "wolfoolcraft:empty");
        if (craftEngine != null && id != null && !id.isEmpty()) {
            ItemStack made = craftEngine.createItem(id, forPlayer);
            if (made != null) {
                setName(made, name);
                return made;
            }
        }
        ItemStack fallback = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        setName(fallback, name);
        return fallback;
    }

    private static void setName(ItemStack stack, Component name) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
    }
}
