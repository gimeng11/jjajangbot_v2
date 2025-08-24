package sing_bot.jjajangbot_v2.music;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CommandListener extends ListenerAdapter {

    private final PlayerManager playerManager;

    public CommandListener(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        try {
            switch (event.getName()) {
                case "play"   -> handlePlay(event);
                case "search" -> handleSearch(event);
                case "pause"  -> handlePause(event);
                case "resume" -> handleResume(event);
                case "skip"   -> handleSkip(event);
                case "queue"  -> handleQueue(event);
                case "stop"   -> handleStop(event);
                default -> event.reply("알 수 없는 명령어예요.").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            event.reply("⚠️ 오류가 발생했어요: " + e.getMessage())
                    .setEphemeral(true).queue();
        }
    }

    private void handlePlay(SlashCommandInteractionEvent event) {
        // url, query 둘 다 지원 + 폴백
        String url   = getStringOption(event, "url");
        String query = coalesce(
                getStringOption(event, "query"),
                getStringOption(event, "q"),
                getStringOption(event, "title"),
                getAnyStringOption(event) // 어떤 이름이든 문자열 옵션 하나라도 잡기
        );

        if (isBlank(url) && isBlank(query)) {
            event.reply("❗ `/play`는 `url` 또는 `query` 중 하나가 필요해요.\n예) `/play query:아이유 블루밍`")
                    .setEphemeral(true).queue();
            return;
        }
        String input = !isBlank(url) ? url : "ytsearch:" + query;

        AudioChannelUnion voice = requireUserVoiceChannel(event);
        if (voice == null) return;
        join(event, voice);

        event.deferReply(false).queue(hook -> {
            TextChannel text = event.getChannel().asTextChannel();
            playerManager.loadAndPlay(text, input);
            hook.sendMessage("🎶 요청 처리 중: " + (!isBlank(query) ? "`" + query + "`" : input))
                    .queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));
        });
    }

    private void handleSearch(SlashCommandInteractionEvent event) {
        // “query”를 기본으로, 다른 이름/아무 문자열 옵션도 폴백
        String query = coalesce(
                getStringOption(event, "query"),
                getStringOption(event, "q"),
                getStringOption(event, "title"),
                getAnyStringOption(event)
        );

        if (isBlank(query)) {
            event.reply("❗ 검색어를 넣어주세요.\n예) `/search sugar`").setEphemeral(true).queue();
            return;
        }

        AudioChannelUnion voice = requireUserVoiceChannel(event);
        if (voice == null) return;
        join(event, voice);

        String input = "ytsearch:" + query;
        event.deferReply(false).queue(hook -> {
            TextChannel text = event.getChannel().asTextChannel();
            playerManager.loadAndPlay(text, input);
            hook.sendMessage("🔎 `" + query + "` 검색 상위 결과를 재생합니다.")
                    .queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));
        });
    }

    private void handlePause(SlashCommandInteractionEvent event) {
        var music = playerManager.getGuildMusicManager(event.getGuild());
        music.player.setPaused(true);
        event.reply("⏸️ 일시정지").queue(m -> m.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
    }

    private void handleResume(SlashCommandInteractionEvent event) {
        var music = playerManager.getGuildMusicManager(event.getGuild());
        music.player.setPaused(false);
        event.reply("▶️ 다시 재생").queue(m -> m.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
    }

    private void handleSkip(SlashCommandInteractionEvent event) {
        var music = playerManager.getGuildMusicManager(event.getGuild());
        music.scheduler.nextTrack(); // void 시그니처
        event.reply("⏭️ 다음 곡으로")
                .queue(m -> m.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
    }

    private void handleQueue(SlashCommandInteractionEvent event) {
        // (스케줄러에 큐 조회 API를 만들지 않았다면 간단 안내)
        event.reply("📜 대기열 표시 기능은 준비 중이에요.")
                .queue(m -> m.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
    }

    private void handleStop(SlashCommandInteractionEvent event) {
        var music = playerManager.getGuildMusicManager(event.getGuild());
        music.player.stopTrack();
        leave(event);
        event.reply("⏹️ 정지하고 음성채널에서 나갑니다")
                .queue(m -> m.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
    }

    /* ===================== 유틸 ===================== */

    private String getStringOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsString() : null;
    }

    // 어떤 이름으로든 들어온 “첫 번째 문자열 옵션”을 폴백으로 사용
    private String getAnyStringOption(SlashCommandInteractionEvent event) {
        return event.getOptions().stream()
                .filter(o -> o != null && o.getType() == OptionType.STRING)
                .findFirst()
                .map(OptionMapping::getAsString)
                .orElse(null);
    }

    private String coalesce(String... values) {
        if (values == null) return null;
        for (String v : values) if (!isBlank(v)) return v;
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private AudioChannelUnion requireUserVoiceChannel(SlashCommandInteractionEvent event) {
        Member m = event.getMember();
        if (m == null) {
            event.reply("❗ 길드에서만 사용할 수 있어요.").setEphemeral(true).queue();
            return null;
        }
        AudioChannelUnion ch = m.getVoiceState() != null ? m.getVoiceState().getChannel() : null;
        if (ch == null) {
            event.reply("❗ 먼저 음성채널에 입장해 주세요!").setEphemeral(true).queue();
            return null;
        }
        return ch;
    }

    private void join(SlashCommandInteractionEvent event, AudioChannelUnion channel) {
        AudioManager am = event.getGuild().getAudioManager();
        if (!am.isConnected()) {
            am.openAudioConnection(channel);
            am.setSelfDeafened(true);
        }
    }

    private void leave(SlashCommandInteractionEvent event) {
        AudioManager am = event.getGuild().getAudioManager();
        if (am.isConnected()) {
            am.closeAudioConnection();
        }
    }
}
