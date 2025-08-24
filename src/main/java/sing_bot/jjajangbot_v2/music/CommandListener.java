package sing_bot.jjajangbot_v2.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import sing_bot.jjajangbot_v2.ui.PlayerUI;

import java.awt.*;
import java.util.concurrent.TimeUnit;

@Component
public class CommandListener extends ListenerAdapter {

    private final PlayerManager playerManager;

    public CommandListener(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        event.getJDA().updateCommands().addCommands(
                Commands.slash("play", "노래 재생 (URL 또는 검색어)")
                        .addOption(OptionType.STRING, "query", "유튜브 URL 또는 검색어", true),
                Commands.slash("pause", "일시정지"),
                Commands.slash("resume", "재개"),
                Commands.slash("skip", "다음 곡"),
                Commands.slash("stop", "정지 및 큐 비우기"),
                Commands.slash("search", "YouTube Music에서 검색(상위 5개 안내)")
                        .addOption(OptionType.STRING, "query", "검색어", true),
                Commands.slash("queue", "현재 대기열 보기")
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "play" -> handlePlay(event);
            case "pause" -> handlePause(event);
            case "resume" -> handleResume(event);
            case "skip" -> handleSkip(event);
            case "stop" -> handleStop(event);
            case "search" -> handleSearch(event);
            case "queue" -> handleQueue(event);
        }
    }

    private void handlePlay(SlashCommandInteractionEvent event) {
        var query = event.getOption("query").getAsString();
        Member member = event.getMember();
        if (member == null) { event.reply("❌ 멤버 정보를 가져올 수 없어요.").setEphemeral(true).queue(); return; }

        AudioChannel voice = member.getVoiceState() != null ? member.getVoiceState().getChannel() : null;
        if (voice == null) {
            event.reply("🔈 먼저 음성 채널에 들어가 주세요.").setEphemeral(true).queue();
            return;
        }

        var guild = event.getGuild();
        if (guild == null) { event.reply("❌ 길드 정보가 없어요.").setEphemeral(true).queue(); return; }

        var am = guild.getAudioManager();
        if (!am.isConnected()) {
            if (!guild.getSelfMember().hasPermission(voice, Permission.VOICE_CONNECT)) {
                event.reply("❌ 해당 채널에 접속 권한이 없어요.").setEphemeral(true).queue();
                return;
            }
            am.openAudioConnection(voice);
        }

        event.reply("⏳ 로딩 중…").queue(hook -> hook.deleteOriginal().queueAfter(2, TimeUnit.SECONDS));

        var tc = event.getChannel().asTextChannel();
        playerManager.loadAndPlay(tc, query);

        var gm = playerManager.getGuildMusicManager(guild);
        PlayerUI.ensurePanel(tc, gm);
    }

    private void handlePause(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;
        var gm = playerManager.getGuildMusicManager(guild);
        gm.player.setPaused(true);
        event.reply("⏸️ 일시정지").queue(h -> h.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
        PlayerUI.updateNowPlaying(guild, gm.player, gm.scheduler.getQueue());
    }

    private void handleResume(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;
        var gm = playerManager.getGuildMusicManager(guild);
        gm.player.setPaused(false);
        event.reply("▶️ 재개").queue(h -> h.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
        PlayerUI.updateNowPlaying(guild, gm.player, gm.scheduler.getQueue());
    }

    private void handleSkip(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;
        var gm = playerManager.getGuildMusicManager(guild);
        gm.scheduler.nextTrack();
        event.reply("⏭️ 스킵").queue(h -> h.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
    }

    private void handleStop(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;
        var gm = playerManager.getGuildMusicManager(guild);
        gm.player.stopTrack();
        gm.scheduler.getQueue().clear();
        event.reply("⏹️ 정지 및 큐 비움").queue(h -> h.deleteOriginal().queueAfter(10, TimeUnit.SECONDS));
        PlayerUI.updateNowPlaying(guild, gm.player, gm.scheduler.getQueue());
    }

    private void handleSearch(SlashCommandInteractionEvent event) {
        String query = event.getOption("query").getAsString();
        var eb = new EmbedBuilder()
                .setTitle("🔎 검색 안내")
                .setDescription("`/play " + query + "` 로 재생해 보세요.\n문제되는 영상은 자동으로 **YouTube Music**에서 다시 찾습니다.")
                .setColor(new Color(0x5865F2));
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void handleQueue(SlashCommandInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;
        var gm = playerManager.getGuildMusicManager(guild);

        var builder = new StringBuilder();
        int i = 1;
        for (AudioTrack t : gm.scheduler.getQueue()) {
            builder.append(i++).append(". ").append(t.getInfo().title).append("\n");
            if (i > 15) { builder.append("…"); break; }
        }
        if (builder.length() == 0) builder.append("대기열이 비었습니다.");

        event.replyEmbeds(new EmbedBuilder()
                        .setTitle("📜 재생 목록")
                        .setDescription(builder.toString())
                        .setColor(0x2ECC71)
                        .build())
                .queue(h -> h.deleteOriginal().queueAfter(15, TimeUnit.SECONDS));
    }
}
