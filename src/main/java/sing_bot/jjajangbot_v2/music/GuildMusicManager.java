package sing_bot.jjajangbot_v2.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.dv8tion.jda.api.entities.Guild;

public class GuildMusicManager {
    public final AudioPlayer player;
    public final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;
    public final Guild guild;

    public GuildMusicManager(AudioPlayerManager manager, Guild guild) {
        this.guild = guild;
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(player, guild);
        this.player.addListener(this.scheduler);
        this.sendHandler = new AudioPlayerSendHandler(player);
    }

    public AudioPlayerSendHandler getSendHandler() {
        return sendHandler;
    }
}
