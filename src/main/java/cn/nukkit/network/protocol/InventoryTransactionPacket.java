package cn.nukkit.network.protocol;

import cn.nukkit.inventory.transaction.data.ReleaseItemData;
import cn.nukkit.inventory.transaction.data.TransactionData;
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.inventory.transaction.data.UseItemOnEntityData;
import cn.nukkit.math.BlockFace;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
public class InventoryTransactionPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.INVENTORY_TRANSACTION_PACKET;

    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_MISMATCH = 1;
    public static final int TYPE_USE_ITEM = 2;
    public static final int TYPE_USE_ITEM_ON_ENTITY = 3;
    public static final int TYPE_RELEASE_ITEM = 4;

    public static final int USE_ITEM_ACTION_CLICK_BLOCK = 0;
    public static final int USE_ITEM_ACTION_CLICK_AIR = 1;
    public static final int USE_ITEM_ACTION_BREAK_BLOCK = 2;

    public static final int RELEASE_ITEM_ACTION_RELEASE = 0; //bow shoot
    public static final int RELEASE_ITEM_ACTION_CONSUME = 1; //eat food, drink potion

    public static final int USE_ITEM_ON_ENTITY_ACTION_INTERACT = 0;
    public static final int USE_ITEM_ON_ENTITY_ACTION_ATTACK = 1;
    public static final int USE_ITEM_ON_ENTITY_ACTION_ITEM_INTERACT = 2;

    /**
     * Maximum number of legacy set-item-slot entries accepted from the client.
     * <p>
     * Mirrors CloudburstMC Protocol's {@code EncodingSettings.maxListSize} default (1536) that
     * bounds {@code readArray} on the inventory-transaction legacy-slots path.
     */
    public static final int MAX_LEGACY_SLOT_ENTRIES = 1536;

    /**
     * Maximum byte length of a single legacy set-item-slot blob accepted from the client.
     * <p>
     * Mirrors CloudburstMC Protocol's {@code InventoryTransactionSerializer_v1001} hard cap of 89
     * (the largest known slot count) used when reading a legacy slot byte array.
     */
    public static final int MAX_LEGACY_SLOT_BLOB_LENGTH = 89;

    public static final int ACTION_MAGIC_SLOT_DROP_ITEM = 0;
    public static final int ACTION_MAGIC_SLOT_PICKUP_ITEM = 1;

    public static final int ACTION_MAGIC_SLOT_CREATIVE_DELETE_ITEM = 0;
    public static final int ACTION_MAGIC_SLOT_CREATIVE_CREATE_ITEM = 1;

    public int transactionType;
    public NetworkInventoryAction[] actions;
    public TransactionData transactionData;
    public boolean hasNetworkIds = false;
    public int legacyRequestId;
    public List<LegacySetItemSlotData> legacySlots = new ArrayList<>();

    /**
     * NOTE: THESE FIELDS DO NOT EXIST IN THE PROTOCOL, it's merely used for convenience for us to easily
     * determine whether we're doing a crafting or enchanting transaction.
     */
    public boolean isCraftingPart = false;
    public boolean isEnchantingPart = false;
    public boolean isRepairItemPart = false;
    public boolean isTradeItemPart = false;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public DataPacket clean() {
        this.legacySlots = new ArrayList<>();
        return super.clean();
    }

    @Override
    public InventoryTransactionPacket clone() {
        InventoryTransactionPacket packet = (InventoryTransactionPacket) super.clone();
        packet.legacySlots = new ArrayList<>();
        if (this.legacySlots != null) {
            for (LegacySetItemSlotData legacySlot : this.legacySlots) {
                packet.legacySlots.add(legacySlot.clone());
            }
        }
        return packet;
    }

    @Override
    public void encode() {
        this.reset();

        if (protocol >= 407) {
            this.putVarInt(this.legacyRequestId);
            if (protocol >= ProtocolInfo.v1_26_30) {
                this.putBoolean(this.hasLegacySlots());
                if (this.hasLegacySlots()) {
                    this.putLegacySlots();
                }
            } else if (this.hasLegacySlots()) {
                this.putLegacySlots();
            } else if (this.legacyRequestId > 0) {
                //TODO
            }
        }

        if (this.protocol >= ProtocolInfo.v1_26_30) {
            this.putBoolean(true);
        }
        this.putUnsignedVarInt(this.transactionType);
        if (protocol >= 407 && protocol < ProtocolInfo.v1_16_220) {
            this.putBoolean(this.hasNetworkIds);
        }
        if (this.protocol >= ProtocolInfo.v1_26_30) {
            this.putBoolean(true);
        }
        this.putUnsignedVarInt(this.actions.length);

        for (NetworkInventoryAction action : this.actions) {
            action.write(this);
        }

        switch (this.transactionType) {
            case TYPE_NORMAL:
            case TYPE_MISMATCH:
                break;
            case TYPE_USE_ITEM:
                UseItemData useItemData = (UseItemData) this.transactionData;

                this.putTransactionActionType(useItemData.actionType);
                if (this.protocol >= ProtocolInfo.v1_21_20) {
                    if (this.protocol >= ProtocolInfo.v1_26_30) {
                        this.putByte(useItemData.triggerType);
                    } else {
                        this.putUnsignedVarInt(useItemData.triggerType);
                    }
                }
                this.putBlockVector3(this.gameVersion, useItemData.blockPos);
                if (this.protocol >= ProtocolInfo.v1_26_30) {
                    this.putByte(useItemData.face.getIndex());
                } else {
                    this.putBlockFace(useItemData.face);
                }
                this.putVarInt(useItemData.hotbarSlot);
                if (this.protocol >= ProtocolInfo.v1_26_30) {
                    this.putNetworkItemStackDescriptor(gameVersion, useItemData.itemInHand);
                } else {
                    this.putSlot(gameVersion, useItemData.itemInHand);
                }
                this.putVector3f(useItemData.playerPos.asVector3f());
                this.putVector3f(useItemData.clickPos);
                if (this.protocol >= ProtocolInfo.v1_16_210) {
                    this.putUnsignedVarInt(useItemData.blockRuntimeId);
                    if (this.protocol >= ProtocolInfo.v1_21_20) {
                        if (this.protocol >= ProtocolInfo.v1_26_30) {
                            this.putByte(useItemData.clientInteractPrediction);
                        } else {
                            this.putUnsignedVarInt(useItemData.clientInteractPrediction);
                        }
                    }
                }
                if (this.protocol >= ProtocolInfo.v1_26_10) {
                    this.putByte(useItemData.clientCooldownState);
                }
                break;
            case TYPE_USE_ITEM_ON_ENTITY:
                UseItemOnEntityData useItemOnEntityData = (UseItemOnEntityData) this.transactionData;

                this.putEntityRuntimeId(useItemOnEntityData.entityRuntimeId);
                this.putTransactionActionType(useItemOnEntityData.actionType);
                this.putVarInt(useItemOnEntityData.hotbarSlot);
                if (this.protocol >= ProtocolInfo.v1_26_30) {
                    this.putNetworkItemStackDescriptor(gameVersion, useItemOnEntityData.itemInHand);
                } else {
                    this.putSlot(gameVersion, useItemOnEntityData.itemInHand);
                }
                this.putVector3f(useItemOnEntityData.playerPos.asVector3f());
                this.putVector3f(useItemOnEntityData.clickPos.asVector3f());
                break;
            case TYPE_RELEASE_ITEM:
                ReleaseItemData releaseItemData = (ReleaseItemData) this.transactionData;

                this.putTransactionActionType(releaseItemData.actionType);
                this.putVarInt(releaseItemData.hotbarSlot);
                if (this.protocol >= ProtocolInfo.v1_26_30) {
                    this.putNetworkItemStackDescriptor(gameVersion, releaseItemData.itemInHand);
                } else {
                    this.putSlot(gameVersion, releaseItemData.itemInHand);
                }
                this.putVector3f(releaseItemData.headRot.asVector3f());
                break;
            default:
                throw new RuntimeException("Unknown transaction type " + this.transactionType);
        }
    }

    @Override
    public void decode() {
        if (protocol >= 407) {
            this.legacyRequestId = this.getVarInt();
            if (protocol >= ProtocolInfo.v1_26_30) {
                if (this.getBoolean() && legacyRequestId < -1 && (legacyRequestId & 1) == 0) {
                    this.readLegacySlots();
                }
            } else if (legacyRequestId < -1 && (legacyRequestId & 1) == 0) {
                this.readLegacySlots();
            }
        }

        if (this.protocol >= ProtocolInfo.v1_26_30 && !this.getBoolean()) {
            throw new IllegalStateException("Expected InventoryTransactionType");
        }
        this.transactionType = (int) this.getUnsignedVarInt();

        if (protocol >= 407 && protocol < ProtocolInfo.v1_16_220) {
            this.hasNetworkIds = this.getBoolean();
        }

        if (this.protocol >= ProtocolInfo.v1_26_30 && !this.getBoolean()) {
            throw new IllegalStateException("Expected InventoryActionData");
        }
        this.actions = new NetworkInventoryAction[Math.min((int) this.getUnsignedVarInt(), 4096)];
        for (int i = 0; i < this.actions.length; i++) {
            this.actions[i] = new NetworkInventoryAction().read(this);
        }

        switch (this.transactionType) {
            case TYPE_NORMAL:
            case TYPE_MISMATCH:
                //Regular ComplexInventoryTransaction doesn't read any extra data
                break;
            case TYPE_USE_ITEM:
                UseItemData itemData = new UseItemData();

                itemData.actionType = this.getTransactionActionType();
                if (this.protocol >= ProtocolInfo.v1_21_20) {
                    itemData.triggerType = this.protocol >= ProtocolInfo.v1_26_30 ? this.getByte() & 0xff : (int) this.getUnsignedVarInt();
                }
                itemData.blockPos = this.getBlockVector3(this.gameVersion);
                itemData.face = this.protocol >= ProtocolInfo.v1_26_30 ? BlockFace.fromIndex(this.getByte() & 0xff) : this.getBlockFace();
                itemData.hotbarSlot = this.getVarInt();
                itemData.itemInHand = this.protocol >= ProtocolInfo.v1_26_30 ? this.getNetworkItemStackDescriptor(this.gameVersion) : this.getSlot(this.gameVersion);
                itemData.playerPos = this.getVector3f().asVector3();
                itemData.clickPos = this.getVector3f();
                if (this.protocol >= ProtocolInfo.v1_16_210) {
                    itemData.blockRuntimeId = (int) this.getUnsignedVarInt();
                    if (this.protocol >= ProtocolInfo.v1_21_20) {
                        itemData.clientInteractPrediction = this.protocol >= ProtocolInfo.v1_26_30 ? this.getByte() & 0xff : (int) this.getUnsignedVarInt();
                    }
                }
                if (this.protocol >= ProtocolInfo.v1_26_10) {
                    itemData.clientCooldownState = (byte) this.getByte();
                }

                this.transactionData = itemData;
                break;
            case TYPE_USE_ITEM_ON_ENTITY:
                UseItemOnEntityData useItemOnEntityData = new UseItemOnEntityData();

                useItemOnEntityData.entityRuntimeId = this.getEntityRuntimeId();
                useItemOnEntityData.actionType = this.getTransactionActionType();
                useItemOnEntityData.hotbarSlot = this.getVarInt();
                useItemOnEntityData.itemInHand = this.protocol >= ProtocolInfo.v1_26_30 ? this.getNetworkItemStackDescriptor(this.gameVersion) : this.getSlot(this.gameVersion);
                useItemOnEntityData.playerPos = this.getVector3f().asVector3();
                useItemOnEntityData.clickPos = this.getVector3f().asVector3();

                this.transactionData = useItemOnEntityData;
                break;
            case TYPE_RELEASE_ITEM:
                ReleaseItemData releaseItemData = new ReleaseItemData();

                releaseItemData.actionType = this.getTransactionActionType();
                releaseItemData.hotbarSlot = getVarInt();
                releaseItemData.itemInHand = this.protocol >= ProtocolInfo.v1_26_30 ? this.getNetworkItemStackDescriptor(this.gameVersion) : this.getSlot(this.gameVersion);
                releaseItemData.headRot = this.getVector3f().asVector3();

                this.transactionData = releaseItemData;
                break;
            default:
                throw new RuntimeException("Unknown transaction type " + this.transactionType);
        }
    }

    private int getTransactionActionType() {
        return this.protocol >= ProtocolInfo.v1_26_30 ? this.getVarInt() : (int) this.getUnsignedVarInt();
    }

    private void putTransactionActionType(int actionType) {
        if (this.protocol >= ProtocolInfo.v1_26_30) {
            this.putVarInt(actionType);
        } else {
            this.putUnsignedVarInt(actionType);
        }
    }

    private boolean hasLegacySlots() {
        return this.legacyRequestId < -1 && (this.legacyRequestId & 1) == 0;
    }

    private void readLegacySlots() {
        this.legacySlots = new ArrayList<>();
        int length = (int) this.getUnsignedVarInt();
        if (length < 0 || length > MAX_LEGACY_SLOT_ENTRIES) {
            throw new IllegalStateException("Invalid legacy slot count: " + length);
        }
        for (int i = 0; i < length; i++) {
            int containerId = this.getByte();
            int bufLen = (int) this.getUnsignedVarInt();
            if (bufLen < 0 || bufLen > MAX_LEGACY_SLOT_BLOB_LENGTH) {
                throw new IllegalStateException("Invalid legacy slot blob length: " + bufLen);
            }
            this.legacySlots.add(new LegacySetItemSlotData(containerId, this.get(bufLen)));
        }
    }

    private void putLegacySlots() {
        this.putUnsignedVarInt(this.legacySlots == null ? 0 : this.legacySlots.size());
        if (this.legacySlots != null) {
            for (LegacySetItemSlotData legacySlot : this.legacySlots) {
                this.putByte((byte) legacySlot.containerId);
                this.putUnsignedVarInt(legacySlot.slots == null ? 0 : legacySlot.slots.length);
                if (legacySlot.slots != null) {
                    this.put(legacySlot.slots);
                }
            }
        }
    }

    public static class LegacySetItemSlotData {
        public final int containerId;
        public final byte[] slots;

        public LegacySetItemSlotData(int containerId, byte[] slots) {
            this.containerId = containerId;
            this.slots = slots;
        }

        @Override
        public LegacySetItemSlotData clone() {
            return new LegacySetItemSlotData(this.containerId, this.slots == null ? null : this.slots.clone());
        }
    }
}
