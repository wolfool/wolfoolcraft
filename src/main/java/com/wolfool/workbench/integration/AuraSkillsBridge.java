package com.wolfool.workbench.integration;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skill;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * AuraSkills 를 실제로 부르는 쪽.
 *
 * <p>AuraSkills 클래스를 직접 쓰므로 이 클래스는 그 플러그인이 있을 때만 로드해야 한다.
 * {@link SkillBridge#create(Plugin)} 이 그걸 보장한다. 다른 데서 직접 {@code new} 하지 마라.
 */
final class AuraSkillsBridge implements SkillBridge {

    private final Plugin plugin;

    AuraSkillsBridge(Plugin plugin) {
        this.plugin = plugin;
        // 여기서 한 번 불러봐서 API 가 진짜 준비됐는지 확인한다.
        // 실패하면 create() 가 잡아서 None 으로 떨어진다.
        AuraSkillsApi.get();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /** 이름으로 스킬을 찾는다. 오타나 없는 이름이면 null. */
    private @Nullable Skill skillOf(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Skills.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private @Nullable SkillsUser user(Player player) {
        try {
            return AuraSkillsApi.get().getUser(player.getUniqueId());
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public int levelOf(Player player, @Nullable String skillName) {
        Skill skill = skillOf(skillName);
        SkillsUser user = user(player);
        if (skill == null || user == null) return 0;
        try {
            return user.getSkillLevel(skill);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public double xpOf(Player player, @Nullable String skillName) {
        Skill skill = skillOf(skillName);
        SkillsUser user = user(player);
        if (skill == null || user == null) return 0;
        try {
            return user.getSkillXp(skill);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public boolean knowsSkill(@Nullable String skillName) {
        return skillOf(skillName) != null;
    }

    @Override
    public boolean meets(Player player, @Nullable String skillName, int required) {
        if (required <= 0) return true;
        return levelOf(player, skillName) >= required;
    }

    @Override
    public void grantXp(Player player, @Nullable String skillName, double amount) {
        if (amount <= 0) return;
        Skill skill = skillOf(skillName);
        SkillsUser user = user(player);
        if (skill == null || user == null) return;
        try {
            user.addSkillXp(skill, amount);
        } catch (Throwable t) {
            plugin.getLogger().warning("숙련도 XP 지급 실패 (" + skillName + "): " + t.getMessage());
        }
    }
}
