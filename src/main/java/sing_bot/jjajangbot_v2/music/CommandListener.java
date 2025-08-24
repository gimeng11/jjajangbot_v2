package sing_bot.jjajangbot_v2.music;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
        String url   = getStringOption(event, "url");
        String query = getStringOption(event, "query");
        if ((url == null || url.isBlank()) && (query == null || query.isBlank())) {
            event.reply("❗ `/play`는 `url` 또는 `query` 중 하나가 필요해요.\n예) `/play query:아이유 블루밍`")
                    .setEphemeral(true).queue();
            return;
        }
        String input = (url != null && !url.isBlank()) ? url : "ytsearch:" + query;

        AudioChannelUnion voice = requireUserVoiceChannel(event);
        if (voice == null) return;
        join(event, voice);

        event.deferReply(false).queue(hook -> {
            playerManager.loadAndPlay((TextChannel) event.getChannel().asGuildMessageChannel(), input);
            hook.sendMessage("🎶 요청 처리 중: " + (query != null ? "`" + query + "`" : input))
                    .queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));
        });
    }

    private void handleSearch(SlashCommandInteractionEvent event) {
        String query = getStringOption(event, "query");
        if (query == null || query.isBlank()) {
            event.reply("❗ 검색어를 넣어주세요.\n예) `/search sugar`").setEphemeral(true).queue();
            return;
        }

        AudioChannelUnion voice = requireUserVoiceChannel(event);
        if (voice == null) return;
        join(event, voice);

        String input = "ytsearch:" + query;
        event.deferReply(false).queue(hook -> {
            playerManager.loadAndPlay((TextChannel) event.getChannel().asGuildMessageChannel(), input); // 2개 인자
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
        music.scheduler.nextTrack(); // void 시그니처에 맞춤
        event.reply("⏭️ 다음 곡으로")
                .queue(m -> m.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
    }

    private void handleQueue(SlashCommandInteractionEvent event) {
        // 현재 스케줄러에 큐 조회 API가 없다면 간단 메시지로 대체
        event.reply("📜 대기열 표시 기능은 아직 스케줄러에 API가 없어 간단 안내만 제공해요.")
                .queue(m -> m.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
    }

    private void handleStop(SlashCommandInteractionEvent event) {
        var music = playerManager.getGuildMusicManager(event.getGuild());
        music.player.stopTrack(); // clearQueue() 없음 -> 현재 곡만 정지
        leave(event);
        event.reply("⏹️ 정지하고 음성채널에서 나갑니다")
                .queue(m -> m.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
    }

    /* ===================== 유틸 ===================== */

    private String getStringOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsString() : null;
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
