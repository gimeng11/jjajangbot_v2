package sing_bot.jjajangbot_v2.config;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sing_bot.jjajangbot_v2.music.CommandListener;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class BotConfig {

    // ENV DISCORD_TOKEN > properties discord.token
    @Value("${DISCORD_TOKEN:${discord.token:}}")
    private String token;

    @Bean
    public JDA jda(CommandListener listener) throws Exception {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Discord 토큰이 비었습니다. DISCORD_TOKEN 또는 discord.token 설정이 필요합니다.");
        }

        JDABuilder builder = JDABuilder.createDefault(
                token,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.GUILD_MESSAGES // 슬래시 명령엔 이 정도면 충분
        );

        // 권한 없는 인텐트로 인한 경고 제거
        builder.disableCache(CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);

        builder.setStatus(OnlineStatus.ONLINE);
        builder.setActivity(Activity.listening("jjajang 🎵"));
        builder.addEventListeners(listener);

        JDA jda = builder.build();
        jda.awaitReady();
        upsertSlashCommands(jda);
        return jda;
    }

    private void upsertSlashCommands(JDA jda) {
        List<CommandData> cmds = new ArrayList<>();

        cmds.add(
                Commands.slash("play", "노래 재생 (URL 또는 검색어)")
                        .addOption(OptionType.STRING, "url", "YouTube/기타 오디오 URL", false)
                        .addOption(OptionType.STRING, "query", "URL 대신 검색어", false)
        );

        cmds.add(
                Commands.slash("search", "유튜브에서 곡 검색")
                        .addOption(OptionType.STRING, "query", "검색어", true)
        );

        cmds.add(Commands.slash("pause",  "일시정지"));
        cmds.add(Commands.slash("resume", "다시 재생"));
        cmds.add(Commands.slash("skip",   "다음 곡으로"));
        cmds.add(Commands.slash("queue",  "재생목록 보기(간단 표시)"));
        cmds.add(Commands.slash("stop",   "정지하고 나가기"));

        jda.updateCommands().addCommands(cmds).queue();
    }

    @PostConstruct
    void logHint() {
        if (token == null || token.isBlank()) {
            System.err.println("[WARN] DISCORD_TOKEN/discord.token 미설정");
        }
    }
}
