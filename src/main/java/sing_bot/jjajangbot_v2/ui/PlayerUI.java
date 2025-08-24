package sing_bot.jjajangbot_v2.ui;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerUI {

    private static final Map<Long, Long> panelMessageIds = new ConcurrentHashMap<>();
    private static final Map<Long, Long> panelChannelIds = new ConcurrentHashMap<>();

    public static void ensurePanel(TextChannel channel, sing_bot.jjajangbot_v2.music.GuildMusicManager gm) {
        long gid = channel.getGuild().getIdLong();
        panelChannelIds.put(gid, channel.getIdLong());

        if (!panelMessageIds.containsKey(gid)) {
            channel.sendMessageEmbeds(buildEmbed(gm.player, gm.scheduler.getQueue()).build())
                    .queue(msg -> panelMessageIds.put(gid, msg.getIdLong()));
        } else {
            updateNowPlaying(channel.getGuild(), gm.player, gm.scheduler.getQueue());
        }
    }

    public static void updateNowPlaying(Guild guild, AudioPlayer player, BlockingQueue<AudioTrack> queue) {
        Long chId = panelChannelIds.get(guild.getIdLong());
        Long msgId = panelMessageIds.get(guild.getIdLong());
        if (chId == null || msgId == null) return;

        var tc = guild.getTextChannelById(chId);
        if (tc == null) return;

        tc.editMessageEmbedsById(msgId, buildEmbed(player, queue).build())
                .queue(null, err -> {
                    tc.sendMessageEmbeds(buildEmbed(player, queue).build())
                            .queue(m -> panelMessageIds.put(guild.getIdLong(), m.getIdLong()));
                });
    }

    private static EmbedBuilder buildEmbed(AudioPlayer player, BlockingQueue<AudioTrack> queue) {
        var eb = new EmbedBuilder()
                .setColor(new Color(0xF1C40F))
                .setTitle("🎛️ jjajangbot_v2 패널");

        AudioTrack cur = player.getPlayingTrack();
        if (cur != null) {
            eb.addField("재생 중", cur.getInfo().title, false);
            eb.addField("상태", player.isPaused() ? "⏸️ 일시정지" : "▶️ 재생", true);
        } else {
            eb.addField("재생 중", "없음", false);
        }

        if (!queue.isEmpty()) {
            var sb = new StringBuilder();
            int i = 1;
            for (AudioTrack t : queue) {
                sb.append(i++).append(". ").append(t.getInfo().title).append("\n");
                if (i > 10) { sb.append("…"); break; }
            }
            eb.addField("대기열", sb.toString(), false);
        } else {
            eb.addField("대기열", "비어있음", false);
        }

        eb.setFooter("/play <검색어 또는 링크>  |  /skip  /pause  /resume  /stop");
        return eb;
    }
}
