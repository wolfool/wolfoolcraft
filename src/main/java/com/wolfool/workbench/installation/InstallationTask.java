package com.wolfool.workbench.installation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 설치 모드인 사람의 미리보기를 시선 끝으로 옮기고 조작 힌트를 띄운다.
 *
 * <p>매 틱 도는 대신 2틱마다 돈다. 미리보기가 따라오는 느낌은 그대로면서
 * 텔레포트 패킷은 절반으로 준다.
 */
public class InstallationTask extends BukkitRunnable {

    /** 문서의 하단 안내와 같은 문구 */
    private static final Component HINT = Component.text("좌클릭 ", NamedTextColor.WHITE)
            .append(Component.text("회전", NamedTextColor.GRAY))
            .append(Component.text("   우클릭 ", NamedTextColor.WHITE))
            .append(Component.text("설치", NamedTextColor.GRAY))
            .append(Component.text("   F ", NamedTextColor.WHITE))
            .append(Component.text("모드 종료", NamedTextColor.RED));

    private static final Component BLOCKED = Component.text("여기엔 놓을 수 없습니다", NamedTextColor.RED);

    private final InstallationManager manager;

    public InstallationTask(InstallationManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!manager.isInstalling(player)) continue;
            boolean ok = manager.updatePreview(player);
            player.sendActionBar(ok ? HINT : BLOCKED);
        }
    }
}
