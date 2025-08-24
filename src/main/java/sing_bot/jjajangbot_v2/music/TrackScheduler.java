package sing_bot.jjajangbot_v2.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.Guild;
import sing_bot.jjajangbot_v2.ui.PlayerUI;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue = new LinkedBlockingQueue<>();
    private final Guild guild;
    private volatile boolean loop = false;
    private volatile AudioTrack lastTrack = null;

    public TrackScheduler(AudioPlayer player, Guild guild) {
        this.player = player;
        this.guild = guild;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
        PlayerUI.updateNowPlaying(guild, player, queue);
    }

    public void nextTrack() {
        AudioTrack next = queue.poll();
        if (next != null) {
            player.startTrack(next, false);
        } else {
            player.stopTrack();
        }
        PlayerUI.updateNowPlaying(guild, player, queue);
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        lastTrack = track;
        PlayerUI.updateNowPlaying(guild, player, queue);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            if (loop && lastTrack != null) {
                player.startTrack(lastTrack.makeClone(), false);
            } else {
                nextTrack();
            }
        }
        PlayerUI.updateNowPlaying(guild, player, queue);
    }

    public BlockingQueue<AudioTrack> getQueue() {
        return queue;
    }
}
