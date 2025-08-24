package sing_bot.jjajangbot_v2.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sing_bot.jjajangbot_v2.music.CommandListener;

@Configuration
public class BotConfig {

    @Value("${discord.token}")
    private String token;

    @Bean
    public JDA jda(CommandListener commandListener) throws Exception {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DISCORD_TOKEN 환경변수가 설정되지 않았습니다.");
        }

        JDABuilder builder = JDABuilder.createDefault(
                token,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.GUILD_MESSAGES // 슬래시 커맨드엔 이 정도면 충분
        );

        builder.disableCache(CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);
        builder.setActivity(Activity.listening("/play, /search"));

        JDA jda = builder.build();
        jda.awaitReady();
        jda.addEventListener(commandListener);
        return jda;
    }
}
