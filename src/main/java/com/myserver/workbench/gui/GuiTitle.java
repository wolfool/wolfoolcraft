package com.myserver.workbench.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.Plugin;

/**
 * 인벤토리 GUI 배경 그리기.
 *
 * <p>마인크래프트에는 인벤토리 배경을 바꾸는 방법이 따로 없다. 그래서 <b>창 제목에</b>
 * 배경 그림 한 글자를 넣고, 그걸 왼쪽 위로 밀어 화면을 덮게 만든다.
 * CraftEngine 기본 GUI 도 같은 방식이다.
 *
 * <p>제목은 창 왼쪽에서 8px 떨어진 곳부터 그려진다. 그림을 창 끝에 맞추려면 그만큼
 * 되돌려야 해서 음수 빈칸을 앞에 붙인다. 빈칸은
 * {@code assets/wolfoolcraft/font/space.json} 에 1·2·4·…·128px 로 정의돼 있고,
 * 필요한 픽셀 수를 2의 제곱으로 쪼개 조합한다.
 */
public final class GuiTitle {

    /** space.json 의 음수 빈칸. 순서대로 -1, -2, -4, … -128 */
    private static final char NEG_BASE = '\uE000';
    /** 양수 빈칸. 순서대로 +1, +2, +4, … +128 */
    private static final char POS_BASE = '\uE008';
    private static final Key SPACE_FONT = Key.key("wolfoolcraft", "space");

    private GuiTitle() {
    }

    /**
     * 가로로 미는 글자열.
     *
     * @param pixels 음수면 왼쪽, 양수면 오른쪽. ±255 까지 표현된다
     */
    public static String shift(int pixels) {
        if (pixels == 0) return "";
        char base = pixels < 0 ? NEG_BASE : POS_BASE;
        int left = Math.min(Math.abs(pixels), 255);
        StringBuilder sb = new StringBuilder();
        for (int bit = 7; bit >= 0; bit--) {          // 128, 64, … 1 순으로 채운다
            int step = 1 << bit;
            while (left >= step) {
                sb.append((char) (base + bit));
                left -= step;
            }
        }
        return sb.toString();
    }

    private static Component space(String s) {
        return Component.text(s).font(SPACE_FONT);
    }

    /**
     * 배경 그림을 깐 창 제목.
     *
     * @param configPath config.yml 의 {@code gui.<이 값>} 아래에서 설정을 읽는다
     * @param fallback   설정에 제목 문구가 없을 때 쓸 값
     */
    public static Component of(Plugin plugin, String configPath, String fallback) {
        return of(plugin, configPath, fallback, null);
    }

    /**
     * 배경 그림을 깐 창 제목.
     *
     * @param configPath   config.yml 의 {@code gui.<이 값>} 아래에서 설정을 읽는다
     * @param fallback     설정에 제목 문구가 없을 때 쓸 값
     * @param glyphOverride 이 값이 있으면 {@code background-char} 대신 쓴다.
     *                      도감처럼 상태마다 배경이 다른 창에서 쓴다
     */
    public static Component of(Plugin plugin, String configPath, String fallback, String glyphOverride) {
        String base = "gui." + configPath + ".";
        String glyph = glyphOverride != null
                ? glyphOverride
                : plugin.getConfig().getString(base + "background-char", "");
        String text = plugin.getConfig().getString(base + "title-text", fallback);
        int offsetX = plugin.getConfig().getInt(base + "offset-x", -11);
        int advance = plugin.getConfig().getInt(base + "glyph-advance", 183);
        String fontName = plugin.getConfig().getString(base + "background-font", "minecraft:gui");

        if (glyph == null || glyph.isEmpty()) {
            // 배경 없이 글자만. 리소스팩이 아직 안 깔린 서버에서도 GUI 는 열려야 한다.
            return Component.text(text == null ? fallback : text)
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false);
        }

        // 색을 안 주면 그림이 어둡게 나온다. 마인크래프트가 창 제목을 그릴 때
        // 기본색으로 진회색(0x404040)을 쓰기 때문인데, 색을 안 정한 글자는 그걸 따라간다.
        // 흰색으로 못박아야 원본 밝기 그대로 보인다.
        Component title = space(shift(offsetX))
                .append(Component.text(glyph)
                        .font(Key.key(fontName))
                        .color(NamedTextColor.WHITE));

        if (text != null && !text.isEmpty()) {
            // 그림을 그리고 나면 커서가 그림 오른쪽 끝에 있다. 제목 글자 자리로 되돌린다.
            title = title.append(space(shift(-(offsetX + advance))))
                    .append(Component.text(text).color(NamedTextColor.DARK_GRAY));
        }
        return title.decoration(TextDecoration.ITALIC, false);
    }
}
