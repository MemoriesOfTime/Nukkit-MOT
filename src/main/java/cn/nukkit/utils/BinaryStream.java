package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.item.*;
import cn.nukkit.item.RuntimeItemMapping.LegacyEntry;
import cn.nukkit.item.RuntimeItemMapping.RuntimeEntry;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.network.LittleEndianByteBufInputStream;
import cn.nukkit.network.LittleEndianByteBufOutputStream;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.EntityLink;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.TextProcessingEventOrigin;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import com.google.common.base.Preconditions;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
import lombok.SneakyThrows;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

import static org.cloudburstmc.protocol.common.util.Preconditions.checkArgument;

/**
 * BinaryStream
 *
 * @author MagicDroidX
 * Nukkit Project
 */
public class BinaryStream {

    public int offset;
    private byte[] buffer;
    protected int count;

    private static final int MAX_ARRAY_SIZE = 2147483639;

    public BinaryStream() {
        this.buffer = new byte[32];
        this.offset = 0;
        this.count = 0;
    }

    public BinaryStream(int initialCapacity) {
        this.buffer = new byte[initialCapacity];
        this.offset = 0;
        this.count = 0;
    }

    public BinaryStream(byte[] buffer) {
        this(buffer, 0);
    }

    public BinaryStream(byte[] buffer, int offset) {
        this.buffer = buffer;
        this.offset = offset;
        this.count = buffer.length;
    }

    public void reuse() {
        this.offset = 0;
        this.count = 0;
    }

    public BinaryStream reset() {
        this.offset = 0;
        this.count = 0;
        return this;
    }

    public void setBuffer(byte[] buffer) {
        this.buffer = buffer;
        this.count = buffer == null ? -1 : buffer.length;
    }

    public void setBuffer(byte[] buffer, int offset) {
        this.setBuffer(buffer);
        this.setOffset(offset);
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public byte[] getBuffer() {
        return Arrays.copyOf(buffer, count);
    }

    public byte[] getBufferUnsafe() {
        return buffer;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public byte[] get() {
        return this.get(this.count - this.offset);
    }

    public byte[] get(int len) {
        if (len < 0) {
            this.offset = this.count - 1;
            return new byte[0];
        }
        len = Math.min(len, this.count - this.offset);
        this.offset += len;
        return Arrays.copyOfRange(this.buffer, this.offset - len, this.offset);
    }

    public void skip(int len) {
        if (len <= 0) {
            return;
        }
        this.offset += Math.min(len, this.count - this.offset);
    }

    public void put(byte[] bytes) {
        if (bytes == null) {
            return;
        }

        this.ensureCapacity(this.count + bytes.length);

        System.arraycopy(bytes, 0, this.buffer, this.count, bytes.length);
        this.count += bytes.length;
    }

    public long getLong() {
        return Binary.readLong(this.get(8));
    }

    public void putLong(long l) {
        this.put(Binary.writeLong(l));
    }

    public int getInt() {
        return Binary.readInt(this.get(4));
    }

    public void putInt(int i) {
        this.put(Binary.writeInt(i));
    }

    public long getLLong() {
        return Binary.readLLong(this.get(8));
    }

    public void putLLong(long l) {
        this.put(Binary.writeLLong(l));
    }

    public int getLInt() {
        return Binary.readLInt(this.get(4));
    }

    public void putLInt(int i) {
        this.put(Binary.writeLInt(i));
    }

    public int getShort() {
        return Binary.readShort(this.get(2));
    }

    public void putShort(int s) {
        this.put(Binary.writeShort(s));
    }

    public int getLShort() {
        return Binary.readLShort(this.get(2));
    }

    public void putLShort(int s) {
        this.put(Binary.writeLShort(s));
    }

    public float getFloat() {
        return getFloat(-1);
    }

    public float getFloat(int accuracy) {
        return Binary.readFloat(this.get(4), accuracy);
    }

    public void putFloat(float v) {
        this.put(Binary.writeFloat(v));
    }

    public float getLFloat() {
        return getLFloat(-1);
    }

    public float getLFloat(int accuracy) {
        return Binary.readLFloat(this.get(4), accuracy);
    }

    public void putLFloat(float v) {
        this.put(Binary.writeLFloat(v));
    }

    public int getTriad() {
        return Binary.readTriad(this.get(3));
    }

    public void putTriad(int triad) {
        this.put(Binary.writeTriad(triad));
    }

    public int getLTriad() {
        return Binary.readLTriad(this.get(3));
    }

    public void putLTriad(int triad) {
        this.put(Binary.writeLTriad(triad));
    }

    public boolean getBoolean() {
        return this.getByte() == 0x01;
    }

    public void putBoolean(boolean bool) {
        this.putByte((byte) (bool ? 1 : 0));
    }

    public byte getSingedByte() {
        return this.buffer[this.offset++];
    }

    public int getByte() {
        return this.buffer[this.offset++] & 0xff;
    }

    public void putByte(byte b) {
        this.put(new byte[]{b});
    }

    public void putByte(int b) {
        putByte((byte) b);
    }

    /**
     * Reads a list of Attributes from the stream.
     *
     * @return Attribute[]
     */
    public Attribute[] getAttributeList() throws Exception {
        List<Attribute> list = new ArrayList<>();
        long count = this.getUnsignedVarInt();

        for (int i = 0; i < count; ++i) {
            String name = this.getString();
            Attribute attr = Attribute.getAttributeByName(name);
            if (attr != null) {
                attr.setMinValue(this.getLFloat());
                attr.setValue(this.getLFloat());
                attr.setMaxValue(this.getLFloat());
                list.add(attr);
            } else {
                throw new Exception("Unknown attribute type \"" + name + '"');
            }
        }

        return list.toArray(new Attribute[0]);
    }

    /**
     * Writes a list of Attributes to the packet buffer using the standard format.
     */
    public void putAttributeList(Attribute[] attributes) {
        this.putUnsignedVarInt(attributes.length);
        for (Attribute attribute : attributes) {
            this.putString(attribute.getName());
            this.putLFloat(attribute.getMinValue());
            this.putLFloat(attribute.getValue());
            this.putLFloat(attribute.getMaxValue());
        }
    }

    public void putUUID(UUID uuid) {
        this.put(Binary.writeUUID(uuid));
    }

    public UUID getUUID() {
        return Binary.readUUID(this.get(16));
    }

    public void putSkin(Skin skin) {
        Server.mvw("BinaryStream#putSkin(Skin)");
        this.putSkin(ProtocolInfo.CURRENT_PROTOCOL, skin);
    }

    private static byte[] steveSkinDecoded;

    public void putSkin(int protocol, Skin skin) {
        this.putString(skin.getSkinId());

        if (protocol < ProtocolInfo.v1_13_0) {
            if (skin.isPersona()) { // Hack: Replace persona skins with steve skins for < 1.13 players to avoid invisible skins
                this.putByteArray(steveSkinDecoded != null ? steveSkinDecoded : (steveSkinDecoded = Base64.getDecoder().decode(Skin.STEVE_SKIN)));
                if (protocol >= ProtocolInfo.v1_2_13) {
                    this.putByteArray(skin.getCapeData().data);
                }
                this.putString("geometry.humanoid.custom");
                this.putString(Skin.STEVE_GEOMETRY_OLD);
            } else {
                this.putByteArray(skin.getSkinData().data);
                if (protocol >= ProtocolInfo.v1_2_13) {
                    this.putByteArray(skin.getCapeData().data);
                }
                this.putString(skin.isLegacySlim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom");
                this.putString(skin.getGeometryData());
            }
        } else {
            if (protocol >= ProtocolInfo.v1_16_210) {
                this.putString(skin.getPlayFabId());
            }
            this.putString(skin.getSkinResourcePatch());
            this.putImage(skin.getSkinData());

            List<SkinAnimation> animations = skin.getAnimations();
            this.putLInt(animations.size());
            for (SkinAnimation animation : animations) {
                this.putImage(animation.image);
                this.putLInt(animation.type);
                this.putLFloat(animation.frames);
                if (protocol >= ProtocolInfo.v1_16_100) {
                    this.putLInt(animation.expression);
                }
            }

            this.putImage(skin.getCapeData());
            this.putString(skin.getGeometryData());
            if (protocol >= ProtocolInfo.v1_17_30) {
                this.putString(skin.getGeometryDataEngineVersion());
            }
            this.putString(skin.getAnimationData());
            if (protocol < ProtocolInfo.v1_17_30) {
                this.putBoolean(skin.isPremium());
                this.putBoolean(skin.isPersona());
                this.putBoolean(skin.isCapeOnClassic());
            }
            this.putString(skin.getCapeId());
            this.putString(skin.getFullSkinId());
            if (protocol >= ProtocolInfo.v1_14_60) {
                this.putString(skin.getArmSize());
                this.putString(skin.getSkinColor());

                List<PersonaPiece> pieces = skin.getPersonaPieces();
                this.putLInt(pieces.size());
                for (PersonaPiece piece : pieces) {
                    this.putString(piece.id);
                    this.putString(piece.type);
                    this.putString(piece.packId);
                    this.putBoolean(piece.isDefault);
                    this.putString(piece.productId);
                }

                List<PersonaPieceTint> tints = skin.getTintColors();
                this.putLInt(tints.size());
                for (PersonaPieceTint tint : tints) {
                    this.putString(tint.pieceType);
                    List<String> colors = tint.colors;
                    this.putLInt(colors.size());
                    for (String color : colors) {
                        this.putString(color);
                    }
                }

                if (protocol >= ProtocolInfo.v1_17_30) {
                    this.putBoolean(skin.isPremium());
                    this.putBoolean(skin.isPersona());
                    this.putBoolean(skin.isCapeOnClassic());
                    this.putBoolean(skin.isPrimaryUser());
                    if (protocol >= ProtocolInfo.v1_19_63) {
                        this.putBoolean(skin.isOverridingPlayerAppearance());
                    }
                }
            }
        }
    }

    public void putImage(SerializedImage image) {
        this.putLInt(image.width);
        this.putLInt(image.height);
        this.putByteArray(image.data);
    }

    public SerializedImage getImage() {
        int width = this.getLInt();
        int height = this.getLInt();
        byte[] data = this.getByteArray();
        return new SerializedImage(width, height, data);
    }

    public SerializedImage getImage(int maxSize) {
        int width = this.getLInt();
        int height = this.getLInt();
        byte[] data = this.getByteArray(maxSize);
        return new SerializedImage(width, height, data);
    }

    public Skin getSkin() {
        Server.mvw("BinaryStream#getSkin()");
        return getSkin(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public Skin getSkin(int protocol) { // Can be used only with protocol >= 388
        Skin skin = new Skin();
        skin.setSkinId(this.getString());
        if (protocol >= ProtocolInfo.v1_16_210) {
            skin.setPlayFabId(this.getString());
        }
        skin.setSkinResourcePatch(this.getString());
        skin.setSkinData(this.getImage(Skin.SKIN_PERSONA_SIZE));

        int animationCount = this.getLInt();
        for (int i = 0; i < Math.min(animationCount, 1024); i++) {
            SerializedImage image = this.getImage(Skin.SKIN_128_128_SIZE);
            int type = this.getLInt();
            float frames = this.getLFloat();
            int expression = protocol >= ProtocolInfo.v1_16_100 ? this.getLInt() : 0;
            skin.getAnimations().add(new SkinAnimation(image, type, frames, expression));
        }

        skin.setCapeData(this.getImage(Skin.SINGLE_SKIN_SIZE));
        skin.setGeometryData(this.getString());
        if (protocol >= ProtocolInfo.v1_17_30) {
            skin.setGeometryDataEngineVersion(this.getString());
        }
        skin.setAnimationData(this.getString());
        if (protocol < ProtocolInfo.v1_17_30) {
            skin.setPremium(this.getBoolean());
            skin.setPersona(this.getBoolean());
            skin.setCapeOnClassic(this.getBoolean());
        }
        skin.setCapeId(this.getString());
        skin.setFullSkinId(this.getString());
        if (protocol >= ProtocolInfo.v1_14_60) {
            skin.setArmSize(this.getString());
            skin.setSkinColor(this.getString());

            int piecesLength = this.getLInt();
            for (int i = 0; i < Math.min(piecesLength, 1024); i++) {
                String pieceId = this.getString();
                String pieceType = this.getString();
                String packId = this.getString();
                boolean isDefault = this.getBoolean();
                String productId = this.getString();
                skin.getPersonaPieces().add(new PersonaPiece(pieceId, pieceType, packId, isDefault, productId));
            }

            int tintsLength = this.getLInt();
            for (int i = 0; i < Math.min(tintsLength, 1024); i++) {
                String pieceType = this.getString();
                List<String> colors = new ArrayList<>();
                int colorsLength = this.getLInt();
                for (int i2 = 0; i2 < Math.min(colorsLength, 1024); i2++) {
                    colors.add(this.getString());
                }
                skin.getTintColors().add(new PersonaPieceTint(pieceType, colors));
            }

            if (protocol >= ProtocolInfo.v1_17_30) {
                skin.setPremium(this.getBoolean());
                skin.setPersona(this.getBoolean());
                skin.setCapeOnClassic(this.getBoolean());
                skin.setPrimaryUser(this.getBoolean());
                if (protocol >= ProtocolInfo.v1_19_63) {
                    skin.setOverridingPlayerAppearance(this.getBoolean());
                }
            }
        }
        return skin;
    }

    private static final String MV_ORIGIN_NBT = "mv_origin_nbt";
    private static final String MV_ORIGIN_ID = "mv_origin_id";
    private static final String MV_ORIGIN_NAMESPACE = "mv_origin_namespace";
    private static final String MV_ORIGIN_META = "mv_origin_meta";

    public Item getSlot() {
        Server.mvw("BinaryStream#getSlot()");
        return this.getSlot(GameVersion.getLastVersion());
    }

    @Deprecated
    public Item getSlot(int protocolId) {
        return this.getSlot(GameVersion.byProtocol(protocolId, false));
    }

    public Item getSlot(GameVersion gameVersion) {
        int protocolId = gameVersion.getProtocol();
        if (protocolId >= ProtocolInfo.v1_16_220) {
            return this.getSlotNew(gameVersion);
        }

        int runtimeId = this.getVarInt();
        if (runtimeId == 0) {
            return Item.get(Item.AIR, 0, 0);
        }

        int auxValue = this.getVarInt();
        int damage = auxValue >> 8;
        if (damage == Short.MAX_VALUE) {
            damage = -1;
        }

        Integer id = null;
        String stringId = null;
        if (protocolId < ProtocolInfo.v1_16_100) {
            id = runtimeId;
        } else {
            RuntimeItemMapping mapping = RuntimeItems.getMapping(gameVersion);
            try {
                LegacyEntry legacyEntry = mapping.fromRuntime(runtimeId);
                id = legacyEntry.getLegacyId();
                if (legacyEntry.isHasDamage()) {
                    damage = legacyEntry.getDamage();
                }
            } catch (IllegalArgumentException e) {

            }

            if (id == null || !Utils.hasItemOrBlock(id)) {
                stringId = mapping.getNamespacedIdByNetworkId(runtimeId);
                if (stringId == null) {
                    throw new IllegalArgumentException("Unknown item: runtimeID=" + runtimeId + " protocol=" + protocolId);
                }
                id = null;
            }
        }

        int cnt = auxValue & 0xff;

        int nbtLen = this.getLShort();
        byte[] nbt = new byte[0];
        if (nbtLen < Short.MAX_VALUE) {
            nbt = this.get(nbtLen);
        } else if (nbtLen == 65535) {
            int nbtTagCount = (int) getUnsignedVarInt();
            int offset = this.offset;
            FastByteArrayInputStream stream = new FastByteArrayInputStream(get());
            for (int i = 0; i < nbtTagCount; i++) {
                try {
                    // TODO: 05/02/2019 This hack is necessary because we keep the raw NBT tag. Try to remove it.
                    CompoundTag tag = NBTIO.read(stream, ByteOrder.LITTLE_ENDIAN, true);
                    // Hack for tool damage
                    if (tag.contains("Damage")) {
                        damage = tag.getInt("Damage");
                        tag.remove("Damage");
                    }
                    if (tag.contains("__DamageConflict__")) {
                        tag.put("Damage", tag.removeAndGet("__DamageConflict__"));
                    }
                    if (tag.containsList("ench")) {
                        int enchCount = tag.getList("ench", CompoundTag.class).getAll().size();
                        if (enchCount > Enchantment.getEnchantments().length * 1.5) {
                            throw new RuntimeException("Too many enchantment: " + enchCount);
                        }
                    }
                    if (!tag.getAllTags().isEmpty()) {
                        nbt = NBTIO.write(tag, ByteOrder.LITTLE_ENDIAN, false);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            setOffset(offset + (int) stream.position());
        }

        int canPlaceCount = this.getVarInt();
        if (canPlaceCount > 4096) {
            throw new RuntimeException("Too many CanPlaceOn blocks: " + canPlaceCount);
        }

        String[] canPlaceOn = new String[canPlaceCount];
        for (int i = 0; i < canPlaceOn.length; ++i) {
            canPlaceOn[i] = this.getString();
        }

        int canBreakCount = this.getVarInt();
        if (canBreakCount > 4096) {
            throw new RuntimeException("Too many CanDestroy blocks: " + canBreakCount);
        }

        String[] canDestroy = new String[canBreakCount];
        for (int i = 0; i < canDestroy.length; ++i) {
            canDestroy[i] = this.getString();
        }

        try {
            if (nbt.length > 0) { // Protocol always < v1_16_220
                CompoundTag tag = Item.parseCompoundTag(nbt.clone());
                if (tag.contains(MV_ORIGIN_ID) && tag.contains(MV_ORIGIN_META)) {
                    int originID = tag.getInt(MV_ORIGIN_ID);
                    int originMeta = tag.getInt(MV_ORIGIN_META);

                    Item item;
                    if (protocolId < ProtocolInfo.v1_16_100
                            && id == Item.INFO_UPDATE
                            && originID == ItemID.STRING_IDENTIFIED_ITEM
                            && tag.contains(MV_ORIGIN_NAMESPACE)) {
                        stringId = tag.getString(MV_ORIGIN_NAMESPACE);
                        id = null;
                    } else if (id != null) { //数字id
                        if ((id == Item.INFO_UPDATE && originID >= Item.SUSPICIOUS_STEW) ||
                                (id == Item.DIAMOND_SWORD && originID == Item.NETHERITE_SWORD) ||
                                (id == Item.DIAMOND_SHOVEL && originID == Item.NETHERITE_SHOVEL) ||
                                (id == Item.DIAMOND_PICKAXE && originID == Item.NETHERITE_PICKAXE) ||
                                (id == Item.DIAMOND_AXE && originID == Item.NETHERITE_AXE) ||
                                (id == Item.DIAMOND_HOE && originID == Item.NETHERITE_HOE) ||
                                (id == Item.DIAMOND_HELMET && originID == Item.NETHERITE_HELMET) ||
                                (id == Item.DIAMOND_CHESTPLATE && originID == Item.NETHERITE_CHESTPLATE) ||
                                (id == Item.DIAMOND_LEGGINGS && originID == Item.NETHERITE_LEGGINGS) ||
                                (id == Item.DIAMOND_BOOTS && originID == Item.NETHERITE_BOOTS) ||
                                (id == Item.CARROT_ON_A_STICK && originID == Item.WARPED_FUNGUS_ON_A_STICK) ||
                                (id == Item.RECORD_13 && originID == Item.RECORD_PIGSTEP)) {
                            id = originID;
                        }
                    }

                    if (tag.contains(MV_ORIGIN_NBT)) {
                        nbt = NBTIO.write(tag.getCompound(MV_ORIGIN_NBT), ByteOrder.LITTLE_ENDIAN);
                    } else {
                        nbt = new byte[0];
                    }
                }
            }
        } catch (Exception e) {
            Server.getInstance().getLogger().logException(e);
        }

        Item item;
        if (id != null) {
            item = Item.get(id, damage, cnt, nbt);
        } else {
            item = Item.fromString(stringId);
            item.setDamage(damage);
            item.setCount(cnt);
            item.setCompoundTag(nbt);
        }

        if (canDestroy.length > 0 || canPlaceOn.length > 0) {
            CompoundTag namedTag = item.getNamedTag();
            if (namedTag == null) {
                namedTag = new CompoundTag();
            }

            if (canDestroy.length > 0) {
                ListTag<StringTag> listTag = new ListTag<>("CanDestroy");
                for (String blockName : canDestroy) {
                    listTag.add(new StringTag("", blockName));
                }
                namedTag.put("CanDestroy", listTag);
            }

            if (canPlaceOn.length > 0) {
                ListTag<StringTag> listTag = new ListTag<>("CanPlaceOn");
                for (String blockName : canPlaceOn) {
                    listTag.add(new StringTag("", blockName));
                }
                namedTag.put("CanPlaceOn", listTag);
            }

            item.setNamedTag(namedTag);
        }

        if (item.getId() == ItemID.SHIELD && protocolId >= ProtocolInfo.v1_11_0) {
            this.getVarLong();
        }

        return item;
    }

    private Item getSlotNew(GameVersion gameVersion) {
        int protocolId = gameVersion.getProtocol();
        int runtimeId = this.getVarInt();
        if (runtimeId == 0) {
            return Item.get(Item.AIR, 0, 0);
        }

        int cnt = this.getLShort();
        int damage = (int) this.getUnsignedVarInt();

        RuntimeItemMapping mapping = RuntimeItems.getMapping(gameVersion);

        Integer id = null;
        String stringId = null;
        try {
            LegacyEntry legacyEntry = mapping.fromRuntime(runtimeId);
            id = legacyEntry.getLegacyId();
            if (legacyEntry.isHasDamage()) {
                damage = legacyEntry.getDamage();
            }
        } catch (IllegalArgumentException e) {

        }

        if (id == null || !Utils.hasItemOrBlock(id)) {
            stringId = mapping.getNamespacedIdByNetworkId(runtimeId);
            if (stringId == null) {
                throw new IllegalArgumentException("Unknown item: runtimeID=" + runtimeId + " protocol=" + protocolId);
            }
            stringId = stringId + ":" + damage;
            id = null;
        }

        if (this.getBoolean()) { // hasNetId
            this.getVarInt(); // netId
        }

        int blockRuntimeId = this.getVarInt();// blockRuntimeId
        //TODO 在1.21.30会得到错误数据
        if (protocolId < ProtocolInfo.v1_19_0_31) {
            if (id != null && id < 256 && id != 166) { // ItemBlock
                int fullId = GlobalBlockPalette.getLegacyFullId(gameVersion, blockRuntimeId);
                if (fullId != -1) {
                    damage = fullId & Block.DATA_MASK;
                }
            }
        }

        byte[] bytes = this.getByteArray();
        ByteBuf buf = AbstractByteBufAllocator.DEFAULT.ioBuffer(bytes.length);
        buf.writeBytes(bytes);

        byte[] nbt = new byte[0];
        String[] canPlace;
        String[] canBreak;

        try (LittleEndianByteBufInputStream stream = new LittleEndianByteBufInputStream(buf)) {
            int nbtSize = stream.readShort();

            CompoundTag compoundTag = null;
            if (nbtSize > 0) {
                compoundTag = NBTIO.read(stream, ByteOrder.LITTLE_ENDIAN);
            } else if (nbtSize == -1) {
                int tagCount = stream.readUnsignedByte();
                if (tagCount != 1) throw new IllegalArgumentException("Expected 1 tag but got " + tagCount);
                compoundTag = NBTIO.read(stream, ByteOrder.LITTLE_ENDIAN);
            }

            if (compoundTag != null && !compoundTag.getAllTags().isEmpty()) {
                if (compoundTag.contains("Damage")) {
                    if (stringId != null || id > 255) {
                        damage = compoundTag.getInt("Damage");
                    }
                    compoundTag.remove("Damage");
                }
                if (compoundTag.contains("__DamageConflict__")) {
                    compoundTag.put("Damage", compoundTag.removeAndGet("__DamageConflict__"));
                }
                if (compoundTag.containsList("ench")) {
                    int enchCount = compoundTag.getList("ench", CompoundTag.class).getAll().size();
                    if (enchCount > Enchantment.getEnchantments().length * 1.5) {
                        throw new RuntimeException("Too many enchantment: " + enchCount);
                    }
                }
                if (!compoundTag.isEmpty()) {
                    nbt = NBTIO.write(compoundTag, ByteOrder.LITTLE_ENDIAN);
                }
            }

            int canPlaceCount = stream.readInt();
            if (canPlaceCount > 4096) {
                throw new RuntimeException("Too many CanPlaceOn blocks: " + canPlaceCount);
            }

            canPlace = new String[canPlaceCount];
            for (int i = 0; i < canPlace.length; i++) {
                canPlace[i] = stream.readUTF();
            }

            int canBreakCount = stream.readInt();
            if (canBreakCount > 4096) {
                throw new RuntimeException("Too many CanDestroy blocks: " + canBreakCount);
            }

            canBreak = new String[canBreakCount];
            for (int i = 0; i < canBreak.length; i++) {
                canBreak[i] = stream.readUTF();
            }

            if (id != null) {
                if (id == ItemID.SHIELD) {
                    stream.readLong();
                }

                if (id == Item.INFO_UPDATE && compoundTag != null && compoundTag.contains(MV_ORIGIN_ID) && compoundTag.contains(MV_ORIGIN_META)) {
                    int originID = compoundTag.getInt(MV_ORIGIN_ID);
                    int originMeta = compoundTag.getInt(MV_ORIGIN_META);
                    Item item;
                    if (originID == ItemID.STRING_IDENTIFIED_ITEM && compoundTag.contains(MV_ORIGIN_NAMESPACE)) {
                        item = Item.fromString(compoundTag.getString(MV_ORIGIN_NAMESPACE));
                        item.setDamage(originMeta);
                        item.setCount(cnt);
                    } else {
                        item = Item.get(originID, originMeta, cnt);
                    }
                    if (compoundTag.contains(MV_ORIGIN_NBT)) {
                        item.setNamedTag(compoundTag.getCompound(MV_ORIGIN_NBT));
                    }
                    return item;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read item user data", e);
        } finally {
            buf.release();
        }

        Item item;
        if (id != null) {
            item = Item.get(id, damage, cnt, nbt);
        } else {
            item = Item.fromString(stringId);
            item.setDamage(damage);
            item.setCount(cnt);
            item.setCompoundTag(nbt);
        }

        if (canBreak.length > 0 || canPlace.length > 0) {
            CompoundTag namedTag = item.getNamedTag();
            if (namedTag == null) {
                namedTag = new CompoundTag();
            }

            if (canBreak.length > 0) {
                ListTag<StringTag> listTag = new ListTag<>("CanDestroy");
                for (String blockName : canBreak) {
                    listTag.add(new StringTag("", blockName));
                }
                namedTag.put("CanDestroy", listTag);
            }

            if (canPlace.length > 0) {
                ListTag<StringTag> listTag = new ListTag<>("CanPlaceOn");
                for (String blockName : canPlace) {
                    listTag.add(new StringTag("", blockName));
                }
                namedTag.put("CanPlaceOn", listTag);
            }

            item.setNamedTag(namedTag);
        }

        return item;
    }

    public void putSlot(Item item) {
        Server.mvw("BinaryStream#putSlot(Item)");
        this.putSlot(GameVersion.getLastVersion(), item);
    }

    @Deprecated
    public void putSlot(int protocolId, Item item) {
        this.putSlot(protocolId, item, false);
    }

    @Deprecated
    public void putSlot(int protocolId, Item item, boolean crafting) {
        this.putSlot(GameVersion.byProtocol(protocolId, Server.getInstance().onlyNetEaseMode), item, crafting);
    }

    public void putSlot(GameVersion protocolId, Item item) {
        this.putSlot(protocolId, item, false);
    }

    public void putSlot(GameVersion gameVersion, Item item, boolean crafting) {
        int protocolId = gameVersion.getProtocol();
        if (protocolId >= ProtocolInfo.v1_16_220) {
            this.putSlotNew(gameVersion, item, crafting);
            return;
        }

        if (protocolId < ProtocolInfo.v1_2_0) {
            this.putSlotV113(item);
            return;
        }

        if (item == null || item.getId() == Item.AIR) {
            this.putVarInt(0);
            return;
        }

        int runtimeId = item.getId();
        boolean isStringItem = item instanceof StringItem;

        // Multiversion: Replace unsupported items
        boolean saveOriginalID = false;
        if (!crafting) {
            if (runtimeId == Item.SPYGLASS || // Protocol always < v1_16_220
                    (protocolId < ProtocolInfo.v1_16_100 && (isStringItem || runtimeId >= 10000))) { //StringItem & CustomItem
                saveOriginalID = true;
                runtimeId = Item.INFO_UPDATE;
            } else if (protocolId < ProtocolInfo.v1_16_0) {
                if (runtimeId >= Item.LODESTONECOMPASS) {
                    saveOriginalID = true;
                    switch (runtimeId) {
                        case Item.NETHERITE_SWORD:
                            runtimeId = Item.DIAMOND_SWORD;
                            break;
                        case Item.NETHERITE_SHOVEL:
                            runtimeId = Item.DIAMOND_SHOVEL;
                            break;
                        case Item.NETHERITE_PICKAXE:
                            runtimeId = Item.DIAMOND_PICKAXE;
                            break;
                        case Item.NETHERITE_AXE:
                            runtimeId = Item.DIAMOND_AXE;
                            break;
                        case Item.NETHERITE_HOE:
                            runtimeId = Item.DIAMOND_HOE;
                            break;
                        case Item.NETHERITE_HELMET:
                            runtimeId = Item.DIAMOND_HELMET;
                            break;
                        case Item.NETHERITE_CHESTPLATE:
                            runtimeId = Item.DIAMOND_CHESTPLATE;
                            break;
                        case Item.NETHERITE_LEGGINGS:
                            runtimeId = Item.DIAMOND_LEGGINGS;
                            break;
                        case Item.NETHERITE_BOOTS:
                            runtimeId = Item.DIAMOND_BOOTS;
                            break;
                        default:
                            runtimeId = Item.INFO_UPDATE;
                            break;
                    }
                } else {
                    if (protocolId < ProtocolInfo.v1_14_0) {
                        if (runtimeId == Item.HONEYCOMB || runtimeId == Item.HONEY_BOTTLE) {
                            saveOriginalID = true;
                            runtimeId = Item.INFO_UPDATE;
                        } else if (protocolId < ProtocolInfo.v1_13_0) {
                            if (runtimeId == Item.SUSPICIOUS_STEW) {
                                saveOriginalID = true;
                                runtimeId = Item.INFO_UPDATE;
                            }
                        }
                    }
                }
            }
        }

        int damage = item.hasMeta() ? item.getDamage() : -1;
        if (protocolId >= ProtocolInfo.v1_16_100) {
            RuntimeItemMapping mapping = RuntimeItems.getMapping(gameVersion);
            RuntimeEntry runtimeEntry;
            if (runtimeId == Item.INFO_UPDATE) { // Fix unknown item mapping errors with 1.16.100+ item replacements
                runtimeEntry = mapping.toRuntime(Item.INFO_UPDATE, item.getDamage());
            } else {
                try {
                    runtimeEntry = mapping.toRuntime(item.getId(), item.getDamage());
                } catch (IllegalArgumentException e) {
                    runtimeEntry = mapping.toRuntime(Item.INFO_UPDATE, item.getDamage());
                    saveOriginalID = true;
                    Server.getInstance().getLogger().debug("Unknown Item", e);
                }
            }
            runtimeId = runtimeEntry.getRuntimeId();
            damage = runtimeEntry.isHasDamage() ? 0 : item.getDamage();
        }

        this.putVarInt(runtimeId);

        int auxValue;
        boolean isDurable = item instanceof ItemDurable;

        if (protocolId >= ProtocolInfo.v1_12_0) {
            auxValue = item.getCount();
            if (!isDurable) {
                int meta;
                if (protocolId < ProtocolInfo.v1_16_100) {
                    meta = item.hasMeta() ? item.getDamage() : -1;
                } else {
                    meta = damage;
                }
                auxValue |= ((meta & 0x7fff) << 8);
            }
        } else {
            auxValue = (((item.hasMeta() ? item.getDamage() : -1) & 0x7fff) << 8) | item.getCount();
        }

        this.putVarInt(auxValue);

        // Hack: fix recipe list not displaying some items
        if (crafting) {
            this.putLShort(0);
            this.putVarInt(0);
            this.putVarInt(0);
            if (item.getId() == ItemID.SHIELD && protocolId >= ProtocolInfo.v1_11_0) {
                this.putVarLong(0);
            }
            return;
        }

        if (item.hasCompoundTag()
                || (isDurable && protocolId >= ProtocolInfo.v1_12_0)
                || saveOriginalID) {
            if (protocolId < ProtocolInfo.v1_12_0) {
                if (saveOriginalID) {
                    try {
                        CompoundTag compoundTag = item.getNamedTag();
                        if (compoundTag != null) {
                            item.setNamedTag(new CompoundTag().putCompound(MV_ORIGIN_NBT, compoundTag));
                        }
                        item.setCustomName(item.getName());
                        item.setNamedTag(item.getNamedTag().putInt(MV_ORIGIN_ID, item.getId()).putInt(MV_ORIGIN_META, item.getDamage()));
                        if (isStringItem) {
                            item.setNamedTag(item.getNamedTag().putString(MV_ORIGIN_NAMESPACE, item.getNamespaceId(protocolId)));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                byte[] nbt = item.getCompoundTag();
                this.putLShort(nbt.length);
                this.put(nbt);
            } else {
                try {
                    // Hack for tool damage
                    byte[] nbt = item.getCompoundTag();
                    CompoundTag tag;
                    if (nbt == null || nbt.length == 0) {
                        tag = new CompoundTag();
                    } else {
                        tag = NBTIO.read(nbt, ByteOrder.LITTLE_ENDIAN, false);
                    }
                    if (tag.contains("Damage")) {
                        tag.put("__DamageConflict__", tag.removeAndGet("Damage"));
                    }
                    if (isDurable) {
                        tag.putInt("Damage", item.getDamage());
                    }

                    if (saveOriginalID) {
                        try {
                            item.setNamedTag(new CompoundTag().putCompound(MV_ORIGIN_NBT, tag));
                            item.setCustomName(item.getName());
                            item.setNamedTag(item.getNamedTag().putInt(MV_ORIGIN_ID, item.getId()).putInt(MV_ORIGIN_META, item.getDamage()));
                            if (isStringItem) {
                                item.setNamedTag(item.getNamedTag().putString(MV_ORIGIN_NAMESPACE, item.getNamespaceId(protocolId)));
                            }
                            tag = item.getNamedTag();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    this.putLShort(0xffff);
                    this.putByte((byte) 1);
                    this.put(NBTIO.write(tag, ByteOrder.LITTLE_ENDIAN, true));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            this.putLShort(0);
        }
        List<String> canPlaceOn = extractStringList(item, "CanPlaceOn");
        List<String> canDestroy = extractStringList(item, "CanDestroy");
        this.putVarInt(canPlaceOn.size());
        for (String block : canPlaceOn) {
            this.putString(block);
        }
        this.putVarInt(canDestroy.size());
        for (String block : canDestroy) {
            this.putString(block);
        }

        if (item.getId() == ItemID.SHIELD && protocolId >= ProtocolInfo.v1_11_0) {
            this.putVarLong(0); //"blocking tick" (ffs mojang)
        }
    }

    private void putSlotV113(Item item) {
        if (item == null || item.getId() == Item.AIR) {
            this.putVarInt(0);
            return;
        }

        this.putVarInt(item.getId());
        int auxValue = (((item.hasMeta() ? item.getDamage() : -1) & 0x7fff) << 8) | item.getCount();
        this.putVarInt(auxValue);
        byte[] nbt = item.getCompoundTag();
        this.putLShort(nbt.length);
        this.put(nbt);
        this.putVarInt(0); //CanPlaceOn entry count
        this.putVarInt(0); //CanDestroy entry count
    }

    private void putSlotNew(GameVersion protocolId, Item item, boolean instanceItem) {
        if (item == null || item.getId() == Item.AIR) {
            this.putByte((byte) 0);
            return;
        }

        RuntimeItemMapping mapping = RuntimeItems.getMapping(protocolId);
        boolean isErrorItem = false;
        boolean isStringItem = item instanceof StringItem;
        try {
            if (isStringItem && mapping.getNetworkIdByNamespaceId(item.getNamespaceId()).isEmpty()) {
                throw new IllegalArgumentException("Unknown StringItem : NamespaceId=" + item.getNamespaceId() + " protocol=" + protocolId);
            } else {
                mapping.toRuntime(item.getId(), item.getDamage());
            }
        } catch (Exception e) {
            Server.getInstance().getLogger().debug("Unknown Item", e);
            isErrorItem = true;
        }

        if (!item.isSupportedOn(protocolId) || isErrorItem) {
            Item originItem = item;
            item = Item.get(Item.INFO_UPDATE, 0, originItem.getCount());
            CompoundTag compoundTag = originItem.getNamedTag();
            if (compoundTag != null) {
                item.setNamedTag(new CompoundTag().putCompound(MV_ORIGIN_NBT, compoundTag));
            }
            item.setCustomName(originItem.getName());
            item.setNamedTag(item.getNamedTag().putInt(MV_ORIGIN_ID, originItem.getId()).putInt(MV_ORIGIN_META, originItem.getDamage()));
            if (isStringItem) {
                item.setNamedTag(item.getNamedTag().putString(MV_ORIGIN_NAMESPACE, originItem.getNamespaceId(protocolId)));
            }
        }

        int id = item.getId();
        int meta = item.getDamage();
        boolean isBlock = item instanceof ItemBlock;
        boolean isDurable = item instanceof ItemDurable;

        int runtimeId;
        int damage = 0;
        if (isStringItem && !isErrorItem) {
            runtimeId = mapping.getNetworkId(item);
            damage = item.getDamage();
        } else {
            RuntimeEntry runtimeEntry = mapping.toRuntime(id, meta);
            runtimeId = runtimeEntry.getRuntimeId();
            damage = isBlock || isDurable || runtimeEntry.isHasDamage() ? 0 : meta;
        }

        this.putVarInt(runtimeId);
        this.putLShort(item.getCount());
        this.putUnsignedVarInt(damage);

        if (!instanceItem) {
            this.putBoolean(true);
            this.putVarInt(1); // Item is present
        }

        Block block = isBlock ? item.getBlockUnsafe() : null;
        int blockRuntimeId = block == null ? 0 : GlobalBlockPalette.getOrCreateRuntimeId(protocolId, block.getId(), block.getDamage());
        this.putVarInt(blockRuntimeId);

        ByteBuf userDataBuf = ByteBufAllocator.DEFAULT.ioBuffer();
        try (LittleEndianByteBufOutputStream stream = new LittleEndianByteBufOutputStream(userDataBuf)) {
            if (!instanceItem && (isDurable || block != null && block.getDamage() > 0)) {
                byte[] nbt = item.getCompoundTag();
                CompoundTag tag;
                if (nbt == null || nbt.length == 0) {
                    tag = new CompoundTag();
                } else {
                    tag = NBTIO.read(nbt, ByteOrder.LITTLE_ENDIAN);
                }
                if (tag.contains("Damage")) {
                    tag.put("__DamageConflict__", tag.removeAndGet("Damage"));
                }
                tag.putInt("Damage", meta);
                stream.writeShort(-1);
                stream.writeByte(1); // Hardcoded in current version
                stream.write(NBTIO.write(tag, ByteOrder.LITTLE_ENDIAN));
            } else if (item.hasCompoundTag()) {
                stream.writeShort(-1);
                stream.writeByte(1); // Hardcoded in current version
                stream.write(item.getCompoundTag());
            } else {
                userDataBuf.writeShortLE(0);
            }

            List<String> canPlaceOn = extractStringList(item, "CanPlaceOn");
            stream.writeInt(canPlaceOn.size());
            for (String string : canPlaceOn) {
                stream.writeUTF(string);
            }

            List<String> canDestroy = extractStringList(item, "CanDestroy");
            stream.writeInt(canDestroy.size());
            for (String string : canDestroy) {
                stream.writeUTF(string);
            }

            if (id == ItemID.SHIELD) {
                stream.writeLong(0);
            }

            byte[] bytes = Utils.convertByteBuf2Array(userDataBuf);
            putByteArray(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write item user data", e);
        } finally {
            userDataBuf.release();
        }
    }

    @Deprecated
    public Item getRecipeIngredient(int protocolId) {
        return this.getRecipeIngredient(GameVersion.byProtocol(protocolId, false));
    }

    public Item getRecipeIngredient(GameVersion gameVersion) {
        int protocolId = gameVersion.getProtocol();
        int runtimeId = this.getVarInt();
        if (runtimeId == 0) {
            return Item.get(0, 0, 0);
        }

        int damage = this.getVarInt();
        if (damage == 0x7fff) {
            damage = -1;
        }

        int id;
        if (protocolId < ProtocolInfo.v1_16_100) {
            id = runtimeId;
        } else {
            RuntimeItemMapping mapping = RuntimeItems.getMapping(gameVersion);
            LegacyEntry legacyEntry = mapping.fromRuntime(runtimeId);
            id = legacyEntry.getLegacyId();
            if (legacyEntry.isHasDamage()) {
                damage = legacyEntry.getDamage();
            }
        }

        int count = this.getVarInt();
        return Item.get(id, damage, count);
    }

    @Deprecated
    public void putRecipeIngredient(int protocolId, Item item) {
        this.putRecipeIngredient(GameVersion.byProtocol(protocolId, Server.getInstance().onlyNetEaseMode), item);
    }

    public void putRecipeIngredient(GameVersion gameVersion, Item item) {
        int protocolId = gameVersion.getProtocol();
        if (item == null || item.getId() == 0) {
            if (protocolId >= ProtocolInfo.v1_19_30_23) {
                this.putByte((byte) 0); //ItemDescriptorType.INVALID
            }
            this.putVarInt(0); // item == null ? 0 : item.getCount()
            return;
        }

        if (protocolId >= ProtocolInfo.v1_19_30_23) {
            this.putByte((byte) 1); //ItemDescriptorType.DEFAULT
        }

        int runtimeId = item.getId();
        int damage = item.hasMeta() ? item.getDamage() : Short.MAX_VALUE;

        if (protocolId >= ProtocolInfo.v1_16_100) {
            RuntimeItemMapping mapping = RuntimeItems.getMapping(gameVersion);
            if (item instanceof StringItem) {
                runtimeId = mapping.getNetworkId(item);
            } else if (!item.hasMeta()) {
                RuntimeEntry runtimeEntry = mapping.toRuntime(item.getId(), 0);
                runtimeId = runtimeEntry.getRuntimeId();
                damage = Short.MAX_VALUE;
            } else {
                RuntimeEntry runtimeEntry = mapping.toRuntime(item.getId(), item.getDamage());
                runtimeId = runtimeEntry.getRuntimeId();
                damage = runtimeEntry.isHasDamage() ? 0 : item.getDamage();
            }
        }

        if (protocolId >= ProtocolInfo.v1_19_30_23) {
            this.putLShort(runtimeId);
            this.putLShort(damage);
        }else {
            this.putVarInt(runtimeId);
            this.putVarInt(damage);
        }
        this.putVarInt(item.getCount());
    }

    //TODO 改成ItemDescriptor 合并两个putRecipeIngredient方法
    public void putRecipeIngredient(int protocolId, String itemTag, int count) {
        if (protocolId < ProtocolInfo.v1_19_30_23) {
            throw new UnsupportedOperationException("This method is only supported on protocol 553+");
        }
        this.putByte((byte) 3);
        this.putString(itemTag);
        this.putVarInt(count);
    }

    private static List<String> extractStringList(Item item, String tagName) {
        CompoundTag namedTag = item.getNamedTag();
        if (namedTag == null) {
            return Collections.emptyList();
        }

        ListTag<StringTag> listTag = namedTag.getList(tagName, StringTag.class);
        if (listTag == null) {
            return Collections.emptyList();
        }

        int size = listTag.size();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            StringTag stringTag = listTag.get(i);
            if (stringTag != null) {
                values.add(stringTag.data);
            }
        }

        return values;
    }

    public byte[] getByteArray() {
        return this.get((int) this.getUnsignedVarInt());
    }

    public byte[] getByteArray(int maxLength) {
        int length = (int) this.getUnsignedVarInt();
        checkArgument(this.isReadable(length),
                "Tried to read %s bytes but only has %s readable", length, this.readableBytes());
        checkArgument(maxLength <= 0 || length <= maxLength, "Tried to read %s bytes but maximum is %s", length, maxLength);
        return this.get(length);
    }

    public void putByteArray(byte[] b) {
        this.putUnsignedVarInt(b.length);
        this.put(b);
    }

    public String getString() {
        return new String(this.getByteArray(), StandardCharsets.UTF_8);
    }

    public void putString(String string) {
        byte[] b = string.getBytes(StandardCharsets.UTF_8);
        this.putByteArray(b);
    }

    public long getUnsignedVarInt() {
        return VarInt.readUnsignedVarInt(this);
    }

    public void putUnsignedVarInt(long v) {
        VarInt.writeUnsignedVarInt(this, v);
    }

    public int getVarInt() {
        return VarInt.readVarInt(this);
    }

    public void putVarInt(int v) {
        VarInt.writeVarInt(this, v);
    }

    public long getVarLong() {
        return VarInt.readVarLong(this);
    }

    public void putVarLong(long v) {
        VarInt.writeVarLong(this, v);
    }

    public long getUnsignedVarLong() {
        return VarInt.readUnsignedVarLong(this);
    }

    public void putUnsignedVarLong(long v) {
        VarInt.writeUnsignedVarLong(this, v);
    }

    public BlockVector3 getBlockVector3() {
        return new BlockVector3(this.getVarInt(), (int) this.getUnsignedVarInt(), this.getVarInt());
    }

    public BlockVector3 getSignedBlockPosition() {
        return new BlockVector3(getVarInt(), getVarInt(), getVarInt());
    }

    public void putSignedBlockPosition(BlockVector3 v) {
        putVarInt(v.x);
        putVarInt(v.y);
        putVarInt(v.z);
    }

    public void putBlockVector3(BlockVector3 v) {
        this.putBlockVector3(v.x, v.y, v.z);
    }

    public void putBlockVector3(int x, int y, int z) {
        this.putVarInt(x);
        this.putUnsignedVarInt(y);
        this.putVarInt(z);
    }

    public Vector3f getVector3f() {
        return new Vector3f(this.getLFloat(), this.getLFloat(), this.getLFloat());
    }

    public void putVector3f(Vector3f v) {
        this.putVector3f(v.x, v.y, v.z);
    }

    public void putVector3f(float x, float y, float z) {
        this.putLFloat(x);
        this.putLFloat(y);
        this.putLFloat(z);
    }

    public Vector2f getVector2f() {
        return new Vector2f(this.getLFloat(), this.getLFloat());
    }

    public void putVector2f(Vector2f v) {
        this.putVector2f(v.x, v.y);
    }

    public void putVector2f(float x, float y) {
        this.putLFloat(x);
        this.putLFloat(y);
    }

    public double getRotationByte() {
        return this.getByte() * (360d / 256d);
    }

    public void putRotationByte(double rotation) {
        this.putByte((byte) (rotation / (360d / 256d)));
    }

    public void putGameRules(GameRules gameRules, boolean startGame) {
        Server.mvw("BinaryStream#putGameRules(GameRules, boolean)");
        this.putGameRules(ProtocolInfo.CURRENT_PROTOCOL, gameRules, startGame);
    }

    @Deprecated
    public void putGameRules(int protocol, GameRules gameRules, boolean startGame) {
        this.putGameRules(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), gameRules, startGame);
    }

    public void putGameRules(GameVersion gameVersion, GameRules gameRules, boolean startGame) {
        Map<GameRule, GameRules.Value> allGameRules = gameRules.getGameRules();
        Map<GameRule, GameRules.Value> rulesToSend = new HashMap<>();
        allGameRules.forEach((gameRule, value) -> {
            if (gameVersion.getProtocol() > value.getMinProtocol()) {
                rulesToSend.put(gameRule, value);
            }
        });
        this.putUnsignedVarInt(rulesToSend.size());
        rulesToSend.forEach((gameRule, value) -> {
            putString(gameRule.getName().toLowerCase(Locale.ROOT));
            value.write(gameVersion, this, startGame);
        });
    }

    @Deprecated
    public void putGameRulesMap(int protocol, Map<GameRule, GameRules.Value> allGameRules, boolean startGame) {
        this.putGameRulesMap(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), allGameRules, startGame);
    }

    public void putGameRulesMap(GameVersion gameVersion, Map<GameRule, GameRules.Value> allGameRules, boolean startGame) {
        Map<GameRule, GameRules.Value> rulesToSend = new HashMap<>();
        allGameRules.forEach((gameRule, value) -> {
            if (gameVersion.getProtocol() > value.getMinProtocol()) {
                rulesToSend.put(gameRule, value);
            }
        });
        this.putUnsignedVarInt(rulesToSend.size());
        rulesToSend.forEach((gameRule, value) -> {
            putString(gameRule.getName().toLowerCase(Locale.ROOT));
            value.write(gameVersion, this, startGame);
        });
    }

    /**
     * Reads and returns an EntityUniqueID
     *
     * @return int
     */
    public long getEntityUniqueId() {
        return this.getVarLong();
    }

    /**
     * Writes an EntityUniqueID
     */
    public void putEntityUniqueId(long eid) {
        this.putVarLong(eid);
    }

    /**
     * Reads and returns an EntityRuntimeID
     */
    public long getEntityRuntimeId() {
        return this.getUnsignedVarLong();
    }

    /**
     * Writes an EntityUniqueID
     */
    public void putEntityRuntimeId(long eid) {
        this.putUnsignedVarLong(eid);
    }

    public BlockFace getBlockFace() {
        return BlockFace.fromIndex(this.getVarInt());
    }

    public void putBlockFace(BlockFace face) {
        this.putVarInt(face.getIndex());
    }

    public void putEntityLink(int protocol, EntityLink link) {
        this.putEntityUniqueId(link.fromEntityUniquieId);
        this.putEntityUniqueId(link.toEntityUniquieId);
        this.putByte(link.type);
        if (protocol >= ProtocolInfo.v1_2_0) {
            this.putBoolean(link.immediate);
            if (protocol >= ProtocolInfo.v1_16_0) {
                this.putBoolean(link.riderInitiated);
                if (protocol >= ProtocolInfo.v1_21_20) {
                    this.putLFloat(link.vehicleAngularVelocity);
                }
            }
        }
    }

    public EntityLink getEntityLink() {
        return new EntityLink(
                getEntityUniqueId(),
                getEntityUniqueId(),
                (byte) getByte(),
                getBoolean(),
                getBoolean(), //1.16+
                getLFloat() //1.21.20+
        );
    }

    @SuppressWarnings("unchecked")
    public <T> T[] getArray(Class<T> clazz, Function<BinaryStream, T> function) {
        ArrayDeque<T> deque = new ArrayDeque<>();
        int count = (int) getUnsignedVarInt();
        for (int i = 0; i < count; i++) {
            deque.add(function.apply(this));
        }
        return deque.toArray((T[]) Array.newInstance(clazz, 0));
    }

    public <T> void getArray(Collection<T> array, Function<BinaryStream, T> function) {
        getArray(array, BinaryStream::getUnsignedVarInt, function);
    }

    public <T> void getArray(Collection<T> array, ToLongFunction<BinaryStream> lengthReader, Function<BinaryStream, T> function) {
        long length = lengthReader.applyAsLong(this);
        for (int i = 0; i < length; i++) {
            array.add(function.apply(this));
        }
    }

    public <T> void putArray(Collection<T> collection, Consumer<T> writer) {
        if (collection == null) {
            putUnsignedVarInt(0);
            return;
        }
        putUnsignedVarInt(collection.size());
        collection.forEach(writer);
    }

    public <T> void putArray(T[] collection, Consumer<T> writer) {
        if (collection == null) {
            putUnsignedVarInt(0);
            return;
        }
        putUnsignedVarInt(collection.length);
        for (T t : collection) {
            writer.accept(t);
        }
    }

    public <T> void putArray(@NotNull Collection<T> array, BiConsumer<BinaryStream, T> biConsumer) {
        this.putUnsignedVarInt(array.size());
        for (T val : array) {
            biConsumer.accept(this, val);
        }
    }

    public <O> O getOptional(O emptyValue, Function<BinaryStream, O> function) {
        if (this.getBoolean()) {
            return function.apply(this);
        }
        return emptyValue;
    }

    public <T> void putOptional(@NotNull Predicate<T> isPresent, T object, Consumer<T> consumer) {
        Preconditions.checkNotNull(consumer, "read consumer");

        boolean exists = isPresent.test(object);
        this.putBoolean(exists);
        if (exists) {
            consumer.accept(object);
        }
    }

    public <T> void putOptional(@NotNull Predicate<T> isPresent, T object, BiConsumer<BinaryStream, T> consumer) {
        Preconditions.checkNotNull(consumer, "read consumer");

        boolean exists = isPresent.test(object);
        this.putBoolean(exists);
        if (exists) {
            consumer.accept(this, object);
        }
    }

    public <T> void putOptionalNull(T object, Consumer<@NotNull T> consumer) {
        this.putOptional(Objects::nonNull, object, consumer);
    }

    public <T> void putOptionalNull(T object, BiConsumer<BinaryStream, @NotNull T> consumer) {
        this.putOptional(Objects::nonNull, object, consumer);
    }

    public void writeFullContainerName(FullContainerName fullContainerName) {
        this.putByte((byte) fullContainerName.getContainer().getId());
        this.putOptionalNull(fullContainerName.getDynamicId(), this::putLInt);
    }

    public boolean isReadable(int length) {
        return this.count - this.offset >= length;
    }

    public int readableBytes() {
        return this.count - this.offset;
    }

    public boolean feof() {
        return this.offset < 0 || this.offset >= this.buffer.length;
    }

    @SneakyThrows(IOException.class)
    public CompoundTag getTag() {
        ByteArrayInputStream is = new ByteArrayInputStream(buffer, offset, buffer.length);
        int initial = is.available();
        try {
            return NBTIO.read(is);
        } finally {
            offset += initial - is.available();
        }
    }

    private void ensureCapacity(int minCapacity) {
        // overflow-conscious code
        if (minCapacity - buffer.length > 0) {
            grow(minCapacity);
        }
    }

    private void grow(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = buffer.length;
        int newCapacity = oldCapacity << 1;

        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }

        if (newCapacity - MAX_ARRAY_SIZE > 0) {
            newCapacity = hugeCapacity(minCapacity);
        }
        this.buffer = Arrays.copyOf(buffer, newCapacity);
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) { // overflow
            throw new OutOfMemoryError();
        }
        return (minCapacity > MAX_ARRAY_SIZE) ?
                Integer.MAX_VALUE :
                MAX_ARRAY_SIZE;
    }

    public <T> void putNbtTag(T tag) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try (NBTOutputStream writer = NbtUtils.createNetworkWriter(stream)) {
            writer.writeTag(tag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.put(stream.toByteArray());
    }

    public ItemStackRequest readItemStackRequest() {
        return readItemStackRequest(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public ItemStackRequest readItemStackRequest(int protocol) {
        int requestId = getVarInt();
        ItemStackRequestAction[] actions = getArray(ItemStackRequestAction.class, (s) -> {
            ItemStackRequestActionType itemStackRequestActionType = ItemStackRequestActionType.fromId(s.getByte());
            return readRequestActionData(protocol, itemStackRequestActionType);
        });
        String[] filteredStrings = getArray(String.class, BinaryStream::getString);

        int originVal = getLInt();
        TextProcessingEventOrigin origin = originVal == -1 ? null : TextProcessingEventOrigin.fromId(originVal);  // new for v552
        return new ItemStackRequest(requestId, actions, filteredStrings, origin);
    }

    protected ItemStackRequestAction readRequestActionData(int protocol, ItemStackRequestActionType type) {
        return switch (type) {
            case CRAFT_REPAIR_AND_DISENCHANT -> new CraftGrindstoneAction((int) getUnsignedVarInt(), getVarInt());
            case CRAFT_LOOM -> new CraftLoomAction(getString());
            case CRAFT_RECIPE_AUTO -> new AutoCraftRecipeAction(
                    (int) getUnsignedVarInt(), getByte(), Collections.emptyList()
            );
            case CRAFT_RESULTS_DEPRECATED -> new CraftResultsDeprecatedAction(
                    getArray(Item.class, (s) -> s.getSlot(protocol)),
                    getByte()
            );
            case MINE_BLOCK -> new MineBlockAction(getVarInt(), getVarInt(), getVarInt());
            case CRAFT_RECIPE_OPTIONAL -> new CraftRecipeOptionalAction((int) getUnsignedVarInt(), getLInt());
            case TAKE -> new TakeAction(
                    getByte(),
                    readStackRequestSlotInfo(),
                    readStackRequestSlotInfo()
            );
            case PLACE -> new PlaceAction(
                    getByte(),
                    readStackRequestSlotInfo(),
                    readStackRequestSlotInfo()
            );
            case SWAP -> new SwapAction(
                    readStackRequestSlotInfo(),
                    readStackRequestSlotInfo()
            );
            case DROP -> new DropAction(
                    getByte(),
                    readStackRequestSlotInfo(),
                    getBoolean()
            );
            case DESTROY -> new DestroyAction(
                    getByte(),
                    readStackRequestSlotInfo()
            );
            case CONSUME -> new ConsumeAction(
                    getByte(),
                    readStackRequestSlotInfo()
            );
            case CREATE -> new CreateAction(
                    getByte()
            );
            case LAB_TABLE_COMBINE -> new LabTableCombineAction();
            case BEACON_PAYMENT -> new BeaconPaymentAction(
                    getVarInt(),
                    getVarInt()
            );
            case CRAFT_RECIPE -> new CraftRecipeAction(
                    (int) getUnsignedVarInt()
            );
            case CRAFT_CREATIVE -> new CraftCreativeAction(
                    (int) getUnsignedVarInt()
            );
            case CRAFT_NON_IMPLEMENTED_DEPRECATED -> new CraftNonImplementedAction();
            default -> throw new UnsupportedOperationException("Unhandled stack request action type: " + type);
        };
    }

    private ItemStackRequestSlotData readStackRequestSlotInfo() {
        return new ItemStackRequestSlotData(
                ContainerSlotType.fromId(getByte()),
                getByte(),
                getVarInt()
        );
    }
}
