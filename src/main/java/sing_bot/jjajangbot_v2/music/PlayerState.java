
package sing_bot.jjajangbot_v2.music;

import java.util.concurrent.ConcurrentHashMap;

public class PlayerState {
    private static final ConcurrentHashMap<Long, PlayerState> STATES = new ConcurrentHashMap<>();
    public static PlayerState of(long guildId) { return STATES.computeIfAbsent(guildId, k -> new PlayerState()); }

    public boolean paused = false;
    public boolean autoplay = false;
    public int volume = 100;
    public String lastTitle = null;
    public String lastSubtitle = null;
}
