package sing_bot.jjajangbot_v2.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import net.dv8tion.jda.api.entities.Guild;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lavaplayer 수명/소스 등록 + 길드별 매니저 관리
 */
public class PlayerManager {

    private static final PlayerManager INSTANCE = new PlayerManager();

    public static PlayerManager getInstance() {
        return INSTANCE;
    }

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();

    private PlayerManager() {
        this.playerManager = new DefaultAudioPlayerManager();

        // ✅ 유튜브 소스 등록 (dev.lavalink.youtube v2)
        YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager();
        this.playerManager.registerSourceManager(yt);

        // (선택) 필요시 설정 추가 가능
        // this.playerManager.getConfiguration().setFilterHotSwapEnabled(true);
    }

    /** 길드별 GuildMusicManager 반환(없으면 생성) */
    public GuildMusicManager getGuildMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), id -> {
            // 🔧 생성자 시그니처에 맞춰 한 개만 전달
            GuildMusicManager mm = new GuildMusicManager(playerManager);
            guild.getAudioManager().setSendingHandler(mm.getSendHandler());
            return mm;
        });
    }

    /**
     * URL 또는 검색식(예: ytsearch:"query") 로드 → 큐 추가
     */
    public void loadAndPlay(Guild guild, String identifier, TrackLoadResultHandler cb) {
        GuildMusicManager mm = getGuildMusicManager(guild);

        playerManager.loadItemOrdered(mm, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                mm.scheduler.queue(track);
                if (cb != null) cb.onLoaded(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack first = playlist.getSelectedTrack();
                if (first == null && !playlist.getTracks().isEmpty()) {
                    first = playlist.getTracks().get(0);
                }
                if (first != null) {
                    mm.scheduler.queue(first);
                    if (cb != null) cb.onLoaded(first);
                } else {
                    if (cb != null) cb.onNoMatches();
                }
            }

            @Override
            public void noMatches() {
                if (cb != null) cb.onNoMatches();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                if (cb != null) cb.onFailed(exception);
            }
        });
    }

    /**
     * 검색 유틸: ytsearch:"..." 로 상위 N개 결과 반환
     */
    public void search(String ytSearchQuery, int limit, Consumer<List<AudioTrack>> onSuccess, Consumer<Throwable> onError) {
        playerManager.loadItem(ytSearchQuery, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                List<AudioTrack> list = new ArrayList<>();
                list.add(track);
                if (onSuccess != null) onSuccess.accept(list);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<AudioTrack> tracks = playlist.getTracks();
                List<AudioTrack> out = tracks.size() > limit ? tracks.subList(0, limit) : new ArrayList<>(tracks);
                if (onSuccess != null) onSuccess.accept(out);
            }

            @Override
            public void noMatches() {
                if (onSuccess != null) onSuccess.accept(List.of());
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                if (onError != null) onError.accept(exception);
            }
        });
    }

    /** 콜백 인터페이스 (CommandListener에서 사용) */
    public interface TrackLoadResultHandler {
        void onLoaded(AudioTrack t);
        void onNoMatches();
        void onFailed(Throwable t);
    }
}
