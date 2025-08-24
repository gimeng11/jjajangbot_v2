package sing_bot.jjajangbot_v2.music;

import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class PlayerManager {

    private final AudioPlayerManager audioPlayerManager;
    private final Map<Long, GuildMusicManager> musicManagers;
    private static final Pattern URL_PATTERN =
            Pattern.compile("^(https?://|www\\.)\\S+$", Pattern.CASE_INSENSITIVE);

    public PlayerManager() {
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        // YouTube v2 소스 등록 (연령/로그인 제한 이슈 대응)
        this.audioPlayerManager.registerSourceManager(new YoutubeAudioSourceManager(true));

        this.musicManagers = new ConcurrentHashMap<>();
    }

    public GuildMusicManager getGuildMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(),
                id -> {
                    GuildMusicManager mm = new GuildMusicManager(audioPlayerManager, guild);
                    guild.getAudioManager().setSendingHandler(mm.getSendHandler());
                    return mm;
                });
    }

    public void loadAndPlay(TextChannel channel, String userInput) {
        GuildMusicManager musicManager = getGuildMusicManager(channel.getGuild());

        final boolean isUrl = URL_PATTERN.matcher(userInput).matches();
        String identifier = isUrl ? userInput : "ytmsearch:" + userInput; // 우선 YT Music 검색

        audioPlayerManager.loadItemOrdered(musicManager, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.scheduler.queue(track);
                channel.sendMessage("🎶 재생 대기열 추가: " + track.getInfo().title)
                        .queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack first = playlist.getSelectedTrack();
                if (first == null && !playlist.getTracks().isEmpty()) {
                    first = playlist.getTracks().get(0);
                }
                if (first != null) {
                    musicManager.scheduler.queue(first);
                    channel.sendMessage("🎶 재생 대기열 추가: " + first.getInfo().title)
                            .queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS));
                } else {
                    channel.sendMessage("❌ 재생 가능한 트랙이 없어요.")
                            .queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS));
                }
            }

            @Override
            public void noMatches() {
                // 일반 youtube 검색 한번 더
                if (!isUrl && !identifier.startsWith("ytsearch:")) {
                    audioPlayerManager.loadItemOrdered(musicManager, "ytsearch:" + userInput, this);
                    return;
                }
                channel.sendMessage("❌ 결과가 없어요.")
                        .queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS));
            }

            @Override
            public void loadFailed(FriendlyException ex) {
                String msg = String.valueOf(ex.getMessage()).toLowerCase();

                if (!isUrl && !identifier.startsWith("ytmsearch:")
                        && (msg.contains("requires login") || msg.contains("signin") || msg.contains("age"))) {
                    channel.sendMessage("🔒 로그인/연령 제한 영상 → **YouTube Music**으로 다시 찾는 중…")
                            .queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS));
                    audioPlayerManager.loadItemOrdered(musicManager, "ytmsearch:" + userInput, this);
                    return;
                }

                channel.sendMessage("❌ 로드 실패: " + ex.getMessage())
                        .queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS));
            }
        });
    }
}
