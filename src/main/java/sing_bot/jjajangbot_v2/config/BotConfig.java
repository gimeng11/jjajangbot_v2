package sing_bot.jjajangbot_v2.config;

import sing_bot.jjajangbot_v2.music.CommandListener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotConfig {

    @Value("${discord.token}")
    private String token;

    // 선택: 슬래시 커맨드를 특정 길드에만 빠르게 등록하고 싶다면 설정
    @Value("${discord.guild-id:}")
    private String guildId;

    private final CommandListener commandListener;

    public BotConfig(CommandListener commandListener) {
        this.commandListener = commandListener;
    }

    @Bean
    public JDA jda() throws Exception {
        // ✅ 여기!
        JDABuilder builder = JDABuilder.createDefault(
                token,
                GatewayIntent.GUILD_VOICE_STATES // 음성 재생에 꼭 필요
        ).enableCache(CacheFlag.VOICE_STATE);

        builder.setStatus(OnlineStatus.ONLINE);
        builder.setActivity(Activity.listening("재생 대기중"));
        builder.addEventListeners(commandListener);

        JDA jda = builder.build();
        jda.awaitReady(); // 준비 완료까지 대기(필수는 아니지만 권장)

        // 슬래시 커맨드 등록 (길드 ID 있으면 길드에, 없으면 글로벌)
        commandListener.registerSlashCommands(jda, guildId);

        return jda;
    }
}
