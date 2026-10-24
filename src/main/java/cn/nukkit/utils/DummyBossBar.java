package cn.nukkit.utils;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.mob.EntityCreeper;
import cn.nukkit.network.protocol.*;

/**
 * DummyBossBar
 * ===============
 * @author boybook
 * Nukkit Project
 * ===============
 */
public class DummyBossBar {

    private final Player player;
    private final long bossBarId;

    private String text;
    private float length;
    private BossBarColor color;

    private static final int ADD = 1;
    private static final int ATTRIBUTES = 2;
    private static final int SHOW = 4;
    private static final int HEALTH = 8;
    private static final int TEXTURE = 16;
    private static final int NAME = 32;
    private static final int TITLE = 64;
    private static final int MOVE = 128;
    private static final int HIDE = 256;
    private static final int REMOVE = 512;
    private static final int CREATE = ADD | ATTRIBUTES | SHOW | HEALTH;
    private static final int DESTROY = HIDE | REMOVE;

    private int pendingPackets;
    private int sendingPackets;
    private int generation;
    private final int[] packetGenerations = new int[10];
    private boolean deferPackets;
    private String pendingShowText;
    private float pendingShowLength;
    private boolean hasAcceptedShow;
    private String acceptedText;
    private float acceptedLength;

    private DummyBossBar(Builder builder) {
        this.player = builder.player;
        this.bossBarId = builder.bossBarId;
        this.text = builder.text;
        this.length = builder.length;
        this.color = builder.color;
    }

    /**
     * Boss bar builder
     */
    public static class Builder {
        private final Player player;
        private final long bossBarId;

        private String text = "";
        private float length = 100;
        private BossBarColor color = null;

        public Builder(Player player) {
            this.player = player;
            //this.bossBarId = 1095216660480L + Utils.random.nextLong(0, Integer.MAX_VALUE);
            this.bossBarId = Entity.entityCount++;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder length(float length) {
            if (length >= 0 && length <= 100) this.length = length;
            return this;
        }

        public Builder color(BossBarColor color) {
            this.color = color;
            return this;
        }

        public DummyBossBar build() {
            return new DummyBossBar(this);
        }
    }

    /**
     * Get boss bar owner
     *
     * @return player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Get boss bar id
     *
     * @return boss bar id
     */
    public long getBossBarId() {
        return bossBarId;
    }

    /**
     * Get boss bar text
     *
     * @return current text
     */
    public String getText() {
        return text;
    }

    /**
     * Set the boss bar text and send it to player if changed
     *
     * @param text new text
     */
    public void setText(String text) {
        if (!this.text.equals(text)) {
            this.text = text;
            markPending(NAME | TITLE);
        }
        if (!deferPackets && (pendingPackets & (ADD | SHOW)) == 0) {
            retryGroup(NAME, TITLE);
        }
    }

    /**
     * Get boss bar length
     *
     * @return length
     */
    public float getLength() {
        return length;
    }

    /**
     * Set boss bar length
     *
     * @param length new length
     */
    public void setLength(float length) {
        if (this.length != length) {
            this.length = length;
            markPending(ATTRIBUTES | HEALTH);
        }
        if (!deferPackets && (pendingPackets & (ADD | SHOW)) == 0) {
            retryGroup(ATTRIBUTES, HEALTH);
        }
    }

    /**
     * @param color the boss bar color
     */
    public void setColor(BossBarColor color) {
        if (this.color == null || !this.color.equals(color)) {
            this.color = color;
            markPending(TEXTURE);
        }
        if (!deferPackets && (pendingPackets & (ADD | SHOW)) == 0) {
            retryPacket(TEXTURE);
        }
    }

    @Deprecated
    public void setColor(BlockColor blockColor) {
        try {
            throw new RuntimeException();
        }catch (Exception e) {
            Server.getInstance().getLogger().warning("Unsupported API usage: DummyBossBar.setColor(BlockColor)", e);
        }
    }

    @Deprecated
    public void setColor(int red, int green, int blue) {
        try {
            throw new RuntimeException();
        }catch (Exception e) {
            Server.getInstance().getLogger().warning("Unsupported API usage: DummyBossBar.setColor(int,int,int)", e);
        }
    }

    public BossBarColor getColor() {
        return this.color;
    }

    private boolean createBossEntity() {
        AddEntityPacket pkAdd = new AddEntityPacket();
        pkAdd.type = EntityCreeper.NETWORK_ID;
        pkAdd.entityUniqueId = bossBarId;
        pkAdd.entityRuntimeId = bossBarId;
        pkAdd.x = (float) player.x;
        pkAdd.y = (float) -10; // Below the bedrock
        pkAdd.z = (float) player.z;
        pkAdd.speedX = 0;
        pkAdd.speedY = 0;
        pkAdd.speedZ = 0;
        pkAdd.metadata = new EntityMetadata()
                // Default Metadata tags
                .putLong(Entity.DATA_FLAGS, 0)
                .putShort(Entity.DATA_AIR, 400)
                .putShort(Entity.DATA_MAX_AIR, 400)
                .putLong(Entity.DATA_LEAD_HOLDER_EID, -1)
                .putString(Entity.DATA_NAMETAG, text) // Set the entity name
                .putFloat(Entity.DATA_SCALE, 0); // And make it invisible
        return submit(ADD, pkAdd);
    }

    private boolean sendAttributes() {
        UpdateAttributesPacket pkAttributes = new UpdateAttributesPacket();
        pkAttributes.entityId = bossBarId;
        Attribute attr = Attribute.getAttribute(Attribute.MAX_HEALTH);
        attr.setMaxValue(100); // Max value - We need to change the max value first, or else the "setValue" will return a IllegalArgumentException
        attr.setValue(length); // Entity health
        pkAttributes.entries = new Attribute[]{attr};
        return submit(ATTRIBUTES, pkAttributes);
    }

    private boolean sendShowBossBar() {
        BossEventPacket pkBoss = new BossEventPacket();
        pkBoss.bossEid = bossBarId;
        pkBoss.type = BossEventPacket.TYPE_SHOW;
        pkBoss.title = pendingShowText;
        pkBoss.healthPercent = player.protocol >= 361 ? pendingShowLength / 100 : pendingShowLength;
        return submit(SHOW, pkBoss);
    }

    private boolean sendHideBossBar() {
        BossEventPacket pkBoss = new BossEventPacket();
        pkBoss.bossEid = bossBarId;
        pkBoss.type = BossEventPacket.TYPE_HIDE;
        return submit(HIDE, pkBoss);
    }

    private boolean sendSetBossBarTexture() {
        BossEventPacket pk = new BossEventPacket();
        pk.bossEid = this.bossBarId;
        pk.type = BossEventPacket.TYPE_TEXTURE;
        pk.color = this.color.ordinal();
        return submit(TEXTURE, pk);
    }

    private boolean sendSetBossBarTitle() {
        BossEventPacket pkBoss = new BossEventPacket();
        pkBoss.bossEid = bossBarId;
        pkBoss.type = BossEventPacket.TYPE_TITLE;
        pkBoss.title = text;
        pkBoss.healthPercent = player.protocol >= 361 ? this.length / 100 : this.length;
        return submit(TITLE, pkBoss);
    }

    private boolean sendSetBossBarLength() {
        if (player.protocol >= 361) { // Again, what the hell is this and where is the documentation?
            BossEventPacket pkBoss = new BossEventPacket();
            pkBoss.bossEid = bossBarId;
            pkBoss.type = BossEventPacket.TYPE_HEALTH_PERCENT;
            pkBoss.healthPercent = this.length / 100;
            return submit(HEALTH, pkBoss);
        }
        pendingPackets &= ~HEALTH;
        return true;
    }

    /**
     * Don't let the entity go too far from the player, or the BossBar will disappear.
     * Update boss entity's position when teleport and each 5s.
     */
    public void updateBossEntityPosition() {
        markPending(MOVE);
        if (!deferPackets && (pendingPackets & ADD) == 0) {
            retryPacket(MOVE);
        }
    }

    private boolean moveBossEntity() {
        MoveEntityAbsolutePacket pk = new MoveEntityAbsolutePacket();
        pk.eid = this.bossBarId;
        pk.x = this.player.x;
        pk.y = -10;
        pk.z = this.player.z;
        pk.headYaw = 0;
        pk.yaw = 0;
        pk.pitch = 0;
        return submit(MOVE, pk);
    }

    private boolean updateBossEntityNameTag() {
        SetEntityDataPacket pk = new SetEntityDataPacket();
        pk.eid = this.bossBarId;
        pk.metadata = new EntityMetadata().putString(Entity.DATA_NAMETAG, this.text);
        return submit(NAME, pk);
    }

    private boolean removeBossEntity() {
        RemoveEntityPacket pkRemove = new RemoveEntityPacket();
        pkRemove.eid = bossBarId;
        return submit(REMOVE, pkRemove);
    }

    /** Pending means refused by the sending API, not missing client acknowledgement. */
    public boolean hasPendingPackets() {
        return pendingPackets != 0;
    }

    /** Applies one frame and attempts each pending kind at most once on the player thread. */
    public boolean updateState(String text, float length, BossBarColor color) {
        boolean previousDefer = deferPackets;
        deferPackets = true;
        try {
            setText(text);
            setLength(length);
            setColor(color);
        } finally {
            deferPackets = previousDefer;
        }
        return retryPendingPackets();
    }

    /** Retries original kinds only; accepted stages are not resent. */
    public boolean retryPendingPackets() {
        if (sendingPackets != 0) return false;
        if (!retryGroup(HIDE, REMOVE) || !retryPacket(ADD)) return false;
        if ((pendingPackets & SHOW) != 0) {
            if (!retryPacket(ATTRIBUTES) || !retryPacket(MOVE) || !retryPacket(SHOW)) return false;
        }
        boolean accepted = retryGroup(NAME, TITLE);
        accepted &= retryGroup(ATTRIBUTES, HEALTH);
        accepted &= retryPacket(TEXTURE);
        accepted &= retryPacket(MOVE);
        return accepted && pendingPackets == 0;
    }

    private void markPending(int kinds) {
        pendingPackets |= kinds;
        for (int remaining = kinds; remaining != 0; remaining &= remaining - 1) {
            int bit = Integer.lowestOneBit(remaining);
            packetGenerations[Integer.numberOfTrailingZeros(bit)] = ++generation;
        }
    }

    private boolean retryGroup(int first, int second) {
        boolean accepted = retryPacket(first);
        accepted &= retryPacket(second);
        return accepted;
    }

    private void resetPending() {
        markPending(CREATE | DESTROY | NAME | TITLE | TEXTURE | MOVE);
        pendingPackets = 0;
    }

    private boolean retryPacket(int kind) {
        if ((pendingPackets & kind) == 0) return true;
        if (sendingPackets != 0) return false;
        return switch (kind) {
            case ADD -> createBossEntity();
            case ATTRIBUTES -> sendAttributes();
            case SHOW -> sendShowBossBar();
            case HEALTH -> sendSetBossBarLength();
            case TEXTURE -> sendSetBossBarTexture();
            case NAME -> updateBossEntityNameTag();
            case TITLE -> sendSetBossBarTitle();
            case MOVE -> moveBossEntity();
            case HIDE -> sendHideBossBar();
            case REMOVE -> removeBossEntity();
            default -> throw new IllegalArgumentException("Unknown boss bar packet kind");
        };
    }

    private boolean submit(int kind, DataPacket packet) {
        int index = Integer.numberOfTrailingZeros(kind);
        int sentGeneration = packetGenerations[index];
        float sentLength = kind == SHOW ? pendingShowLength : length;
        sendingPackets |= kind;
        try {
            if (player.dataPacket(packet)) {
                if (kind == SHOW || kind == TITLE) {
                    acceptedText = ((BossEventPacket) packet).title;
                }
                if (kind == SHOW || kind == HEALTH) acceptedLength = sentLength;
                if (kind == SHOW) hasAcceptedShow = true;
                if (packetGenerations[index] == sentGeneration) {
                    pendingPackets &= ~kind;
                    if (kind == SHOW) pendingShowText = null;
                }
            }
            return packetGenerations[index] == sentGeneration && (pendingPackets & kind) == 0;
        } finally {
            sendingPackets &= ~kind;
        }
    }

    public void create() {
        int pendingDestroy = pendingPackets & DESTROY;
        resetPending();
        markPending(pendingDestroy | CREATE | (color == null ? 0 : TEXTURE));
        pendingShowText = text;
        pendingShowLength = length;
        retryPendingPackets();
    }

    /** Once the player has teleported, resend Show BossBar. */
    public void reshow() {
        // A teleport refresh must not bypass a cancelled field update through SHOW.
        if ((pendingPackets & SHOW) == 0) {
            pendingShowText = hasAcceptedShow && (pendingPackets & TITLE) != 0 ? acceptedText : text;
            pendingShowLength = hasAcceptedShow && (pendingPackets & HEALTH) != 0 ? acceptedLength : length;
        }
        markPending(MOVE | SHOW | HEALTH);
        retryPendingPackets();
    }

    public void destroy() {
        resetPending();
        markPending(DESTROY);
        pendingShowText = null;
        retryPendingPackets();
    }
}
