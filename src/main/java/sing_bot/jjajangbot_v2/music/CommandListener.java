package sing_bot.jjajangbot_v2.music;

import sing_bot.jjajangbot_v2.ui.PlayerUI;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class CommandListener extends ListenerAdapter {

    @Value("${discord.panel-channel-id:}")
    private String panelChannelId;

    private static final String MENU_SEARCH = "player:searchSelect";
    private static final int QUEUE_PAGE_SIZE = 10;

    // 길드별 패널 메시지 ID 저장
    private final Map<Long, Long> panelMessageIds = new ConcurrentHashMap<>();

    // ====== 슬래시 커맨드 등록 ======
    public void registerSlashCommands(JDA jda, String guildId) {
        var panel = Commands.slash("panel", "플레이어 UI 패널 표시/갱신");
        var play  = Commands.slash("play", "트랙 재생").addOption(OptionType.STRING, "query", "URL 또는 검색어", true);
        var join  = Commands.slash("join", "내가 있는 음성 채널로 봇을 초대");
        var leave = Commands.slash("leave", "봇이 음성 채널에서 나가기");
        var search= Commands.slash("search", "제목으로 검색해서 선택 재생")
                .addOption(OptionType.STRING, "title", "노래 제목", true);
        var queue = Commands.slash("queue", "현재 재생목록(큐) 보기")
                .addOptions(new OptionData(OptionType.INTEGER, "page", "페이지 번호 (기본 1)").setRequired(false));

        if (guildId != null && !guildId.isBlank() && jda.getGuildById(guildId) != null) {
            jda.getGuildById(guildId).updateCommands()
                    .addCommands(panel, play, join, leave, search, queue)
                    .queue();
        } else {
            jda.updateCommands().addCommands(panel, play, join, leave, search, queue).queue();
        }
    }

    // ====== 봇 준비되면 길드별로 패널 자동 표시 ======
    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("✅ JDA Ready: " + event.getJDA().getSelfUser().getAsTag());
        for (var guild : event.getJDA().getGuilds()) {
            TextChannel ch = resolvePreferredChannel(event.getJDA(), guild, null);
            upsertPanel(ch);
        }
    }

    // ====== 슬래시 커맨드 처리 ======
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent e) {
        switch (e.getName()) {
            case "panel" -> showOrRefreshPanel(e);
            case "play"  -> handlePlayCommand(e);
            case "join"  -> handleJoin(e);
            case "leave" -> {
                e.getGuild().getAudioManager().closeAudioConnection();
                upsertPanel(resolvePreferredChannel(e.getJDA(), e.getGuild(), e.getChannel()));
                sendTemp(e.getChannel(), "👋 나갔습니다");
            }
            case "search" -> handleSearch(e);
            case "queue"  -> handleQueue(e);
        }
    }

    private void showOrRefreshPanel(SlashCommandInteractionEvent e) {
        TextChannel ch = resolvePreferredChannel(e.getJDA(), e.getGuild(), e.getChannel());
        upsertPanel(ch);
        e.reply("패널을 갱신했습니다.").setEphemeral(true).queue();
    }

    private void handlePlayCommand(SlashCommandInteractionEvent e) {
        String raw = e.getOption("query", OptionMapping::getAsString);
        joinIfMemberInVoice(e);
        e.deferReply().queue(); // ACK만, 안내는 채널로(10초 삭제)

        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            enqueue(e, raw, e.getGuild());
            return;
        }
        String searchQ = normalizeForSearch(raw);
        PlayerManager.getInstance().search(searchQ, 5, tracks -> {
            var best = pickBestMatch(tracks, raw);
            if (best == null) { sendTemp(e.getChannel(), "검색 결과 없음"); return; }
            String url = best.getInfo().uri != null ? best.getInfo().uri : searchQ;
            enqueue(e, url, e.getGuild());
        }, err -> sendTemp(e.getChannel(), "검색 실패: " + err.getMessage()));
    }

    private void handleSearch(SlashCommandInteractionEvent e) {
        String title = e.getOption("title", OptionMapping::getAsString);
        e.deferReply(true).queue(); // ephemeral

        String q = "ytsearch:\\\"" + title.replace("\"","") + "\\\"";
        PlayerManager.getInstance().search(q, 5, tracks -> {
            if (tracks.isEmpty()) { e.getHook().sendMessage("검색 결과 없음").queue(); return; }
            StringSelectMenu.Builder menu = StringSelectMenu.create(MENU_SEARCH).setPlaceholder("재생할 곡 선택");
            int i = 1;
            for (var t : tracks) {
                var info = t.getInfo();
                String label = (i++) + ". " + safe(info.title, 90);
                String value = info.uri != null ? info.uri : info.identifier;
                String desc  = safe(info.author, 90);
                menu.addOption(label, value, desc);
            }
            e.getHook().sendMessage("🔎 검색 결과 (최대 5개)")
                    .setComponents(ActionRow.of(menu.build()))
                    .queue();
        }, err -> e.getHook().sendMessage("검색 실패: " + err.getMessage()).queue());
    }

    private void enqueue(SlashCommandInteractionEvent e, String url, net.dv8tion.jda.api.entities.Guild guild) {
        PlayerManager.getInstance().loadAndPlay(guild, url, new PlayerManager.TrackLoadResultHandler() {
            @Override public void onLoaded(AudioTrack t) {
                var st = PlayerState.of(guild.getIdLong());
                st.lastTitle = t.getInfo().title; st.lastSubtitle = t.getInfo().author;

                // 패널 업데이트
                TextChannel ch = resolvePreferredChannel(e.getJDA(), guild, e.getChannel());
                upsertPanel(ch);

                // 10초 후 삭제 안내
                sendTemp(e.getChannel(), "재생 대기열 추가: **" + t.getInfo().title + "**");
            }
            @Override public void onNoMatches() { sendTemp(e.getChannel(), "결과 없음"); }
            @Override public void onFailed(Throwable t) { sendTemp(e.getChannel(), "로드 실패: " + t.getMessage()); }
        });
    }

    private void joinIfMemberInVoice(SlashCommandInteractionEvent e) {
        var m = e.getMember();
        if (m != null && m.getVoiceState() != null && m.getVoiceState().inAudioChannel()) {
            e.getGuild().getAudioManager().openAudioConnection((VoiceChannel) m.getVoiceState().getChannel());
        }
    }

    private String normalizeForSearch(String q) {
        if (q.startsWith("http://") || q.startsWith("https://")) return q;
        return "ytsearch:\\\"" + q.replace("\"","") + "\\\"";
    }

    private AudioTrack pickBestMatch(java.util.List<AudioTrack> candidates, String query) {
        if (candidates.isEmpty()) return null;
        if (query == null) return candidates.get(0);
        String q = query.toLowerCase();
        for (var t: candidates) if (t.getInfo().title != null && t.getInfo().title.equalsIgnoreCase(query)) return t;
        for (var t: candidates) if (t.getInfo().title != null && t.getInfo().title.toLowerCase().contains(q)) return t;
        return candidates.get(0);
    }

    // ===== 버튼 인터랙션 =====
    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent e) {
        var st = PlayerState.of(e.getGuild().getIdLong());
        var gm = PlayerManager.getInstance().getGuildMusicManager(e.getGuild());

        switch (e.getComponentId()) {
            case PlayerUI.BTN_PLAYPAUSE -> {
                st.paused = !st.paused;
                gm.player.setPaused(st.paused);
                upsertPanel(resolvePreferredChannel(e.getJDA(), e.getGuild(), e.getChannel()));
                sendTemp(e.getChannel(), st.paused ? "⏸ 일시정지" : "▶ 재생");
                e.deferEdit().queue();
            }
            case PlayerUI.BTN_SKIP -> {
                gm.scheduler.nextTrack();
                upsertPanel(resolvePreferredChannel(e.getJDA(), e.getGuild(), e.getChannel()));
                sendTemp(e.getChannel(), "⏭ 스킵");
                e.deferEdit().queue();
            }
            case PlayerUI.BTN_STOP -> {
                gm.player.stopTrack();
                gm.scheduler.queue.clear();
                upsertPanel(resolvePreferredChannel(e.getJDA(), e.getGuild(), e.getChannel()));
                sendTemp(e.getChannel(), "⏹ 정지 & 큐 비움");
                e.deferEdit().queue();
            }
            case PlayerUI.BTN_SEARCH -> {
                var input = TextInput.create("query", "검색어 또는 URL", TextInputStyle.SHORT)
                        .setPlaceholder("예) newjeans hype boy")
                        .setRequired(true).build();
                e.replyModal(Modal.create("player:searchModal", "🔍 노래 검색").addActionRow(input).build()).queue();
            }
            case PlayerUI.BTN_AUTOPLAY -> {
                st.autoplay = !st.autoplay;
                upsertPanel(resolvePreferredChannel(e.getJDA(), e.getGuild(), e.getChannel()));
                sendTemp(e.getChannel(), st.autoplay ? "🔁 자동재생 ON" : "🔁 자동재생 OFF");
                e.deferEdit().queue();
            }
        }
    }

    // ===== 셀렉트 메뉴 =====
    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent e) {
        if (PlayerUI.MENU_VOLUME.equals(e.getComponentId())) {
            int v = Integer.parseInt(e.getValues().get(0));
            var st = PlayerState.of(e.getGuild().getIdLong());
            st.volume = v;
            PlayerManager.getInstance().getGuildMusicManager(e.getGuild()).player.setVolume(v);
            upsertPanel(resolvePreferredChannel(e.getJDA(), e.getGuild(), e.getChannel()));
            sendTemp(e.getChannel(), "🔊 볼륨: " + v + "%");
            e.deferEdit().queue();
            return;
        }
        if (MENU_SEARCH.equals(e.getComponentId())) {
            String chosen = e.getValues().get(0);
            var m = e.getMember();
            if (m != null && m.getVoiceState() != null && m.getVoiceState().inAudioChannel()) {
                e.getGuild().getAudioManager().openAudioConnection((VoiceChannel) m.getVoiceState().getChannel());
            }
            e.deferReply(true).queue(); // ephemeral ack
            PlayerManager.getInstance().loadAndPlay(e.getGuild(), chosen, new PlayerManager.TrackLoadResultHandler() {
                @Override public void onLoaded(AudioTrack t) {
                    var st = PlayerState.of(e.getGuild().getIdLong());
                    st.lastTitle = t.getInfo().title; st.lastSubtitle = t.getInfo().author;
                    upsertPanel(resolvePreferredChannel(e.getJDA(), e.getGuild(), e.getChannel()));
                    sendTemp(e.getChannel(), "▶ 재생: **" + t.getInfo().title + "**");
                    e.getHook().sendMessage("선택 완료").queue(msg -> msg.delete().queueAfter(3, TimeUnit.SECONDS));
                }
                @Override public void onNoMatches() { e.getHook().sendMessage("결과 없음").queue(m -> m.delete().queueAfter(3, TimeUnit.SECONDS)); }
                @Override public void onFailed(Throwable t) { e.getHook().sendMessage("실패: " + t.getMessage()).queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS)); }
            });
        }
    }

    // ===== 모달 제출 (검색 → 즉시 재생) =====
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent e) {
        if (!"player:searchModal".equals(e.getModalId())) return;
        String q = e.getValue("query").getAsString();
        var m = e.getMember();
        if (m != null && m.getVoiceState() != null && m.getVoiceState().inAudioChannel()) {
            e.getGuild().getAudioManager().openAudioConnection((VoiceChannel) m.getVoiceState().getChannel());
        }
        e.deferReply(true).queue(); // ephemeral ack
        PlayerManager.getInstance().loadAndPlay(e.getGuild(), normalizeForSearch(q), new PlayerManager.TrackLoadResultHandler() {
            @Override public void onLoaded(AudioTrack t) {
                var st = PlayerState.of(e.getGuild().getIdLong());
                st.lastTitle = t.getInfo().title; st.lastSubtitle = t.getInfo().author;
                upsertPanel(resolvePreferredChannel(e.getJDA(), e.getGuild(), e.getChannel()));
                sendTemp(e.getChannel(), "추가됨: **" + t.getInfo().title + "**");
                e.getHook().sendMessage("검색 완료").queue(msg -> msg.delete().queueAfter(3, TimeUnit.SECONDS));
            }
            @Override public void onNoMatches() { e.getHook().sendMessage("검색 결과 없음").queue(m -> m.delete().queueAfter(3, TimeUnit.SECONDS)); }
            @Override public void onFailed(Throwable t) { e.getHook().sendMessage("실패: " + t.getMessage()).queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS)); }
        });
    }

    // ===== /queue (재생목록 보기) =====
    private void handleQueue(SlashCommandInteractionEvent e) {
        var guild = e.getGuild();
        var gm = PlayerManager.getInstance().getGuildMusicManager(guild);

        var now = gm.player.getPlayingTrack();
        var all = new java.util.ArrayList<AudioTrack>();
        all.addAll(gm.scheduler.queue);

        int page = e.getOption("page", 1, OptionMapping::getAsInt);
        if (page < 1) page = 1;
        int total = all.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) QUEUE_PAGE_SIZE));
        if (page > totalPages) page = totalPages;

        int from = (page - 1) * QUEUE_PAGE_SIZE;
        int to = Math.min(total, from + QUEUE_PAGE_SIZE);
        var slice = all.subList(from, to);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🎶 재생목록 (" + page + "/" + totalPages + ")");
        if (now != null) {
            eb.addField("지금 재생", fmtLine(now, true), false);
        } else {
            eb.addField("지금 재생", "없음", false);
        }

        if (slice.isEmpty()) {
            eb.addField("다음 대기", total == 0 ? "대기열 비어있음" : "이 페이지에 표시할 항목이 없습니다.", false);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < slice.size(); i++) {
                int idx = from + i + 1;
                sb.append("`").append(idx).append(".` ").append(fmtLine(slice.get(i), false)).append("\n");
            }
            eb.addField("다음 대기 (" + (from + 1) + "–" + to + ")", sb.toString(), false);
        }

        var st = PlayerState.of(guild.getIdLong());
        eb.setFooter("자동재생: " + (st.autoplay ? "ON" : "OFF") + " · 볼륨: " + st.volume + "%");

        e.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private String fmtLine(AudioTrack t, boolean nowPlaying) {
        var info = t.getInfo();
        String title = safe(info.title, 70);
        String author = info.author != null ? info.author : "";
        String dur = info.isStream ? "LIVE" : formatMs(t.getDuration());
        String prefix = nowPlaying ? "▶ " : "";
        return prefix + "**" + title + "** — " + author + " `[" + dur + "]`";
    }

    private String formatMs(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }

    // ===== 패널 업서트(있으면 수정, 없으면 새로 생성) =====
    private void upsertPanel(TextChannel ch) {
        if (ch == null) return;
        long guildId = ch.getGuild().getIdLong();
        var st = PlayerState.of(guildId);
        var embed = PlayerUI.buildStatus(st.lastTitle, st.lastSubtitle, st.paused, st.autoplay, st.volume);
        Long msgId = panelMessageIds.get(guildId);

        if (msgId == null) {
            ch.sendMessageEmbeds(embed)
                    .setComponents(PlayerUI.rowMain(st.paused, st.autoplay),
                            PlayerUI.rowVolume(st.volume))
                    .queue(m -> panelMessageIds.put(guildId, m.getIdLong()), err -> {});
        } else {
            ch.editMessageEmbedsById(msgId, embed)
                    .setComponents(PlayerUI.rowMain(st.paused, st.autoplay),
                            PlayerUI.rowVolume(st.volume))
                    .queue(null, err -> {
                        // 메시지가 사라졌다면 새로 만듦
                        ch.sendMessageEmbeds(embed)
                                .setComponents(PlayerUI.rowMain(st.paused, st.autoplay),
                                        PlayerUI.rowVolume(st.volume))
                                .queue(m -> panelMessageIds.put(guildId, m.getIdLong()), e2 -> {});
                    });
        }
    }

    // ===== 패널 채널 결정 =====
    private TextChannel resolvePreferredChannel(@NotNull JDA jda,
                                                @NotNull net.dv8tion.jda.api.entities.Guild guild,
                                                MessageChannelUnion ctx) {
        if (panelChannelId != null && !panelChannelId.isBlank()) {
            TextChannel ch = jda.getTextChannelById(panelChannelId);
            if (ch != null) return ch;
        }
        try {
            if (ctx != null) return ctx.asTextChannel();
        } catch (Exception ignored) { }
        return guild.getTextChannels().stream().filter(TextChannel::canTalk).findFirst().orElse(null);
    }

    // ===== 10초 뒤 삭제되는 안내 메시지 =====
    private void sendTemp(MessageChannelUnion ch, String content) {
        if (ch == null) return;
        ch.sendMessage(content)
                .queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS), err -> {});
    }

    private void handleJoin(SlashCommandInteractionEvent e) {
        Member member = e.getMember();
        if (member == null || member.getVoiceState() == null || !member.getVoiceState().inAudioChannel()) {
            sendTemp(e.getChannel(), "먼저 음성 채널에 들어가 주세요.");
            return;
        }
        VoiceChannel vc = (VoiceChannel) member.getVoiceState().getChannel();
        e.getGuild().getAudioManager().openAudioConnection(vc);
        upsertPanel(resolvePreferredChannel(e.getJDA(), e.getGuild(), e.getChannel()));
        sendTemp(e.getChannel(), "연결 완료: `" + vc.getName() + "`");
    }

    // 문자열 안전 자르기
    private String safe(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
