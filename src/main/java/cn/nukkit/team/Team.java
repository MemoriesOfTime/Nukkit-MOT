package cn.nukkit.team;

import cn.nukkit.Player;
import cn.nukkit.utils.TextFormat;
import lombok.Getter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 队伍类
 */
public class Team {
    private final String name;
    /**
     * -- GETTER --
     *  获取显示名称
     *
     * @return 显示名称
     */
    @Getter
    private String displayName;
    /**
     * -- GETTER --
     *  获取队伍颜色
     *
     * @return 队伍颜色
     */
    @Getter
    private TextFormat color;
    /**
     * -- GETTER --
     *  获取前缀
     *
     * @return 前缀
     */
    @Getter
    private String prefix;
    /**
     * -- GETTER --
     *  获取后缀
     *
     * @return 后缀
     */
    @Getter
    private String suffix;
    private int maxPlayers;
    private boolean allowFriendlyFire;
    private boolean canSeeFriendlyInvisibles;

    /**
     * -- GETTER --
     *  获取是否允许复活
     */
    @Getter
    private boolean canRespawn;

    /**
     * -- GETTER --
     *  获取复活时间（秒）
     */
    @Getter
    private int respawnTime;

    /**
     * -- GETTER --
     *  获取无敌时间（秒）
     */
    @Getter
    private int invincibleTime;

    private final Set<Player> players;

    /**
     * 构造方法
     * @param name 队伍名称
     */
    public Team(String name) {
        this.name = name;
        this.displayName = name;
        this.color = TextFormat.WHITE;
        this.prefix = "";
        this.suffix = "";
        this.maxPlayers = Integer.MAX_VALUE;
        this.allowFriendlyFire = true;
        this.players = new HashSet<>();
    }

    /**
     * 获取队伍名称
     * @return 队伍名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置显示名称
     * @param displayName 显示名称
     * @return 当前队伍实例
     */
    public Team setDisplayName(String displayName) {
        this.displayName = displayName != null ? displayName : "";
        return this;
    }

    /**
     * 设置队伍颜色
     * @param color 队伍颜色
     * @return 当前队伍实例
     */
    public Team setColor(TextFormat color) {
        this.color = color != null ? color : TextFormat.WHITE;
        return this;
    }

    /**
     * 设置前缀
     * @param prefix 前缀
     * @return 当前队伍实例
     */
    public Team setPrefix(String prefix) {
        this.prefix = prefix != null ? prefix : "";
        return this;
    }

    /**
     * 设置后缀
     * @param suffix 后缀
     * @return 当前队伍实例
     */
    public Team setSuffix(String suffix) {
        this.suffix = suffix != null ? suffix : "";
        return this;
    }

    /**
     * 获取最大玩家数
     * @return 最大玩家数
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * 设置最大玩家数
     * @param maxPlayers 最大玩家数
     * @return 当前队伍实例
     */
    public Team setMaxPlayers(int maxPlayers) {
        this.maxPlayers = Math.max(1, maxPlayers);
        return this;
    }

    /**
     * 是否允许友军伤害
     * @return 是否允许友军伤害
     */
    public boolean isAllowFriendlyFire() {
        return allowFriendlyFire;
    }

    /**
     * 设置是否允许友军伤害
     * @param allowFriendlyFire 是否允许友军伤害
     * @return 当前队伍实例
     */
    public Team setAllowFriendlyFire(boolean allowFriendlyFire) {
        this.allowFriendlyFire = allowFriendlyFire;
        return this;
    }

    /**
     * 添加玩家到队伍
     * @param player 玩家
     * @return 是否添加成功
     */
    public boolean addPlayer(Player player) {
        if (player == null || players.size() >= maxPlayers) {
            return false;
        }
        boolean success = players.add(player);
        if (success) {
            // 同步更新Player对象的team字段
            player.setTeam(this.name);
        }
        return success;
    }

    /**
     * 从队伍中移除玩家
     * @param player 玩家
     * @return 是否移除成功
     */
    public boolean removePlayer(Player player) {
        if (player == null) return false;

        boolean success = players.remove(player);
        if (success) {
            // 同步清除Player对象的team字段
            if (this.name.equals(player.getTeam())) {
                player.clearTeam();
            }
        }
        return success;
    }

    /**
     * 清空队伍中的所有玩家
     */
    public void clearPlayers() {
        for (Player player : players) {
            // 清除每个玩家的team字段
            if (this.name.equals(player.getTeam())) {
                player.clearTeam();
            }
        }
        players.clear();
    }
    /**
     * 检查玩家是否在队伍中
     * @param player 玩家
     * @return 是否在队伍中
     */
    public boolean hasPlayer(Player player) {
        return player != null && players.contains(player);
    }

    /**
     * 获取队伍中的所有玩家
     * @return 玩家集合
     */
    public Set<Player> getPlayers() {
        return new HashSet<>(players);
    }

    /**
     * 获取玩家数量
     * @return 玩家数量
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * 队伍是否已满
     * @return 是否已满
     */
    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    /**
     * 队伍是否为空
     * @return 是否为空
     */
    public boolean isEmpty() {
        return players.isEmpty();
    }

    /**
     * 获取格式化后的玩家名称
     * @param player 玩家
     * @return 格式化后的名称
     */
    public String getFormattedPlayerName(Player player) {
        if (player == null) return "";
        return color + prefix + player.getName() + suffix;
    }

    /**
     * 获取队伍信息字符串
     * @return 队伍信息
     */
    public String getInfoString() {
        return String.format("%s%s%s - %d/%d 玩家 - %s",
                color, displayName, TextFormat.RESET,
                players.size(), maxPlayers,
                allowFriendlyFire ? "友伤开启" : "友伤关闭");
    }

    /**
     * 检查队伍是否存活（至少有一名在线且存活的玩家）
     * @return 是否存活
     */
    public boolean isAlive() {
        for (Player player : players) {
            if (player.isOnline() && player.isAlive() && !player.isClosed()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取在线的玩家数量
     * @return 在线玩家数量
     */
    public int getOnlinePlayerCount() {
        int count = 0;
        for (Player player : players) {
            if (player.isOnline() && !player.isClosed()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取存活的玩家数量
     * @return 存活玩家数量
     */
    public int getAlivePlayerCount() {
        int count = 0;
        for (Player player : players) {
            if (player.isOnline() && player.isAlive() && !player.isClosed()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取队伍中所有在线玩家
     * @return 在线玩家集合
     */
    public Set<Player> getOnlinePlayers() {
        Set<Player> onlinePlayers = new HashSet<>();
        for (Player player : players) {
            if (player.isOnline() && !player.isClosed()) {
                onlinePlayers.add(player);
            }
        }
        return onlinePlayers;
    }

    /**
     * 获取队伍中所有存活玩家
     * @return 存活玩家集合
     */
    public Set<Player> getAlivePlayers() {
        Set<Player> alivePlayers = new HashSet<>();
        for (Player player : players) {
            if (player.isOnline() && player.isAlive() && !player.isClosed()) {
                alivePlayers.add(player);
            }
        }
        return alivePlayers;
    }

    /**
     * 向队伍中所有玩家发送消息
     * @param message 消息内容
     */
    public void broadcastMessage(String message) {
        String formattedMessage = color + "[" + displayName + "] " + TextFormat.WHITE + message;
        for (Player player : getOnlinePlayers()) {
            player.sendMessage(formattedMessage);
        }
    }

    /**
     * 向队伍中所有玩家发送标题
     * @param title 标题
     * @param subtitle 副标题
     */
    public void broadcastTitle(String title, String subtitle) {
        for (Player player : getOnlinePlayers()) {
            player.sendTitle(title, subtitle);
        }
    }

    /**
     * 检查玩家是否可以攻击队友（基于友军伤害设置）
     * @param attacker 攻击者
     * @param target 目标
     * @return 是否可以攻击
     */
    public boolean canAttackTeammate(Player attacker, Player target) {
        if (attacker == null || target == null) return true;
        return allowFriendlyFire || !hasPlayer(attacker) || !hasPlayer(target) || !attacker.equals(target);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Team team = (Team) o;
        return Objects.equals(name, team.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Team{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", color=" + color +
                ", players=" + players.size() + "/" + maxPlayers +
                ", alive=" + isAlive() +
                '}';
    }

    /**
     * 创建队伍构建器
     * @param name 队伍名称
     * @return 队伍构建器
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * 队伍构建器 - 用于链式创建队伍
     */
    public static class Builder {
        private final Team team;

        public Builder(String name) {
            this.team = new Team(name);
        }

        public Builder displayName(String displayName) {
            team.setDisplayName(displayName);
            return this;
        }

        public Builder color(TextFormat color) {
            team.setColor(color);
            return this;
        }

        public Builder prefix(String prefix) {
            team.setPrefix(prefix);
            return this;
        }

        public Builder suffix(String suffix) {
            team.setSuffix(suffix);
            return this;
        }

        public Builder maxPlayers(int maxPlayers) {
            team.setMaxPlayers(maxPlayers);
            return this;
        }

        public Builder allowFriendlyFire(boolean allowFriendlyFire) {
            team.setAllowFriendlyFire(allowFriendlyFire);
            return this;
        }

        public Team build() {
            return team;
        }
    }
}