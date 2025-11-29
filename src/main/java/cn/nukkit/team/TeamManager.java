package cn.nukkit.team;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.utils.TextFormat;

import java.util.*;

/**
 * 队伍管理器 - 用于管理游戏中的队伍系统
 */
public class TeamManager {
    private final HashMap<String, Team> teams = new HashMap<>();
    private final HashMap<String, Set<Player>> teamPlayers = new HashMap<>();  // 玩家
    private final Server server;

    public TeamManager() {
        this.server = Server.getInstance();
    }

    public boolean createTeam(String teamName, String displayName, TextFormat color) {
        return this.createTeam(teamName, displayName, color, 1);
    }

    public boolean createTeam(String teamName, String displayName, TextFormat color, int maxPlayers) {
        return this.createTeam(teamName, displayName, color, maxPlayers, false);
    }

    /**
     * 创建新队伍
     * @param teamName 队伍名称
     * @param displayName 显示名称
     * @param color 队伍颜色
     * @param maxPlayers 最大玩家数
     * @return 是否创建成功
     */
    public boolean createTeam(String teamName, String displayName, TextFormat color, int maxPlayers, boolean friendlyFire) {
        if (teamName == null || teamName.isEmpty() || teams.containsKey(teamName)) {
            return false;
        }

        Team team = new Team(teamName);
        team.setDisplayName(displayName);
        team.setColor(color);
        team.setPrefix(color.toString());
        team.setMaxPlayers(maxPlayers);
        team.setAllowFriendlyFire(friendlyFire);

        teams.put(teamName, team);
        teamPlayers.put(teamName, new HashSet<>());

        return true;
    }

    /**
     * 清除指定队伍
     * @param teamName 队伍名称
     * @return 是否清除成功
     */
    public boolean clearTeam(String teamName) {
        if (teamName == null || !teams.containsKey(teamName)) {
            return false;
        }

        // 移除队伍中的所有玩家
        Set<Player> players = teamPlayers.get(teamName);
        if (players != null) {
            for (Player player : new HashSet<>(players)) {
                removePlayerFromTeam(player, teamName);
            }
        }

        teams.remove(teamName);
        teamPlayers.remove(teamName);

        server.getLogger().info("队伍 " + teamName + " 已清除");
        return true;
    }

    /**
     * 清除所有队伍
     */
    public void clearAllTeams() {
        for (String teamName : new HashSet<>(teams.keySet())) {
            clearTeam(teamName);
        }
        server.getLogger().info("所有队伍已清除");
    }

    /**
     * 添加玩家到队伍
     * @param player 玩家
     * @param team 队伍对象
     * @return 是否添加成功
     */
    public boolean addPlayerToTeam(Player player, Team team) {
        String teamName = team.getName();
        if (player == null || team == null || !teams.containsKey(teamName)) {
            return false;
        }

        Set<Player> players = teamPlayers.get(teamName);

        // 检查队伍是否已满
        if (players.size() >= team.getMaxPlayers()) {
            player.sendMessage(TextFormat.RED + "队伍 " + teamName + " 已满！");
            return false;
        }

        // 先从其他队伍移除该玩家
        removePlayerFromAllTeams(player);

        // 添加到新队伍
        if (players.add(player)) {
            // 更新Player对象的team字段 - 修复：传递队伍名称而不是Team对象
            player.setTeam(teamName);

            // 更新玩家显示名称
            updatePlayerTeamDisplay(player, team);
            player.sendMessage(TextFormat.GREEN + "你已加入队伍: " + team.getColor() + team.getDisplayName());

            server.getLogger().info("玩家 " + player.getName() + " 加入队伍 " + teamName);
            return true;
        }

        return false;
    }

    /**
     * 添加玩家到队伍（通过队伍名称）
     * @param player 玩家
     * @param teamName 队伍名称
     * @return 是否添加成功
     */
    public boolean addPlayerToTeam(Player player, String teamName) {
        Team team = teams.get(teamName);
        if (team == null) {
            return false;
        }
        return addPlayerToTeam(player, team);
    }

    /**
     * 从队伍中移除玩家
     * @param player 玩家
     * @param teamName 队伍名称
     * @return 是否移除成功
     */
    public boolean removePlayerFromTeam(Player player, String teamName) {
        if (player == null || teamName == null || !teamPlayers.containsKey(teamName)) {
            return false;
        }

        Set<Player> players = teamPlayers.get(teamName);
        if (players.remove(player)) {
            // 清除Player对象的team字段
            if (teamName.equals(player.getTeam())) {
                player.clearTeam();
            }

            resetPlayerDisplay(player);
            player.sendMessage(TextFormat.YELLOW + "你已离开队伍: " + teamName);

            server.getLogger().info("玩家 " + player.getName() + " 离开队伍 " + teamName);
            return true;
        }

        return false;
    }

    /**
     * 从所有队伍中移除玩家
     * @param player 玩家
     */
    public void removePlayerFromAllTeams(Player player) {
        if (player == null) return;

        for (String teamName : teamPlayers.keySet()) {
            if (removePlayerFromTeam(player, teamName)) {
                break; // 一个玩家只能在一个队伍中，找到后就可以退出
            }
        }
    }

    /**
     * 获取玩家所在的队伍名称
     * @param player 玩家
     * @return 队伍名称，如果不在任何队伍中返回null
     */
    public String getPlayerTeamName(Player player) {
        if (player == null) return null;

        // 首先检查Player对象自身的team字段
        String playerTeamName = player.getTeam();
        if (playerTeamName != null && teamPlayers.containsKey(playerTeamName) &&
                teamPlayers.get(playerTeamName).contains(player)) {
            return playerTeamName;
        } else {
            // 如果数据不一致，清除玩家的team字段
            player.clearTeam();
        }

        // 回退到原来的查找逻辑
        for (Map.Entry<String, Set<Player>> entry : teamPlayers.entrySet()) {
            if (entry.getValue().contains(player)) {
                // 更新Player对象的team字段 - 修复：传递队伍名称字符串
                player.setTeam(entry.getKey());
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * 获取玩家所在的队伍对象
     * @param player 玩家
     * @return 队伍对象，如果不在任何队伍中返回null
     */
    public Team getPlayerTeam(Player player) {
        String teamName = getPlayerTeamName(player);
        return teamName != null ? teams.get(teamName) : null;
    }

    /**
     * 平均随机分配所有玩家到所有队伍
     * @return 分配结果
     */
    public TeamAssignmentResult assignBalancedRandomTeams() {
        Collection<Player> allPlayers = server.getOnlinePlayers().values();
        return assignBalancedRandomTeams(new ArrayList<>(allPlayers));
    }

    /**
     * 平均随机分配指定玩家列表到所有队伍
     * @param players 玩家列表
     * @return 分配结果
     */
    public TeamAssignmentResult assignBalancedRandomTeams(List<Player> players) {
        if (players == null || players.isEmpty()) {
            return new TeamAssignmentResult(false, "没有玩家可分配");
        }

        if (teams == null || teams.isEmpty()) {
            return new TeamAssignmentResult(false, "没有可用的队伍");
        }

        // 清除所有队伍的现有玩家
        clearAllTeamPlayers();

        // 打乱玩家顺序
        List<Player> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers);

        List<String> availableTeams = new ArrayList<>(teams.keySet());
        int totalPlayers = shuffledPlayers.size();
        int totalTeams = availableTeams.size();

        // 如果队伍数量大于玩家数量，只使用前 totalPlayers 个队伍
        if (totalTeams > totalPlayers) {
            availableTeams = availableTeams.subList(0, totalPlayers);
            totalTeams = totalPlayers;
            server.getLogger().debug("队伍数量多于玩家数量，调整为使用前 " + totalTeams + " 个队伍");
        }

        // 计算每个队伍的基础人数和剩余人数
        int basePlayersPerTeam = totalPlayers / totalTeams;
        int remainingPlayers = totalPlayers % totalTeams;

        server.getLogger().debug("玩家总数: " + totalPlayers + ", 队伍数: " + totalTeams +
                ", 每队基础人数: " + basePlayersPerTeam + ", 剩余: " + remainingPlayers);

        int playerIndex = 0;
        int assignmentCount = 0;

        // 分配玩家到每个队伍
        for (int i = 0; i < totalTeams && playerIndex < shuffledPlayers.size(); i++) {
            String teamName = availableTeams.get(i);
            Team team = teams.get(teamName);

            // 计算这个队伍应该分配的人数
            int playersForThisTeam = basePlayersPerTeam;
            if (i < remainingPlayers) {
                playersForThisTeam++; // 前几个队伍多分配一个玩家
            }

            // 确保不会超出玩家列表范围
            playersForThisTeam = Math.min(playersForThisTeam, shuffledPlayers.size() - playerIndex);

            server.getLogger().debug("队伍 " + teamName + " 分配 " + playersForThisTeam + " 名玩家");

            // 分配指定数量的玩家到这个队伍 - 修复：使用正确的重载方法
            for (int j = 0; j < playersForThisTeam && playerIndex < shuffledPlayers.size(); j++) {
                Player player = shuffledPlayers.get(playerIndex);
                if (addPlayerToTeam(player, team)) {  // 修复：传递Team对象而不是字符串
                    assignmentCount++;
                    server.getLogger().debug("玩家 " + player.getName() + " 分配到队伍 " + teamName);
                } else {
                    server.getLogger().debug("玩家 " + player.getName() + " 分配到队伍 " + teamName + " 失败");
                }
                playerIndex++;
            }
        }

        // 验证分配结果
        if (assignmentCount != totalPlayers) {
            server.getLogger().warning("分配异常: 期望分配 " + totalPlayers + " 名玩家，实际分配 " + assignmentCount + " 名");
        }

        String message = "平均分配完成！共分配 " + assignmentCount + " 名玩家到 " + totalTeams + " 个队伍";
        return new TeamAssignmentResult(true, message);
    }

    /**
     * 清除所有队伍的玩家但不删除队伍
     */
    private void clearAllTeamPlayers() {
        for (Set<Player> players : teamPlayers.values()) {
            for (Player player : new HashSet<>(players)) {
                removePlayerFromAllTeams(player);
            }
        }
    }

    /**
     * 获取分配统计信息
     */
    public String getDistributionStats() {
        StringBuilder stats = new StringBuilder();
        stats.append(TextFormat.YELLOW).append("=== 队伍分配统计 ===\n");

        for (String teamName : teams.keySet()) {
            Team team = teams.get(teamName);
            Set<Player> players = teamPlayers.get(teamName);
            int currentPlayers = players != null ? players.size() : 0;

            stats.append(team.getColor()).append(team.getDisplayName())
                    .append(TextFormat.WHITE).append(": ")
                    .append(currentPlayers).append("/").append(team.getMaxPlayers())
                    .append(" 玩家\n");
        }

        return stats.toString();
    }

    /**
     * 获取队伍状态
     * @param teamName 队伍名称
     * @return 队伍状态，如果队伍不存在返回null
     */
    public TeamStatus getTeamStatus(String teamName) {
        if (!teams.containsKey(teamName)) {
            return null;
        }

        Team team = teams.get(teamName);
        Set<Player> players = teamPlayers.get(teamName);
        boolean alive = isTeamAlive(teamName);

        return new TeamStatus(teamName, team.getDisplayName(), team.getColor(),
                players.size(), team.getMaxPlayers(), getPlayerNames(players), alive);
    }

    /**
     * 获取所有队伍状态
     * @return 所有队伍状态列表
     */
    public List<TeamStatus> getAllTeamsStatus() {
        List<TeamStatus> statusList = new ArrayList<>();
        for (String teamName : teams.keySet()) {
            statusList.add(getTeamStatus(teamName));
        }
        return statusList;
    }

    /**
     * 检查队伍是否存活（至少有一名在线且存活的玩家）
     * @param teamName 队伍名称
     * @return 是否存活
     */
    public boolean isTeamAlive(String teamName) {
        if (!teamPlayers.containsKey(teamName)) {
            return false;
        }

        Set<Player> players = teamPlayers.get(teamName);
        for (Player player : players) {
            if (player.isOnline() && player.isAlive() && !player.isClosed()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取存活的队伍数量
     * @return 存活队伍数量
     */
    public int getAliveTeamCount() {
        int count = 0;
        for (String teamName : teams.keySet()) {
            if (isTeamAlive(teamName)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 检查队伍是否存在
     * @param teamName 队伍名称
     * @return 是否存在
     */
    public boolean hasTeam(String teamName) {
        return teams.containsKey(teamName);
    }

    /**
     * 获取所有队伍名称
     * @return 队伍名称集合
     */
    public Set<String> getTeamNames() {
        return new HashSet<>(teams.keySet());
    }

    /**
     * 获取队伍中的玩家
     * @param teamName 队伍名称
     * @return 玩家集合，如果队伍不存在返回空集合
     */
    public Set<Player> getTeamPlayers(String teamName) {
        return teamPlayers.containsKey(teamName) ?
                new HashSet<>(teamPlayers.get(teamName)) : new HashSet<>();
    }

    /**
     * 广播消息到指定队伍
     * @param teamName 队伍名称
     * @param message 消息内容
     */
    public void broadcastToTeam(String teamName, String message) {
        if (!teamPlayers.containsKey(teamName)) return;

        Team team = teams.get(teamName);
        String formattedMessage = team.getColor() + "[队伍] " + TextFormat.WHITE + message;

        for (Player player : teamPlayers.get(teamName)) {
            if (player.isOnline()) {
                player.sendMessage(formattedMessage);
            }
        }
    }

    /**
     * 检查两个玩家是否在同一队伍
     * @param player1 玩家1
     * @param player2 玩家2
     * @return 是否在同一队伍
     */
    public boolean areTeammates(Player player1, Player player2) {
        if (player1 == null || player2 == null) return false;

        String team1 = getPlayerTeamName(player1);
        String team2 = getPlayerTeamName(player2);

        return team1 != null && team1.equals(team2);
    }

    /**
     * 获取同一队伍的所有队友（不包括自己）
     * @param player 玩家
     * @return 队友集合
     */
    public Set<Player> getTeammates(Player player) {
        Set<Player> teammates = new HashSet<>();
        String teamName = getPlayerTeamName(player);

        if (teamName != null) {
            Set<Player> teamPlayers = getTeamPlayers(teamName);
            for (Player teammate : teamPlayers) {
                if (!teammate.equals(player)) {
                    teammates.add(teammate);
                }
            }
        }

        return teammates;
    }

    // 私有方法
    private void updatePlayerTeamDisplay(Player player, Team team) {
        String displayName = team.getColor() + player.getName() + TextFormat.RESET;
        player.setDisplayName(displayName);
        player.setNameTag(team.getColor() + player.getName());
    }

    private void resetPlayerDisplay(Player player) {
        player.setDisplayName(player.getName());
        player.setNameTag(player.getName());
    }

    private Set<String> getPlayerNames(Set<Player> players) {
        Set<String> names = new HashSet<>();
        for (Player player : players) {
            names.add(player.getName());
        }
        return names;
    }

    /**
     * 队伍分配结果类
     */
    public static class TeamAssignmentResult {
        private final boolean success;
        private final String message;

        public TeamAssignmentResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    /**
     * 队伍状态信息类
     */
    public static class TeamStatus {
        private final String teamName;
        private final String displayName;
        private final TextFormat color;
        private final int playerCount;
        private final int maxPlayers;
        private final Set<String> playerNames;
        private final boolean alive;

        public TeamStatus(String teamName, String displayName, TextFormat color,
                          int playerCount, int maxPlayers, Set<String> playerNames, boolean alive) {
            this.teamName = teamName;
            this.displayName = displayName;
            this.color = color;
            this.playerCount = playerCount;
            this.maxPlayers = maxPlayers;
            this.playerNames = playerNames;
            this.alive = alive;
        }

        // Getter 方法
        public String getTeamName() { return teamName; }
        public String getDisplayName() { return displayName; }
        public TextFormat getColor() { return color; }
        public int getPlayerCount() { return playerCount; }
        public int getMaxPlayers() { return maxPlayers; }
        public Set<String> getPlayerNames() { return new HashSet<>(playerNames); }
        public boolean isAlive() { return alive; }

        @Override
        public String toString() {
            return color + displayName + TextFormat.RESET + " - " +
                    playerCount + "/" + maxPlayers + " 玩家 - " +
                    (alive ? TextFormat.GREEN + "存活" : TextFormat.RED + "淘汰");
        }
    }

    public TeamManager getInstance() {
        return this;
    }
}