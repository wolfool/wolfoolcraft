package com.wolfool.workbench.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * 숙련도 조회·지급.
 *
 * <p>이 인터페이스에는 Mastery 타입이 하나도 안 나온다. 일부러 그렇게 뒀다.
 * Mastery 는 선택 의존이라 서버에 없을 수 있는데, 클래스 시그니처에 그 타입이 있으면
 * JVM 이 이 클래스를 검증할 때 {@code NoClassDefFoundError} 로 플러그인이 통째로 죽는다.
 * 실제 호출은 {@link MasteryBridge} 가 하고, 그 클래스는 Mastery 가 있을 때만 로드된다.
 */
public interface SkillBridge {

    boolean isAvailable();

    /** 그 스킬의 현재 레벨. 연동이 없으면 0. */
    int levelOf(Player player, @Nullable String skillName);

    /** 그 이름의 스킬이 실제로 있는지. 레시피의 오타를 짚어줄 때 쓴다. */
    boolean knowsSkill(@Nullable String skillName);

    /** 화면에 보여 줄 이름. 모르는 이름이면 적힌 그대로. */
    String displayName(@Nullable String skillName);

    /** 레시피를 쓸 수 있는지. 요구치가 0 이하면 스킬과 무관하게 열려 있다. */
    boolean meets(Player player, @Nullable String skillName, int required);

    /** 숙련도 경험치를 준다. */
    void grantXp(Player player, @Nullable String skillName, double amount);

    /**
     * 서버 상황에 맞는 구현을 고른다.
     *
     * <p>이 서버의 직업·숙련도는 <b>Mastery 가 전부 들고 있다.</b> 예전에는 AuraSkills 를
     * 봤는데, Mastery 가 레벨을 직접 저장하게 되면서 그쪽은 뺐다. 그래서 후보가 하나뿐이다.
     *
     * <p>{@code new MasteryBridge(...)} 는 그 줄에 도달할 때 비로소 클래스를 읽는다.
     * 그래서 플러그인이 없으면 아예 건드리지 않고 넘어간다.
     */
    static SkillBridge create(Plugin plugin) {
        if (Bukkit.getPluginManager().isPluginEnabled("Mastery")) {
            try {
                SkillBridge bridge = new MasteryBridge(plugin);
                plugin.getLogger().info("Mastery 연동됨 - 직업 숙련도로 레시피가 풀린다");
                return bridge;
            } catch (Throwable t) {
                plugin.getLogger().warning("Mastery 연동에 실패했다: " + t);
            }
        }
        plugin.getLogger().info("숙련도를 물어볼 곳이 없다. 요구치가 있는 레시피는 잠긴 채로 둔다.");
        return new None();
    }

    /** Mastery 가 없을 때. 요구 숙련도가 없는 레시피만 열린다. */
    final class None implements SkillBridge {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public int levelOf(Player player, @Nullable String skillName) {
            return 0;
        }

        @Override
        public boolean knowsSkill(@Nullable String skillName) {
            return false;
        }

        @Override
        public String displayName(@Nullable String skillName) {
            return String.valueOf(skillName);
        }

        @Override
        public boolean meets(Player player, @Nullable String skillName, int required) {
            return required <= 0;
        }

        @Override
        public void grantXp(Player player, @Nullable String skillName, double amount) {
            // 줄 곳이 없다
        }
    }
}
