package com.wolfool.workbench.integration;

import com.wolfool.mastery.MasteryApi;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * 숙련(Mastery) 를 실제로 부르는 쪽.
 *
 * <p>Mastery 클래스를 직접 쓰므로 이 클래스는 그 플러그인이 있을 때만 로드해야 한다.
 * {@link SkillBridge#create(Plugin)} 이 그걸 보장한다. 다른 데서 직접 {@code new} 하지 마라.
 *
 * <p><b>여기서 말하는 "스킬" 은 직업이다.</b> 레시피의 {@code skill-type} 에
 * {@code farmer · fisher · miner · cook} (또는 한국어 이름)을 적으면, 그 직업으로
 * 일하는 사람의 숙련도를 본다.
 *
 * <p>제작법은 두 가지로 열린다 — <b>숙련도가 요구치에 닿거나</b>, 마법의 황금 솥처럼
 * <b>물건을 써서 따로 익히거나.</b> {@link #meets} 가 둘을 같이 본다.
 *
 * <p>경험치는 <b>주지 않는다.</b> Mastery 는 직업 활동(수확·낚시·채굴·요리)으로만
 * 숙련도가 오른다. 제작으로도 오르게 하면 재료만 있으면 방에 앉아 100 을 찍을 수 있다.
 */
final class MasteryBridge implements SkillBridge {

    private final Plugin plugin;

    MasteryBridge(Plugin plugin) {
        this.plugin = plugin;
        // 여기서 한 번 불러봐서 API 가 진짜 준비됐는지 확인한다.
        // 실패하면 create() 가 잡아서 다음 후보로 떨어진다
        if (!MasteryApi.isReady()) {
            throw new IllegalStateException("Mastery 가 아직 준비되지 않았다");
        }
    }

    @Override
    public boolean isAvailable() {
        return MasteryApi.isReady();
    }

    @Override
    public int levelOf(Player player, @Nullable String skillName) {
        return MasteryApi.levelOf(player, skillName);
    }

    @Override
    public boolean knowsSkill(@Nullable String skillName) {
        return MasteryApi.knowsJob(skillName);
    }

    @Override
    public String displayName(@Nullable String skillName) {
        return MasteryApi.displayName(skillName);
    }

    @Override
    public boolean meets(Player player, @Nullable String skillName, int required) {
        if (required <= 0) return true;
        return MasteryApi.levelOf(player, skillName) >= required;
    }

    @Override
    public void grantXp(Player player, @Nullable String skillName, double amount) {
        // 제작으로는 숙련도가 오르지 않는다. 위 설명을 보라
    }
}
