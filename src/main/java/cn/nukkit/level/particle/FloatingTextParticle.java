package cn.nukkit.level.particle;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.*;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.SerializedImage;
import cn.nukkit.utils.Utils;
import com.google.common.base.Strings;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Created on 2015/11/21 by xtypr.
 * Package cn.nukkit.level.particle in project Nukkit .
 */
public class FloatingTextParticle extends Particle {

    private static final Skin EMPTY_SKIN = new Skin();
    private static final SerializedImage SKIN_DATA = SerializedImage.fromLegacy(new byte[8192]);
    private static final UUID SKIN_UUID = UUID.nameUUIDFromBytes(Binary.appendBytes(Skin.GEOMETRY_CUSTOM.getBytes(StandardCharsets.UTF_8), SKIN_DATA.data));

    static {
        EMPTY_SKIN.setSkinData(SKIN_DATA);
        EMPTY_SKIN.setSkinResourcePatch(Skin.GEOMETRY_CUSTOM);
        EMPTY_SKIN.setSkinId(SKIN_UUID + ".FloatingText");
        EMPTY_SKIN.setCapeData(SerializedImage.EMPTY);
        EMPTY_SKIN.setCapeId("");
    }

    protected final Level level;
    protected UUID uuid = UUID.randomUUID();
    protected long entityId = -1;
    protected boolean invisible = false;
    protected EntityMetadata metadata = new EntityMetadata();

    public FloatingTextParticle(Location location, String title) {
        this(location, title, null);
    }

    public FloatingTextParticle(Location location, String title, String text) {
        this(location.getLevel(), location, title, text);
    }

    public FloatingTextParticle(Vector3 pos, String title) {
        this(pos, title, null);
    }

    public FloatingTextParticle(Vector3 pos, String title, String text) {
        this(null, pos, title, text);
    }

    private FloatingTextParticle(Level level, Vector3 pos, String title, String text) {
        super(pos.x, pos.y, pos.z);
        this.level = level;

        long flags = (
                65536L
        );
        metadata.putLong(Entity.DATA_FLAGS, flags)
                .putLong(Entity.DATA_LEAD_HOLDER_EID, -1)
                .putFloat(Entity.DATA_SCALE, 0.01f)
                .putFloat(Entity.DATA_BOUNDING_BOX_HEIGHT, 0.01f)
                .putFloat(Entity.DATA_BOUNDING_BOX_WIDTH, 0.01f)
                .putBoolean(Entity.DATA_FLAG_IMMOBILE, true)
                .putBoolean(Entity.DATA_FLAG_CAN_SHOW_NAMETAG, true)
                .putBoolean(Entity.DATA_ALWAYS_SHOW_NAMETAG, true);
        if (!Strings.isNullOrEmpty(title)) {
            metadata.putString(Entity.DATA_NAMETAG, title);
        }
        if (!Strings.isNullOrEmpty(text)) {
            metadata.putString(Entity.DATA_SCORE_TAG, text);
        }
    }

    public String getText() {
        return metadata.getString(Entity.DATA_SCORE_TAG);
    }

    public void setText(String text) {
        this.metadata.putString(Entity.DATA_SCORE_TAG, text);
        sendMetadata();
    }

    public String getTitle() {
        return metadata.getString(Entity.DATA_NAMETAG);
    }

    public void setTitle(String title) {
        this.metadata.putString(Entity.DATA_NAMETAG, title);
        sendMetadata();
    }

    private void sendMetadata() {
        if (level != null) {
            SetEntityDataPacket packet = new SetEntityDataPacket();
            packet.eid = entityId;
            packet.metadata = metadata;
            level.addChunkPacket(getChunkX(), getChunkZ(), packet);
        }
    }

    public boolean isInvisible() {
        return invisible;
    }

    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
    }

    public void setInvisible() {
        this.setInvisible(true);
    }

    public long getEntityId() {
        return entityId;
    }

    @Override
    public DataPacket[] mvEncode(int protocol) {
        ArrayList<DataPacket> packets = new ArrayList<>();
        if (this.entityId == -1) {
            this.entityId = 1095216660480L + Utils.random.nextLong(0, 0x7fffffffL);
        } else {
            RemoveEntityPacket pk = new RemoveEntityPacket();
            pk.eid = this.entityId;
            pk.protocol = protocol;
            pk.tryEncode();

            packets.add(pk);
        }

        if (!this.invisible) {
            PlayerListPacket.Entry[] entry = {new PlayerListPacket.Entry(uuid, entityId,
                    metadata.getString(Entity.DATA_NAMETAG), EMPTY_SKIN)};
            PlayerListPacket playerAdd = new PlayerListPacket();
            playerAdd.entries = entry;
            playerAdd.type = PlayerListPacket.TYPE_ADD;
            playerAdd.protocol = protocol;
            playerAdd.tryEncode();
            packets.add(playerAdd);

            AddPlayerPacket pk = new AddPlayerPacket();
            pk.uuid = uuid;
            pk.username = "";
            pk.entityUniqueId = this.entityId;
            pk.entityRuntimeId = this.entityId;
            pk.x = (float) this.x;
            pk.y = (float) (this.y - 0.75);
            pk.z = (float) this.z;
            pk.speedX = 0;
            pk.speedY = 0;
            pk.speedZ = 0;
            pk.yaw = 0;
            pk.pitch = 0;
            pk.metadata = this.metadata;
            pk.item = Item.get(Item.AIR);
            pk.protocol = protocol;
            pk.tryEncode();
            packets.add(pk);

            PlayerListPacket playerRemove = new PlayerListPacket();
            playerRemove.entries = entry;
            playerRemove.type = PlayerListPacket.TYPE_REMOVE;
            playerRemove.protocol = protocol;
            playerRemove.tryEncode();
            packets.add(playerRemove);
        }
        return packets.toArray(new DataPacket[0]);
    }
}
