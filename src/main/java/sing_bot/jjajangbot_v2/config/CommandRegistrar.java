package sing_bot.jjajangbot_v2.config;

import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class CommandRegistrar extends ListenerAdapter {

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        var play = Commands.slash("play", "URL 또는 검색어로 재생")
                .addOptions(
                        new OptionData(OptionType.STRING, "url", "유튜브/사운드클라우드 등 URL", false),
                        new OptionData(OptionType.STRING, "query", "검색어 (URL 대신)", false)
                );
        var search = Commands.slash("search", "검색 결과 상위 항목 재생")
                .addOptions(new OptionData(OptionType.STRING, "query", "검색어", true));
        var pause  = Commands.slash("pause", "일시정지");
        var resume = Commands.slash("resume", "재개");
        var skip   = Commands.slash("skip", "다음 곡");
        var queue  = Commands.slash("queue", "대기열 보기");
        var stop   = Commands.slash("stop", "정지 및 퇴장");

        event.getJDA().getGuilds().forEach(guild ->
                guild.updateCommands()
                        .addCommands(play, search, pause, resume, skip, queue, stop)
                        .queue()
        );
    }
}
