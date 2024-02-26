package cn.nukkit.scoreboard.scoreboard;

import cn.nukkit.network.protocol.types.DisplaySlot;
import cn.nukkit.network.protocol.types.SortOrder;
import cn.nukkit.scoreboard.displayer.IScoreboardViewer;
import cn.nukkit.scoreboard.scorer.FakeScorer;
import cn.nukkit.scoreboard.scorer.IScorer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class Scoreboard implements IScoreboard {
    protected String objectiveName;
    protected String displayName;
    protected String criteriaName;
    @Setter
    protected SortOrder sortOrder;

    protected Map<DisplaySlot, Set<IScoreboardViewer>> viewers = new EnumMap<>(DisplaySlot.class);
    protected Map<IScorer, IScoreboardLine> lines = new HashMap<>();

    {
        for (DisplaySlot slot : DisplaySlot.values()) {
            viewers.put(slot, new HashSet<>());
        }
    }

    public Scoreboard(@NotNull String objectiveName, @NotNull String displayName) {
        this(objectiveName, displayName, "dummy");
    }

    public Scoreboard(@NotNull String objectiveName, @NotNull String displayName, @NotNull String criteriaName) {
        this(objectiveName, displayName, criteriaName, SortOrder.ASCENDING);
    }

    public Scoreboard(@NotNull String objectiveName, @NotNull String displayName, @NotNull String criteriaName, SortOrder sortOrder) {
        this.objectiveName = Objects.requireNonNull(objectiveName);
        this.displayName = Objects.requireNonNull(displayName);
        this.criteriaName = Objects.requireNonNull(criteriaName);
        this.sortOrder = sortOrder;
    }

    @Override
    public Set<IScoreboardViewer> getAllViewers() {
        HashSet<IScoreboardViewer> all = new HashSet<>();
        this.viewers.values().forEach(all::addAll);
        return all;
    }

    @Override
    public Set<IScoreboardViewer> getViewers(DisplaySlot slot) {
        return this.viewers.get(slot);
    }

    @Override
    public boolean removeViewer(IScoreboardViewer viewer, DisplaySlot slot) {
        boolean removed = this.viewers.get(slot).remove(viewer);
        if (removed) viewer.hide(slot);
        return removed;
    }

    @Override
    public boolean addViewer(IScoreboardViewer viewer, DisplaySlot slot) {
        boolean added = this.viewers.get(slot).add(viewer);
        if (added) viewer.display(this, slot);
        return added;
    }

    @Override
    public boolean containViewer(IScoreboardViewer viewer, DisplaySlot slot) {
        return this.viewers.get(slot).contains(viewer);
    }

    @Override
    public @Nullable IScoreboardLine getLine(IScorer scorer) {
        return this.lines.get(scorer);
    }

    @Override
    public boolean addLine(IScoreboardLine line) {
        this.lines.put(line.getScorer(), line);
        this.updateScore(line);
        return true;
    }

    @Override
    public boolean addLine(IScorer scorer, int score) {
        return this.addLine(new ScoreboardLine(this, scorer, score));
    }

    @Override
    public boolean addLine(String text, int score) {
        FakeScorer fakeScorer = new FakeScorer(text);
        return this.addLine(new ScoreboardLine(this, fakeScorer, score));
    }

    @Override
    public boolean removeLine(IScorer scorer) {
        IScoreboardLine removed = lines.get(scorer);
        if (removed == null) return false;
        this.lines.remove(scorer);
        this.getAllViewers().forEach(viewer -> viewer.removeLine(removed));
        return true;
    }

    @Override
    public boolean removeAllLine(boolean send) {
        if (lines.isEmpty()) return false;
        if (send) {
            this.lines.keySet().forEach(this::removeLine);
        } else {
            this.lines.clear();
        }
        return true;
    }

    @Override
    public boolean containLine(IScorer scorer) {
        return this.lines.containsKey(scorer);
    }

    @Override
    public void updateScore(IScoreboardLine update) {
        this.getAllViewers().forEach(viewer -> viewer.updateScore(update));
    }

    @Override
    public void resend() {
        this.getAllViewers().forEach(viewer -> viewer.removeScoreboard(this));

        this.viewers.forEach((slot, slotViewers) -> {
            slotViewers.forEach(slotViewer -> {
                slotViewer.display(this, slot);
            });
        });
    }

    @Override
    public void setLines(List<String> lines) {
        this.removeAllLine(false);
        AtomicInteger score = new AtomicInteger();
        lines.forEach(str -> {
            FakeScorer scorer = new FakeScorer(str);
            this.lines.put(scorer, new ScoreboardLine(this, scorer, score.getAndIncrement()));
        });
        this.resend();
    }

    @Override
    public void setLines(Collection<IScoreboardLine> lines) {
        this.removeAllLine(false);
        lines.forEach(line -> this.lines.put(line.getScorer(), line));
        this.resend();
    }
}
