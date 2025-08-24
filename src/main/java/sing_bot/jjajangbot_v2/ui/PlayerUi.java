
package sing_bot.jjajangbot_v2.ui;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.time.Instant;

public class PlayerUI {
    public static final String BTN_PLAYPAUSE = "player:playpause";
    public static final String BTN_SKIP      = "player:skip";
    public static final String BTN_STOP      = "player:stop";
    public static final String BTN_SEARCH    = "player:search";
    public static final String BTN_AUTOPLAY  = "player:autoplay";
    public static final String MENU_VOLUME   = "player:volume";

    public static MessageEmbed buildStatus(String title, String subtitle, boolean paused, boolean autoplay, int volume) {
        return new EmbedBuilder()
                .setTitle("🎵 Jjajang Player")
                .setDescription(title != null ? "**지금 재생중:** " + title : "현재 재생중인 트랙 없음")
                .addField("상태", paused ? "⏸ 일시정지" : "▶ 재생중", true)
                .addField("자동재생", autoplay ? "🔁 ON" : "⏹ OFF", true)
                .addField("볼륨", volume + "%", true)
                .setFooter(subtitle != null ? subtitle : "", null)
                .setTimestamp(Instant.now())
                .build();
    }

    public static ActionRow rowMain(boolean paused, boolean autoplay) {
        return ActionRow.of(
                Button.primary(BTN_PLAYPAUSE, paused ? "▶ 재생" : "⏸ 일시정지"),
                Button.secondary(BTN_SKIP, "⏭ 스킵"),
                Button.danger(BTN_STOP, "⏹ 정지"),
                Button.secondary(BTN_SEARCH, "🔍 검색"),
                autoplay ? Button.success(BTN_AUTOPLAY, "🔁 자동재생 ON")
                         : Button.secondary(BTN_AUTOPLAY, "🔁 자동재생 OFF")
        );
    }

    public static ActionRow rowVolume(int current) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(MENU_VOLUME).setPlaceholder("볼륨 조절");
        int[] options = new int[]{25, 50, 75, 100, 125, 150};
        for (int v : options) {
            menu.addOption(v + "%", String.valueOf(v), v == current ? "현재" : null);
        }
        return ActionRow.of(menu.build());
    }
}
