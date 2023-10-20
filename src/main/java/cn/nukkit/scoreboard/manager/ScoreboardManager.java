package cn.nukkit.scoreboard.manager;

import cn.nukkit.Player;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.network.protocol.UpdateSoftEnumPacket;
import cn.nukkit.scoreboard.data.DisplaySlot;
import cn.nukkit.scoreboard.displayer.IScoreboardViewer;
import cn.nukkit.scoreboard.scoreboard.IScoreboard;
import cn.nukkit.scoreboard.scorer.EntityScorer;
import cn.nukkit.scoreboard.scorer.PlayerScorer;
import cn.nukkit.scoreboard.storage.IScoreboardStorage;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
public class ScoreboardManager implements IScoreboardManager {

    protected Map<String, IScoreboard> scoreboards = new HashMap<>();
    protected Map<DisplaySlot, IScoreboard> display = new EnumMap<>(DisplaySlot.class);
    protected Set<IScoreboardViewer> viewers = new HashSet<>();
    protected IScoreboardStorage storage;

    public ScoreboardManager(IScoreboardStorage storage) {
        this.storage = storage;
        this.read();
    }

    @Override
    public boolean addScoreboard(IScoreboard scoreboard) {
        scoreboards.put(scoreboard.getObjectiveName(), scoreboard);
        CommandEnum.SCOREBOARD_OBJECTIVES.updateSoftEnum(UpdateSoftEnumPacket.Type.ADD, scoreboard.getObjectiveName());
        return true;
    }

    @Override
    public boolean removeScoreboard(IScoreboard scoreboard) {
        return this.removeScoreboard(scoreboard.getObjectiveName());
    }

    @Override
    public boolean removeScoreboard(String objectiveName) {
        IScoreboard removed = scoreboards.get(objectiveName);
        if (removed == null) return false;
        scoreboards.remove(objectiveName);
        CommandEnum.SCOREBOARD_OBJECTIVES.updateSoftEnum(UpdateSoftEnumPacket.Type.REMOVE, objectiveName);
        viewers.forEach(viewer -> viewer.removeScoreboard(removed));
        display.forEach((slot, scoreboard) -> {
            if (scoreboard != null && scoreboard.getObjectiveName().equals(objectiveName)) {
                display.put(slot, null);
            }
        });
        return true;
    }

    @Override
    public IScoreboard getScoreboard(String objectiveName) {
        return scoreboards.get(objectiveName);
    }

    @Override
    public boolean containScoreboard(IScoreboard scoreboard) {
        return scoreboards.containsKey(scoreboard.getObjectiveName());
    }

    @Override
    public boolean containScoreboard(String name) {
        return scoreboards.containsKey(name);
    }

    @Override
    public IScoreboard getDisplaySlot(DisplaySlot slot) {
        return display.get(slot);
    }

    @Override
    public void setDisplay(DisplaySlot slot, @Nullable IScoreboard scoreboard) {
        IScoreboard old = display.put(slot, scoreboard);
        if (old != null) this.viewers.forEach(viewer -> old.removeViewer(viewer, slot));
        if (scoreboard != null) this.viewers.forEach(viewer -> scoreboard.addViewer(viewer, slot));
    }

    @Override
    public boolean addViewer(IScoreboardViewer viewer) {
        boolean added = this.viewers.add(viewer);
        if (added) this.display.forEach((slot, scoreboard) -> {
            if (scoreboard != null) scoreboard.addViewer(viewer, slot);
        });
        return added;
    }

    @Override
    public boolean removeViewer(IScoreboardViewer viewer) {
        boolean removed = viewers.remove(viewer);
        if (removed) this.display.forEach((slot, scoreboard) -> {
            if (scoreboard != null) scoreboard.removeViewer(viewer, slot);
        });
        return removed;
    }

    @Override
    public void onPlayerJoin(Player player) {
        this.addViewer(player);
        PlayerScorer scorer = new PlayerScorer(player);
        this.scoreboards.values().forEach(scoreboard -> {
            if (scoreboard.containLine(scorer)) {
                this.viewers.forEach(viewer -> viewer.updateScore(scoreboard.getLine(scorer)));
            }
        });
    }

    @Override
    public void beforePlayerQuit(Player player) {
        PlayerScorer scorer = new PlayerScorer(player);
        this.scoreboards.values().forEach(scoreboard -> {
            if (scoreboard.containLine(scorer)) {
                this.viewers.forEach(viewer -> viewer.removeLine(scoreboard.getLine(scorer)));
            }
        });
        this.removeViewer(player);
    }

    @Override
    public void onEntityDead(EntityLiving entity) {
        EntityScorer scorer = new EntityScorer(entity);
        this.scoreboards.forEach((s, scoreboard) -> {
            if (scoreboard.getLines().isEmpty()) return;
            scoreboard.removeLine(scorer);
        });
    }

    @Override
    public void save() {
        storage.removeAllScoreboard();
        storage.saveScoreboard(scoreboards.values());
        storage.saveDisplay(display);
    }

    @Override
    public void read() {
        new ArrayList<>(this.scoreboards.values()).forEach(this::removeScoreboard);
        this.display.forEach((slot, scoreboard) -> this.setDisplay(slot, null));

        scoreboards = storage.readScoreboard();
        storage.readDisplay().forEach((slot, objectiveName) -> {
            IScoreboard scoreboard = this.getScoreboard(objectiveName);
            if (scoreboard != null) {
                this.setDisplay(slot, scoreboard);
            }
        });
    }
}
