package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.InventoryContentPacket;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.v113.ContainerSetContentPacket_v113;
import cn.nukkit.network.protocol.v113.ContainerSetSlotPacket_v113;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public abstract class BaseInventory implements Inventory {

    protected final InventoryType type;

    protected int maxStackSize = Inventory.MAX_STACK;

    protected int size;

    protected final String name;

    protected final String title;

    public final Map<Integer, Item> slots = new HashMap<>();

    protected final Set<Player> viewers = new HashSet<>();

    protected InventoryHolder holder;

    public boolean destroyed = false;

    final Item air;

    public BaseInventory(InventoryHolder holder, InventoryType type) {
        this(holder, type, new HashMap<>());
    }

    public BaseInventory(InventoryHolder holder, InventoryType type, Map<Integer, Item> items) {
        this(holder, type, items, null);
    }

    public BaseInventory(InventoryHolder holder, InventoryType type, Map<Integer, Item> items, Integer overrideSize) {
        this(holder, type, items, overrideSize, null);
    }

    public BaseInventory(InventoryHolder holder, InventoryType type, Map<Integer, Item> items, Integer overrideSize, String overrideTitle) {
        air = new ItemBlock(Block.get(BlockID.AIR, null), 0, 0);

        this.holder = holder;

        this.type = type;

        this.size = Objects.requireNonNullElseGet(overrideSize, this.type::getDefaultSize);

        if (overrideTitle != null) {
            this.title = overrideTitle;
        } else {
            this.title = this.type.getDefaultTitle();
        }

        this.name = this.type.getDefaultTitle();

        if (!(this instanceof DoubleChestInventory)) {
            this.setContents(items);
        }
    }

    @Override
    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Override
    public int getMaxStackSize() {
        return maxStackSize;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Item getItem(int index) {
        Item original = this.slots.get(index);
        if (original != null) {
            // Ensure every non-empty stack carries a valid stackNetworkId for SAI.
            if (!original.isNull() && original.getStackNetId() == 0) {
                original.autoAssignStackNetworkId();
            }
            return original.clone();
        }
        return new ItemBlock(Block.get(BlockID.AIR), null, 0);
    }

    @Override
    @ApiStatus.Internal
    public Item getUnclonedItem(int index) {
        Item item = this.slots.get(index);
        if (item != null) {
            if (!item.isNull() && item.getStackNetId() == 0) {
                item.autoAssignStackNetworkId();
            }
            return item;
        }
        return new ItemBlock(Block.get(BlockID.AIR), null, 0);
    }

    @Override
    public Item getItemFast(int index) {
        Item item = this.slots.getOrDefault(index, air);
        if (item != air && !item.isNull() && item.getStackNetId() == 0) {
            item.autoAssignStackNetworkId();
        }
        return item;
    }

    @Override
    public Map<Integer, Item> getContents() {
        return new HashMap<>(this.slots);
    }

    @Override
    public void setContents(Map<Integer, Item> items) {
        if (items.size() > this.size) {
            TreeMap<Integer, Item> newItems = new TreeMap<>(items);
            items = newItems;
            newItems = new TreeMap<>();
            int i = 0;
            for (Map.Entry<Integer, Item> entry : items.entrySet()) {
                newItems.put(entry.getKey(), entry.getValue());
                i++;
                if (i >= this.size) {
                    break;
                }
            }
            items = newItems;
        }

        // 溢出必须等所有槽位定稿后再装：逐槽写入中途 addItem 的话，
        // 溢出会被后续槽位的 setItem/clear 覆盖丢失
        // Route overflow only after every slot is final; inserting it mid-loop
        // would let later iterations overwrite or clear it.
        List<Item> deferredOverflow = new ArrayList<>();
        for (int i = 0; i < this.size; ++i) {
            if (!items.containsKey(i)) {
                if (this.slots.containsKey(i)) {
                    this.clear(i);
                }
            } else {
                Item[] parts = splitOverstack(items.get(i));
                if (this.setItem(i, parts[0])) {
                    if (parts[1] != null) {
                        deferredOverflow.add(parts[1]);
                    }
                } else {
                    this.clear(i);
                }
            }
        }
        for (Item overflow : deferredOverflow) {
            this.routeOverflow(overflow);
        }
    }

    @Override
    public boolean setItem(int index, Item item, boolean send) {
        //item = item.clone();
        if (index < 0 || index >= this.size || !this.allowedToAdd(item)) {
            return false;
        } else if (item.getId() == 0 || item.getCount() <= 0) {
            return this.clear(index, send);
        }

        InventoryHolder holder = this.getHolder();
        if (holder instanceof Entity) {
            EntityInventoryChangeEvent ev = new EntityInventoryChangeEvent((Entity) holder, this.getItem(index), item, index);
            Server.getInstance().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                this.sendSlot(index, this.getViewers());
                return false;
            }

            item = ev.getNewItem();
        }

        if (holder instanceof BlockEntity) {
            ((BlockEntity) holder).setDirty();
        }
        // Server-Authoritative Inventory requires every non-empty stack to carry a
        // positive stackNetworkId. Items created before SAI (e.g. loaded from NBT or
        // spawned by plugins) may still have id==0, which Bedrock clients interpret as
        // "empty slot" in ItemStackResponse packets and causes cursor/inventory desync.
        if (!item.isNull() && item.getStackNetId() == 0) {
            item.autoAssignStackNetworkId();
        }

        if (item instanceof ItemBundle bundle) {
            ensureUniqueBundleId(index, bundle);
        }

        Item[] parts = splitOverstack(item);
        item = parts[0];

        Item old = this.getItem(index);
        this.slots.put(index, item.clone());
        this.onSlotChange(index, old, send);
        this.routeOverflow(parts[1]);
        return true;
    }

    /**
     * 把超叠堆拆分为"本槽合法数量 + 溢出"，不动调用方传入的对象。
     * 返回数组：[0] 应写入本槽的堆（原对象或克隆），[1] 溢出堆或 null。
     * <p>
     * Split an overstacked item into the legal per-slot amount plus overflow,
     * without mutating the caller's object. Returns [the stack to store in
     * this slot (original object or a clone), the overflow or null].
     */
    protected final Item[] splitOverstack(Item item) {
        // 用 getter 而非字段：ShelfInventory 等覆写 getMaxStackSize() 收紧单槽上限
        // Use the getter, not the field: ShelfInventory etc. override getMaxStackSize()
        int limit = Math.min(item.getMaxStackSize(), this.getMaxStackSize());
        if (item.getCount() <= limit || limit <= 0) {
            return new Item[]{item, null};
        }
        Item capped = item.clone();
        capped.setCount(limit);
        Item overflow = item.clone();
        overflow.setCount(item.getCount() - limit);
        // 溢出是服务端新产生的堆，清零 netId 让下游 setItem 重新分配；
        // 否则克隆与封顶堆共享同一 netId，破坏 SAI 的堆唯一标识
        overflow.setStackNetId(0);
        return new Item[]{capped, overflow};
    }

    /**
     * 溢出部分先尝试装回本库存，装不下再在世界掉落；无位置可掉的
     * （虚拟库存）记 warn 后丢弃，保证超叠永远不会留在单个槽位里。
     * <p>
     * Route overflow back into this inventory first, then drop it in the
     * world; if there is nowhere to drop (virtual inventory), warn and discard
     * so an overstack never stays in a single slot.
     */
    protected final void routeOverflow(Item overflow) {
        if (overflow == null || overflow.isNull() || overflow.getCount() <= 0) {
            return;
        }
        for (Item drop : this.addItem(overflow)) {
            if (drop == null || drop.getCount() <= 0) {
                continue;
            }
            InventoryHolder holder = this.getHolder();
            Level level = null;
            Vector3 pos = null;
            if (holder instanceof Entity entity) {
                level = entity.getLevel();
                pos = entity;
            } else if (holder instanceof BlockEntity blockEntity) {
                level = blockEntity.getLevel();
                pos = blockEntity;
            }
            if (level != null && pos != null) {
                level.dropItem(pos, drop);
            } else {
                log.warn("Discarded overstack overflow of {}x item id {} from inventory without a droppable location",
                        drop.getCount(), drop.getId());
            }
        }
    }

    @Override
    @ApiStatus.Internal
    public void setItemForce(int index, Item item) {
        if (index < 0 || index >= this.size) {
            return;
        }
        Item old = this.getItem(index);
        Item overflow = null;
        if (item == null || item.isNull() || item.getCount() <= 0) {
            this.slots.remove(index);
        } else {
            Item[] parts = splitOverstack(item);
            item = parts[0];
            overflow = parts[1];
            if (item.getStackNetId() == 0) {
                item.autoAssignStackNetworkId();
            }
            if (item instanceof ItemBundle bundle) {
                ensureUniqueBundleId(index, bundle);
            }
            this.slots.put(index, item.clone());
        }
        InventoryHolder holder = this.getHolder();
        if (holder instanceof BlockEntity) {
            ((BlockEntity) holder).setDirty();
        }
        this.onSlotChange(index, old, false);
        this.routeOverflowForce(overflow);
    }

    /**
     * setItemForce 专用的溢出处理：契约要求不触发事件、不发网络包、
     * 不产生世界掉落，因此绕过 addItem/setItem，按堆叠上限分块直接
     * 写入空槽；装不下的记 warn 丢弃。
     * <p>
     * Overflow handling for setItemForce: the contract forbids events, network
     * packets and world drops, so chunks of up to the stack limit are written
     * straight into empty slots, bypassing addItem/setItem; whatever does not
     * fit is discarded with a warning.
     */
    protected final void routeOverflowForce(Item overflow) {
        if (overflow == null || overflow.isNull() || overflow.getCount() <= 0) {
            return;
        }
        int limit = Math.min(overflow.getMaxStackSize(), this.getMaxStackSize());
        if (limit <= 0) {
            return;
        }
        // 先合并已有同类堆叠，保持与 addItem 相同的库存填充顺序。
        // Merge into compatible partial stacks first, matching addItem ordering.
        for (int i = 0; i < this.getSize() && overflow.getCount() > 0; ++i) {
            Item slot = this.getItem(i);
            if (slot.getId() == Item.AIR || slot.getCount() <= 0 || !overflow.equals(slot)) {
                continue;
            }
            int amount = Math.min(limit - slot.getCount(), overflow.getCount());
            if (amount <= 0) continue;
            Item old = slot.clone();
            slot.setCount(slot.getCount() + amount);
            overflow.setCount(overflow.getCount() - amount);
            this.slots.put(i, slot.clone());
            this.onSlotChange(i, old, false);
        }
        for (int i = 0; i < this.getSize() && overflow.getCount() > 0; ++i) {
            Item slot = this.getItem(i);
            if (slot.getId() != Item.AIR && slot.getCount() > 0) continue;
            Item chunk = overflow.clone();
            chunk.setCount(Math.min(limit, overflow.getCount()));
            overflow.setCount(overflow.getCount() - chunk.getCount());
            // 直接写入，避免再次进入 setItemForce -> routeOverflowForce 的递归路径。
            // Write directly to avoid recursively re-entering setItemForce -> routeOverflowForce.
            Item old = this.getItem(i);
            if (chunk.getStackNetId() == 0) {
                chunk.autoAssignStackNetworkId();
            }
            if (chunk instanceof ItemBundle bundle) {
                ensureUniqueBundleId(i, bundle);
            }
            this.slots.put(i, chunk.clone());
            this.onSlotChange(i, old, false);
        }
        if (overflow.getCount() > 0) {
            log.warn("Discarded {}x item id {} overflowing setItemForce into a full inventory",
                    overflow.getCount(), overflow.getId());
        }
    }

    @Override
    public boolean contains(Item item) {
        int count = Math.max(1, item.getCount());
        boolean checkDamage = item.hasMeta() && item.getDamage() >= 0;
        boolean checkTag = item.getCompoundTag() != null;
        for (Item i : this.getContents().values()) {
            if (item.equals(i, checkDamage, checkTag)) {
                count -= i.getCount();
                if (count <= 0) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public Map<Integer, Item> all(Item item) {
        Map<Integer, Item> slots = new HashMap<>();
        boolean checkDamage = item.hasMeta() && item.getDamage() >= 0;
        boolean checkTag = item.getCompoundTag() != null;
        for (Map.Entry<Integer, Item> entry : this.getContents().entrySet()) {
            if (item.equals(entry.getValue(), checkDamage, checkTag)) {
                slots.put(entry.getKey(), entry.getValue());
            }
        }

        return slots;
    }

    @Override
    public void remove(Item item) {
        boolean checkDamage = item.hasMeta();
        boolean checkTag = item.getCompoundTag() != null;
        for (Map.Entry<Integer, Item> entry : this.getContents().entrySet()) {
            if (item.equals(entry.getValue(), checkDamage, checkTag)) {
                this.clear(entry.getKey());
            }
        }
    }

    @Override
    public int first(Item item, boolean exact) {
        int count = Math.max(1, item.getCount());
        boolean checkDamage = item.hasMeta();
        boolean checkTag = item.getCompoundTag() != null;
        for (Map.Entry<Integer, Item> entry : this.getContents().entrySet()) {
            if (item.equals(entry.getValue(), checkDamage, checkTag) && (entry.getValue().getCount() == count || (!exact && entry.getValue().getCount() > count))) {
                return entry.getKey();
            }
        }

        return -1;
    }

    @Override
    public int firstEmpty(Item item) {
        for (int i = 0; i < this.size; ++i) {
            if (this.getItem(i).getId() == Item.AIR) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void decreaseCount(int slot) {
        Item item = this.getItem(slot);

        if (item.getCount() > 0) {
            item.count--;
            this.setItem(slot, item);
        }
    }

    @Override
    public boolean canAddItem(Item item) {
        int count = item.getCount();
        boolean checkDamage = item.hasMeta();
        boolean checkTag = item.getCompoundTag() != null;
        int i1 = this.getSize();
        for (int i = 0; i < i1; ++i) {
            Item slot = this.getItemFast(i);
            int maxStackSize = Math.min(slot.getMaxStackSize(), this.getMaxStackSize());
            if (item.equals(slot, checkDamage, checkTag)) {
                int diff;
                if ((diff = maxStackSize - slot.getCount()) > 0) {
                    count -= diff;
                }
            } else if (slot.getId() == Item.AIR) {
                count -= maxStackSize;
            }

            if (count <= 0) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean allowedToAdd(Item item) {
        return true;
    }

    @Override
    public Item[] addItem(Item... slots) {
        List<Item> itemSlots = new ArrayList<>();
        for (Item slot : slots) {
            if (slot.getId() != 0 && slot.getCount() > 0) {
                itemSlots.add(slot.clone());
            }
        }

        IntList emptySlots = new IntArrayList();

        for (int i = 0; i < this.getSize(); ++i) {
            Item item = this.getItem(i);
            if (item.getId() == Item.AIR || item.getCount() <= 0) {
                emptySlots.add(i);
            }

            for (Iterator<Item> iterator = itemSlots.iterator(); iterator.hasNext();) {
                Item slot = iterator.next();
                if (slot.equals(item)) {
                    int maxStackSize = Math.min(item.getMaxStackSize(), this.getMaxStackSize());
                    if (item.getCount() < maxStackSize) {
                        int amount = Math.min(maxStackSize - item.getCount(), slot.getCount());
                        if (amount > 0) {
                            slot.setCount(slot.getCount() - amount);
                            item.setCount(item.getCount() + amount);
                            this.setItem(i, item);
                            if (slot.getCount() <= 0) {
                                iterator.remove();
                            }
                        }
                    }
                }
            }
            if (itemSlots.isEmpty()) {
                break;
            }
        }

        if (!itemSlots.isEmpty() && !emptySlots.isEmpty()) {
            for (int slotIndex : emptySlots) {
                if (!itemSlots.isEmpty()) {
                    Item slot = itemSlots.get(0);
                    int amount = Math.min(Math.min(slot.getMaxStackSize(), this.getMaxStackSize()), slot.getCount());
                    slot.setCount(slot.getCount() - amount);
                    Item item = slot.clone();
                    item.setCount(amount);
                    this.setItem(slotIndex, item);
                    if (slot.getCount() <= 0) {
                        itemSlots.remove(slot);
                    }
                }
            }
        }

        return itemSlots.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public Item[] removeItem(Item... slots) {
        List<Item> itemSlots = new ArrayList<>();
        for (Item slot : slots) {
            if (slot.getId() != 0 && slot.getCount() > 0) {
                itemSlots.add(slot.clone());
            }
        }

        for (int i = 0; i < this.size; ++i) {
            Item item = this.getItem(i);
            if (item.getId() == Item.AIR || item.getCount() <= 0) {
                continue;
            }

            for (Item slot : new ArrayList<>(itemSlots)) {
                if (slot.equals(item, item.hasMeta(), item.getCompoundTag() != null)) {
                    int amount = Math.min(item.getCount(), slot.getCount());
                    slot.setCount(slot.getCount() - amount);
                    item.setCount(item.getCount() - amount);
                    this.setItem(i, item);
                    if (slot.getCount() <= 0) {
                        itemSlots.remove(slot);
                    }
                }
            }

            if (itemSlots.isEmpty()) {
                break;
            }
        }

        return itemSlots.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public boolean clear(int index, boolean send) {
        Item old = this.slots.get(index);
        if (old != null) {
            Item item = new ItemBlock(Block.get(BlockID.AIR), null, 0);
            InventoryHolder holder = this.getHolder();
            if (holder instanceof Entity) {
                EntityInventoryChangeEvent ev = new EntityInventoryChangeEvent((Entity) holder, old, item, index);
                Server.getInstance().getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    this.sendSlot(index, this.getViewers());
                    return false;
                }
                item = ev.getNewItem();
            }

            if (holder instanceof BlockEntity) {
                ((BlockEntity) holder).setDirty();
            }

            if (item.getId() != Item.AIR) {
                this.slots.put(index, item.clone());
            } else {
                this.slots.remove(index);
            }

            this.onSlotChange(index, old, send);
        }

        return true;
    }

    @Override
    public void clearAll() {
        for (Integer index : this.getContents().keySet()) {
            this.clear(index);
        }
    }

    @Override
    public Set<Player> getViewers() {
        return viewers;
    }

    @Override
    public InventoryHolder getHolder() {
        return holder;
    }

    protected void ensureUniqueBundleId(int targetSlot, ItemBundle bundle) {
        HashSet<Integer> existingBundleIds = new HashSet<>();
        for (var entry : this.slots.entrySet()) {
            if (entry.getKey() != targetSlot) {
                collectBundleIds(entry.getValue(), existingBundleIds, new HashSet<>());
            }
        }
        while (existingBundleIds.contains(bundle.getBundleId())) {
            bundle.assignNewBundleId();
        }
    }

    private void collectBundleIds(Item item, Set<Integer> bundleIds, Set<Integer> visitedBundleIds) {
        if (!(item instanceof ItemBundle bundle)) {
            return;
        }
        int currentId = bundle.getBundleId();
        if (!visitedBundleIds.add(currentId)) {
            return;
        }
        bundleIds.add(currentId);
        for (Item nested : bundle.getInventory().getContents().values()) {
            collectBundleIds(nested, bundleIds, visitedBundleIds);
        }
    }

    @Override
    public void setMaxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
    }

    @Override
    public boolean open(Player who) {
        InventoryOpenEvent ev = new InventoryOpenEvent(this, who);
        who.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return false;
        }
        this.onOpen(who);

        return true;
    }

    @Override
    public void close(Player who) {
        this.onClose(who);
    }

    @Override
    public void onOpen(Player who) {
        this.viewers.add(who);
        if (isContainerVibrationSource() && this.holder instanceof cn.nukkit.level.Position pos) {
            who.getLevel().getVibrationManager().callVibrationEvent(
                    new cn.nukkit.level.vibration.VibrationEvent(who, pos.add(0.5, 0.5, 0.5), cn.nukkit.level.vibration.VibrationType.CONTAINER_OPEN));
        }
    }

    @Override
    public void onClose(Player who) {
        this.viewers.remove(who);
        if (isContainerVibrationSource() && this.holder instanceof cn.nukkit.level.Position pos) {
            who.getLevel().getVibrationManager().callVibrationEvent(
                    new cn.nukkit.level.vibration.VibrationEvent(who, pos.add(0.5, 0.5, 0.5), cn.nukkit.level.vibration.VibrationType.CONTAINER_CLOSE));
        }
    }

    /**
     * Whether opening/closing this inventory emits a vibration (genuine container types only).
     */
    protected boolean isContainerVibrationSource() {
        return switch (this.type) {
            case CHEST, ENDER_CHEST, DOUBLE_CHEST, FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND,
                    DISPENSER, DROPPER, HOPPER, SHULKER_BOX, BARREL, CRAFTER,
                    MINECART_CHEST, MINECART_HOPPER, CHEST_BOAT -> true;
            default -> false;
        };
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        if (send) {
            this.sendSlot(index, this.getViewers());
        }
    }

    @Override
    public void sendContents(Player player) {
        this.sendContents(new Player[]{player});
    }

    @Override
    public void sendContents(Player... players) {
        InventoryContentPacket pk = new InventoryContentPacket();
        pk.slots = new Item[this.getSize()];
        for (int i = 0; i < this.getSize(); ++i) {
            pk.slots[i] = this.getItem(i);
        }

        if (Server.getInstance().minimumProtocol <= ProtocolInfo.v1_1_0) {
            ContainerSetContentPacket_v113 pk2 = new ContainerSetContentPacket_v113();
            pk2.slots = pk.slots.clone();
            for (Player player : players) {
                if (player.protocol > ProtocolInfo.v1_1_0) {
                    continue;
                }
                pk2.eid = player.getId();
                int id = player.getWindowId(this);
                if (id == -1 || !player.spawned) {
                    this.close(player);
                    continue;
                }
                pk2.windowid = (byte) id;
                player.dataPacket(pk2);
            }
        }

        for (Player player : players) {
            if (player.protocol < ProtocolInfo.v1_2_0) {
                continue;
            }
            int id = player.getWindowId(this);
            if (id == -1) {
                this.close(player);
                continue;
            }
            pk.inventoryId = id;
            pk.containerNameData = this.resolveFullContainerName(0);
            player.dataPacket(pk);
        }
    }

    @Override
    public boolean isFull() {
        if (this.slots.size() < this.getSize()) {
            return false;
        }

        for (Item item : this.slots.values()) {
            if (item == null || item.getId() == 0 || item.getCount() < item.getMaxStackSize() || item.getCount() < this.maxStackSize) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isEmpty() {
        if (this.maxStackSize <= 0) {
            return false;
        }

        for (Item item : this.slots.values()) {
            if (item != null && item.getId() != 0 && item.getCount() > 0) {
                return false;
            }
        }

        return true;
    }

    public int getFreeSpace(Item item) {
        int maxStackSize = Math.min(item.getMaxStackSize(), this.maxStackSize);
        int space = (this.getSize() - this.slots.size()) * maxStackSize;

        for (Item slot : this.getContents().values()) {
            if (slot == null || slot.getId() == 0) {
                space += maxStackSize;
                continue;
            }

            if (slot.equals(item, true, true)) {
                space += maxStackSize - slot.getCount();
            }
        }

        return space;
    }

    @Override
    public void sendContents(Collection<Player> players) {
        this.sendContents(players.toArray(Player.EMPTY_ARRAY));
    }

    @Override
    public void sendSlot(int index, Player player) {
        this.sendSlotTo(index, player);
    }

    private void sendSlotTo(int index, Player player) {
        int id = player.getWindowId(this);
        if (id == -1) {
            this.close(player);
            return;
        }

        Item item = this.getItem(index);
        if (player.protocol >= ProtocolInfo.v1_2_0) {
            InventorySlotPacket pk = new InventorySlotPacket();
            pk.slot = index;
            pk.item = item;
            pk.inventoryId = id;
            pk.containerNameData = this.resolveFullContainerName(index);
            player.dataPacket(pk);
        } else {
            ContainerSetSlotPacket_v113 pk = new ContainerSetSlotPacket_v113();
            pk.slot = index;
            pk.item = item;
            pk.windowid = (byte) id;
            player.dataPacket(pk);
        }
    }

    @Override
    public void sendSlot(int index, Player... players) {
        Item item = this.getItem(index);

        InventorySlotPacket pk = new InventorySlotPacket();
        pk.slot = index;
        pk.item = item;
        pk.containerNameData = this.resolveFullContainerName(index);

        ContainerSetSlotPacket_v113 pk2 = null;

        for (Player player : players) {
            int id = player.getWindowId(this);
            if (id == -1) {
                this.close(player);
                continue;
            }
            if (player.protocol >= ProtocolInfo.v1_2_0) {
                pk.inventoryId = id;
                player.dataPacket(pk);
            } else {
                if (pk2 == null) {
                    pk2 = new ContainerSetSlotPacket_v113();
                    pk2.slot = index;
                    pk2.item = item.clone();
                }
                pk2.windowid = id;
                player.dataPacket(pk2);
            }
        }
    }

    @Override
    public void sendSlot(int index, Collection<Player> players) {
        this.sendSlot(index, players.toArray(Player.EMPTY_ARRAY));
    }

    @Override
    public InventoryType getType() {
        return type;
    }

    protected FullContainerName resolveFullContainerName(int index) {
        return new FullContainerName(resolveContainerSlotType(index), null);
    }

    protected ContainerSlotType resolveContainerSlotType(int index) {
        return switch (this.type) {
            case PLAYER -> {
                if (index < 9) {
                    yield ContainerSlotType.HOTBAR;
                }
                if (index < 36) {
                    yield ContainerSlotType.INVENTORY;
                }
                yield ContainerSlotType.ARMOR;
            }
            case OFFHAND -> ContainerSlotType.OFFHAND;
            case ENTITY_ARMOR -> ContainerSlotType.ARMOR;
            case BARREL -> ContainerSlotType.BARREL;
            case SHULKER_BOX -> ContainerSlotType.SHULKER_BOX;
            case FURNACE -> switch (index) {
                case 0 -> ContainerSlotType.FURNACE_INGREDIENT;
                case 1 -> ContainerSlotType.FURNACE_FUEL;
                default -> ContainerSlotType.FURNACE_RESULT;
            };
            case BLAST_FURNACE -> switch (index) {
                case 0 -> ContainerSlotType.BLAST_FURNACE_INGREDIENT;
                case 1 -> ContainerSlotType.FURNACE_FUEL;
                default -> ContainerSlotType.FURNACE_RESULT;
            };
            case SMOKER -> switch (index) {
                case 0 -> ContainerSlotType.SMOKER_INGREDIENT;
                case 1 -> ContainerSlotType.FURNACE_FUEL;
                default -> ContainerSlotType.FURNACE_RESULT;
            };
            case BREWING_STAND -> {
                if (index == 0) {
                    yield ContainerSlotType.BREWING_INPUT;
                }
                if (index == 4) {
                    yield ContainerSlotType.BREWING_FUEL;
                }
                yield ContainerSlotType.BREWING_RESULT;
            }
            case BEACON -> ContainerSlotType.BEACON_PAYMENT;
            case TRADING -> switch (index) {
                case 0 -> ContainerSlotType.TRADE2_INGREDIENT_1;
                case 1 -> ContainerSlotType.TRADE2_INGREDIENT_2;
                default -> ContainerSlotType.TRADE2_RESULT;
            };
            case HORSE -> index <= HorseInventory.SLOT_ARMOR ? ContainerSlotType.HORSE_EQUIP : ContainerSlotType.LEVEL_ENTITY;
            case UI -> ContainerSlotType.CRAFTING_INPUT;
            default -> ContainerSlotType.LEVEL_ENTITY;
        };
    }
}
