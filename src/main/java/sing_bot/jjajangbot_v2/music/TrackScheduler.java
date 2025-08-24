package sing_bot.jjajangbot_v2.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;

    /** 패널/큐 표시용 공개 큐 */
    public final BlockingQueue<AudioTrack> queue = new LinkedBlockingQueue<>();

    /** 타임아웃류 실패 시 1회 재시도 플래그 */
    private volatile boolean retriedOnce = false;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
    }

    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    public void nextTrack() {
        retriedOnce = false;
        player.startTrack(queue.poll(), false);
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        retriedOnce = false;
    }

    @Override
    public void onTrackEnd(
            AudioPlayer player,
            AudioTrack track,
            com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason endReason
    ) {
        if (endReason == com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason.FINISHED
                || endReason == com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason.LOAD_FAILED) {
            nextTrack();
        }
    }


    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        // 네트워크 타임아웃/연결 계열은 1회 재시도
        if (!retriedOnce && isTimeoutLike(exception)) {
            retriedOnce = true;
            player.startTrack(track.makeClone(), false);
            return;
        }
        nextTrack();
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        nextTrack();
    }

    private boolean isTimeoutLike(Throwable t) {
        while (t != null) {
            if (t instanceof SocketTimeoutException || t instanceof ConnectException) return true;
            t = t.getCause();
        }
        return false;
    }
}
