package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.event.inventory.FurnaceBurnEvent;
import cn.nukkit.event.inventory.FurnaceSmeltEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.ContainerSetDataPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author MagicDroidX
 */
public class BlockEntityFurnace extends BlockEntitySpawnable implements InventoryHolder, BlockEntityContainer, BlockEntityNameable {

    protected FurnaceInventory inventory;

    protected int burnTime;
    protected int burnDuration;
    protected int cookTime;
    protected int maxTime;

    protected int crackledTime;

    protected float experience;

    public BlockEntityFurnace(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    protected InventoryType getInventoryType() {
        return InventoryType.FURNACE;
    }

    @Override
    protected void initBlockEntity() {
        if (this instanceof BlockEntityBlastFurnace) {
            this.inventory = new BlastFurnaceInventory((BlockEntityBlastFurnace) this);
        } else if (this instanceof BlockEntitySmoker) {
            this.inventory = new SmokerInventory((BlockEntitySmoker) this);
        } else {
            this.inventory = new FurnaceInventory(this);
        }

        if (!(this.namedTag.get("Items") instanceof ListTag)) {
            this.namedTag.putList(new ListTag<CompoundTag>("Items"));
        }

        for (int i = 0; i < this.getSize(); i++) {
            this.inventory.setItem(i, this.getItem(i));
        }

        int burnTimeTag = this.namedTag.getShort("BurnTime");
        int cookTimeTag = this.namedTag.getShort("CookTime");
        int burnDurationTag = this.namedTag.getShort("BurnDuration");

        if (!this.namedTag.contains("BurnTime") || burnTimeTag < 0) {
            burnTime = 0;
        } else {
            burnTime = burnTimeTag;
        }

        if (!this.namedTag.contains("CookTime") || cookTimeTag < 0 || (burnTimeTag == 0 && cookTimeTag > 0)) {
            cookTime = 0;
        } else {
            cookTime = cookTimeTag;
        }

        if (!this.namedTag.contains("BurnDuration") || burnDurationTag < 0) {
            burnDuration = 0;
        } else {
            burnDuration = burnDurationTag;
        }

        if (!this.namedTag.contains("MaxTime")) {
            maxTime = burnTime;
            burnDuration = 0;
        } else {
            maxTime = this.namedTag.getShort("MaxTime");
        }

        if (this.namedTag.contains("BurnTicks")) {
            burnDuration = this.namedTag.getShort("BurnTicks");
            this.namedTag.remove("BurnTicks");
        }

        if (this.namedTag.contains("StoredXpInt")) {
            experience = this.namedTag.getShort("StoredXpInt");
        } else {
            experience = 0;
        }

        if (burnTime > 0) {
            this.scheduleUpdate();
        }

        super.initBlockEntity();
    }

    protected String getFurnaceName() {
        return "Furnace";
    }

    protected String getClientName() {
        return FURNACE;
    }

    @Override
    public String getName() {
        return this.hasName() ? this.namedTag.getString("CustomName") : this.getFurnaceName();
    }

    @Override
    public boolean hasName() {
        return this.namedTag.contains("CustomName");
    }

    @Override
    public void setName(String name) {
        if (name == null || name.isEmpty()) {
            this.namedTag.remove("CustomName");
            return;
        }

        this.namedTag.putString("CustomName", name);
    }

    @Override
    public void close() {
        if (!closed) {
            for (Player player : new HashSet<>(this.inventory.getViewers())) {
                player.removeWindow(this.inventory);
            }

            super.close();
        }
    }

    @Override
    public void onBreak() {
        for (Item content : inventory.getContents().values()) {
            level.dropItem(this, content);
        }
        inventory.clearAll();
        this.releaseExperience();
    }

    @Override
    public void saveNBT() {
        this.namedTag.putList(new ListTag<CompoundTag>("Items"));
        for (int index = 0; index < this.getSize(); index++) {
            this.setItem(index, this.inventory.getItem(index));
        }

        this.namedTag.putShort("CookTime", cookTime);
        this.namedTag.putShort("BurnTime", burnTime);
        this.namedTag.putShort("BurnDuration", burnDuration);
        this.namedTag.putShort("MaxTime", maxTime);
        this.namedTag.putShort("StoredXpInt", (int) experience);
    }

    @Override
    public boolean isBlockEntityValid() {
        int blockID = getBlock().getId();
        return blockID == this.getIdleBlockId() || blockID == this.getBurningBlockId();
    }

    @Override
    public int getSize() {
        return 3;
    }

    protected int getSlotIndex(int index) {
        ListTag<CompoundTag> list = this.namedTag.getList("Items", CompoundTag.class);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getByte("Slot") == index) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public Item getItem(int index) {
        int i = this.getSlotIndex(index);
        if (i < 0) {
            return new ItemBlock(Block.get(BlockID.AIR), 0, 0);
        } else {
            CompoundTag data = (CompoundTag) this.namedTag.getList("Items").get(i);
            return NBTIO.getItemHelper(data);
        }
    }

    @Override
    public void setItem(int index, Item item) {
        int i = this.getSlotIndex(index);

        CompoundTag d = NBTIO.putItemHelper(item, index);

        if (item.getId() == Item.AIR || item.getCount() <= 0) {
            if (i >= 0) {
                this.namedTag.getList("Items").getAll().remove(i);
            }
        } else if (i < 0) {
            (this.namedTag.getList("Items", CompoundTag.class)).add(d);
        } else {
            (this.namedTag.getList("Items", CompoundTag.class)).add(i, d);
        }
    }

    @Override
    public FurnaceInventory getInventory() {
        return inventory;
    }

    protected int getIdleBlockId() {
        return Block.FURNACE;
    }

    protected int getBurningBlockId() {
        return Block.LIT_FURNACE;
    }

    protected void setBurning(boolean burning) {
        if (burning) {
            if (this.getBlock().getId() == getIdleBlockId()) {
                this.getLevel().setBlock(this, Block.get(getBurningBlockId(), this.getBlock().getDamage()), true);
            }
        } else if (this.getBlock().getId() == getBurningBlockId()) {
            this.getLevel().setBlock(this, Block.get(getIdleBlockId(), this.getBlock().getDamage()), true);
        }
    }

    protected void checkFuel(Item fuel) {
        Item originalFuel = this.inventory.getFuel();
        if (!originalFuel.equalsExact(fuel)) return;
        FurnaceState state = new FurnaceState(this);
        FurnaceBurnEvent ev = new FurnaceBurnEvent(this, fuel.clone(), fuel.getFuelTime() == null ? 0 : fuel.getFuelTime());
        int[] approvedTime = new int[1];
        boolean[] consumeFuel = new boolean[1];
        NativeContainerTransaction.convert(this.inventory, () -> {
            this.server.getPluginManager().callEvent(ev);
            approvedTime[0] = (int) Math.ceil(ev.getBurnTime() / (float) getSpeedMultiplier());
            consumeFuel[0] = ev.isBurning();
            return !ev.isCancelled();
        }, () -> {
            int time = approvedTime[0];
            if (time <= 0 || !consumeFuel[0]) return java.util.Map.of();
            Item debit = originalFuel.clone();
            debit.setCount(debit.getCount() - 1);
            if (debit.getCount() == 0 && debit.getId() == Item.BUCKET && debit.getDamage() == 10) {
                debit.setDamage(0);
                debit.setCount(1);
            }
            return java.util.Map.of(1, debit);
        }, state::matches, state::restore, () -> {
            int time = approvedTime[0];
            maxTime = time;
            burnTime = time;
            burnDuration = 0;
            this.setBurning(true);
        });
    }

    private record FurnaceState(BlockEntityFurnace furnace, int burn, int duration, int cook,
                                int max, float xp) {
        FurnaceState(BlockEntityFurnace furnace) {
            this(furnace, furnace.burnTime, furnace.burnDuration, furnace.cookTime, furnace.maxTime, furnace.experience);
        }
        void restore() {
            furnace.burnTime = burn;
            furnace.burnDuration = duration;
            furnace.cookTime = cook;
            furnace.maxTime = max;
            furnace.experience = xp;
        }
        boolean matches() {
            return furnace.burnTime == burn && furnace.burnDuration == duration && furnace.cookTime == cook
                    && furnace.maxTime == max && Float.compare(furnace.experience, xp) == 0;
        }
    }

    protected SmeltingRecipe matchRecipe(Item raw) {
        return this.server.getCraftingManager().matchFurnaceRecipe(raw);
    }

    protected int getSpeedMultiplier() {
        return 1;
    }

    @Override
    public boolean onUpdate() {
        if (this.closed || NativeContainerTransaction.isActive(this.inventory)) {
            return false;
        }

        boolean ret = false;
        Item fuel = this.inventory.getFuel();
        Item raw = this.inventory.getSmelting();
        Item product = this.inventory.getResult();
        SmeltingRecipe smelt = this.matchRecipe(raw);
        boolean canSmelt = (smelt != null && raw.getCount() > 0 && ((smelt.getResult().equals(product, true) && product.getCount() < product.getMaxStackSize()) || product.getId() == Item.AIR));

        if (burnTime <= 0 && canSmelt && fuel.getFuelTime() != null && fuel.getCount() > 0) {
            this.checkFuel(fuel);
        }

        if (burnTime > 0) {
            burnTime--;
            int readyAt = 200 / getSpeedMultiplier();
            burnDuration = (int) Math.ceil((float) burnTime / maxTime * readyAt);

            if (this.crackledTime-- <= 0) {
                this.crackledTime = ThreadLocalRandom.current().nextInt(20, 100);
                this.getLevel().addLevelSoundEvent(this.add(0.5, 0.5, 0.5), LevelSoundEventPacket.SOUND_BLOCK_FURNACE_LIT);
            }

            if (smelt != null && canSmelt) {
                cookTime++;
                if (cookTime >= readyAt) {
                    Item productBefore = product.clone();
                    Item result = smelt.getResult();
                    Item newProduct = result.clone();
                    newProduct.setCount(product.getCount() + 1);
                    product = newProduct;

                    FurnaceSmeltEvent ev = new FurnaceSmeltEvent(this, raw, product, (float) this.server.getCraftingManager().getRecipeXp(smelt));
                    Item inputBefore = raw.clone();
                    FurnaceState state = new FurnaceState(this);
                    float[] approvedXp = new float[1];
                    boolean converted = NativeContainerTransaction.convert(this.inventory, () -> {
                        if (!this.inventory.getSmelting().equalsExact(inputBefore)
                                || !this.inventory.getResult().equalsExact(productBefore)) return false;
                        this.server.getPluginManager().callEvent(ev);
                        approvedXp[0] = ev.getXp();
                        return !ev.isCancelled();
                    }, () -> {
                        Item debit = inputBefore.clone();
                        debit.setCount(debit.getCount() - 1);
                        java.util.Map<Integer, Item> writes = new java.util.LinkedHashMap<>();
                        writes.put(2, ev.getResult());
                        writes.put(0, debit);
                        return writes;
                    }, state::matches, state::restore, () -> {
                        this.experience += approvedXp[0];
                        this.cookTime -= readyAt;
                    });

                    // A cancelled/refused conversion keeps the stock cook-progress policy.
                    if (!converted) cookTime -= readyAt;
                }
            } else if (burnTime <= 0) {
                burnTime = 0;
                cookTime = 0;
                burnDuration = 0;
            } else {
                cookTime = 0;
            }
            ret = true;
        } else {
            this.setBurning(false);
            burnTime = 0;
            cookTime = 0;
            burnDuration = 0;
            this.crackledTime = 0;
        }

        if (Server.getInstance().getTick() % 4 == 0) {
            this.sendPacket();
        }

        return ret;
    }

    protected void sendPacket() {
        for (Player player : this.inventory.getViewers()) {
            int windowId = player.getWindowId(this.inventory);
            if (windowId > 0) {
                ContainerSetDataPacket pk = new ContainerSetDataPacket();
                pk.windowId = windowId;
                pk.property = ContainerSetDataPacket.PROPERTY_FURNACE_TICK_COUNT;
                pk.value = cookTime;
                player.dataPacket(pk);

                pk = new ContainerSetDataPacket();
                pk.windowId = windowId;
                pk.property = ContainerSetDataPacket.PROPERTY_FURNACE_LIT_TIME;
                pk.value = burnDuration;
                player.dataPacket(pk);
            }
        }
    }

    @Override
    public CompoundTag getSpawnCompound() {
        CompoundTag c = new CompoundTag()
                .putString("id", this.getClientName())
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putShort("BurnDuration", burnDuration)
                .putShort("BurnTime", burnTime)
                .putShort("CookTime", cookTime);

        if (this.hasName()) {
            c.put("CustomName", this.namedTag.get("CustomName"));
        }

        return c;
    }

    public int getBurnTime() {
        return burnTime;
    }

    public void setBurnTime(int burnTime) {
        this.burnTime = burnTime;
    }

    public int getBurnDuration() {
        return burnDuration;
    }

    public void setBurnDuration(int burnDuration) {
        this.burnDuration = burnDuration;
    }

    public int getCookTime() {
        return cookTime;
    }

    public void setCookTime(int cookTime) {
        this.cookTime = cookTime;
    }

    public int getMaxTime() {
        return maxTime;
    }

    public void setMaxTime(int maxTime) {
        this.maxTime = maxTime;
    }

    public float getExperience() {
        return experience;
    }

    public void setExperience(float experience) {
        this.experience = experience;
    }

    public void releaseExperience() {
        int experience = NukkitMath.floorDouble(this.experience);
        if (experience >= 1) {
            this.experience = 0;
            this.level.dropExpOrb(this, experience);
        }
    }
}
