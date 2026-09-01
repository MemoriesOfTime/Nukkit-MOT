package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.utils.Utils;
import lombok.ToString;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Created by CreeperFace on 5.3.2017.
 */
@ToString
public class ClientboundMapItemDataPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CLIENTBOUND_MAP_ITEM_DATA_PACKET;
    public static final long[] EMPTY_LONGS = new long[0];

    public long[] eids = EMPTY_LONGS;

    public long mapId;
    @Deprecated
    public int update;
    public byte scale;
    public boolean isLocked;
    public int width;
    public int height;
    public int offsetX;
    public int offsetZ;

    public byte dimensionId;
    public BlockVector3 origin = new BlockVector3();

    public MapDecorator[] decorators = new MapDecorator[0];
    public MapTrackedObject[] trackedEntities = new MapTrackedObject[0];
    public int[] colors = new int[0];
    public BufferedImage image = null;

    public static final int TEXTURE_UPDATE = 0x02;
    public static final int DECORATIONS_UPDATE = 0x04;
    public static final int ENTITIES_UPDATE = 0x08;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityUniqueId(mapId);

        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.putByte(this.dimensionId);
            this.putBoolean(this.isLocked);
            this.putBlockVector3(this.gameVersion, this.origin);
            boolean hasEids = this.eids.length > 0;
            this.putBoolean(hasEids);
            if (hasEids) {
                this.putUnsignedVarInt(this.eids.length);
                for (long eid : this.eids) {
                    this.putEntityUniqueId(eid);
                }
            }
            this.putBoolean(true);
            this.putByte(this.scale);
            boolean hasTrackedObjects = this.trackedEntities.length > 0;
            this.putBoolean(hasTrackedObjects);
            if (hasTrackedObjects) {
                this.putUnsignedVarInt(this.trackedEntities.length);
                for (MapTrackedObject object : this.trackedEntities) {
                    this.putLInt(object.type);
                    boolean isEntity = object.type == MapTrackedObject.TYPE_ENTITY;
                    boolean isBlock = object.type == MapTrackedObject.TYPE_BLOCK;
                    this.putBoolean(isEntity);
                    this.putBoolean(isBlock);
                    if (isBlock) {
                        this.putBlockVector3(this.gameVersion, object.x, object.y, object.z);
                    } else if (isEntity) {
                        this.putEntityUniqueId(object.entityUniqueId);
                    } else {
                        throw new IllegalArgumentException("Unknown map object type " + object.type);
                    }
                }
            }
            boolean hasDecorations = this.decorators.length > 0;
            this.putBoolean(hasDecorations);
            if (hasDecorations) {
                this.putUnsignedVarInt(this.decorators.length);
                for (MapDecorator decorator : this.decorators) {
                    this.putByte(decorator.icon);
                    this.putByte(decorator.rotation);
                    this.putByte(decorator.offsetX);
                    this.putByte(decorator.offsetZ);
                    this.putString(decorator.label);
                    this.putLInt(decorator.color.getRGB());
                }
            }
            boolean hasTexture = this.image != null || this.colors.length > 0;
            this.putBoolean(hasTexture);
            if (hasTexture) {
                this.putVarInt(this.width);
            }
            this.putBoolean(hasTexture);
            if (hasTexture) {
                this.putVarInt(this.height);
            }
            this.putBoolean(hasTexture);
            if (hasTexture) {
                this.putVarInt(this.offsetX);
            }
            this.putBoolean(hasTexture);
            if (hasTexture) {
                this.putVarInt(this.offsetZ);
            }
            boolean hasColors = this.colors.length > 0 || this.image != null;
            this.putBoolean(hasColors);
            if (hasColors) {
                if (this.colors.length > 0) {
                    this.putUnsignedVarInt(this.colors.length);
                    for (int color : this.colors) {
                        this.putLInt(color);
                    }
                } else {
                    this.putUnsignedVarInt((long) this.width * this.height);
                    for (int y = 0; y < this.height; y++) {
                        for (int x = 0; x < this.width; x++) {
                            this.putLInt((int) Utils.toABGR(this.image.getRGB(x, y)));
                        }
                    }
                    this.image.flush();
                }
            }
        } else {
            int update = 0;
            if (eids.length > 0) {
                update |= ENTITIES_UPDATE;
            }
            if (decorators.length > 0 || trackedEntities.length > 0) {
                update |= DECORATIONS_UPDATE;
            }

            if (image != null || colors.length > 0) {
                update |= TEXTURE_UPDATE;
            }

            this.putUnsignedVarInt(update);
            this.putByte(this.dimensionId);
            if (protocol >= 354) {
                this.putBoolean(this.isLocked);
            }
            if (protocol >= ProtocolInfo.v1_19_20) {
                this.putBlockVector3(this.origin);
            }

            if ((update & ENTITIES_UPDATE) != 0) {
                this.putUnsignedVarInt(eids.length);
                for (long eid : eids) {
                    this.putEntityUniqueId(eid);
                }
            }
            if ((update & (ENTITIES_UPDATE | TEXTURE_UPDATE | DECORATIONS_UPDATE)) != 0) {
                this.putByte(this.scale);
            }

            if ((update & DECORATIONS_UPDATE) != 0) {
                this.putUnsignedVarInt(trackedEntities.length);
                for (MapTrackedObject object : trackedEntities) {
                    this.putLInt(object.type);
                    if (object.type == MapTrackedObject.TYPE_BLOCK) {
                        this.putBlockVector3(object.x, object.y, object.z);
                    } else if (object.type == MapTrackedObject.TYPE_ENTITY) {
                        this.putEntityUniqueId(object.entityUniqueId);
                    } else {
                        throw new IllegalArgumentException("Unknown map object type " + object.type);
                    }
                }

                this.putUnsignedVarInt(decorators.length);
                for (MapDecorator decorator : decorators) {
                    this.putByte(decorator.icon);
                    this.putByte(decorator.rotation);
                    this.putByte(decorator.offsetX);
                    this.putByte(decorator.offsetZ);
                    this.putString(decorator.label);
                    this.putUnsignedVarInt(decorator.color.getRGB());
                }
            }

            if ((update & TEXTURE_UPDATE) != 0) {
                this.putVarInt(width);
                this.putVarInt(height);
                this.putVarInt(offsetX);
                this.putVarInt(offsetZ);

                this.putUnsignedVarInt((long) width * height);

                if (image != null) {
                    for (int y = 0; y < width; y++) {
                        for (int x = 0; x < height; x++) {
                            this.putUnsignedVarInt(Utils.toABGR(this.image.getRGB(x, y)));
                        }
                    }

                    image.flush();
                } else if (colors.length > 0) {
                    this.putUnsignedVarInt(colors.length);
                    for (int color : colors) {
                        this.putUnsignedVarInt(color);
                    }
                }
            }
        }
    }

    public static class MapDecorator {
        public byte rotation;
        public byte icon;
        public byte offsetX;
        public byte offsetZ;
        public String label = "";
        public Color color;
    }

    public static class MapTrackedObject {
        public static final int TYPE_ENTITY = 0;
        public static final int TYPE_BLOCK = 1;

        public int type;
        public long entityUniqueId;

        public int x;
        public int y;
        public int z;
    }
}
