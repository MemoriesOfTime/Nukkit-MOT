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
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.recipe.RecipeRegistry;
import cn.nukkit.recipe.SmeltingRecipe;

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

        if (!this.namedTag.contains("Items") || !(this.namedTag.get("Items") instanceof ListTag)) {
            this.namedTag.putList(new ListTag<CompoundTag>("Items"));
        }

        for (int i = 0; i < this.getSize(); i++) {
            this.inventory.setItem(i, this.getItem(i));
        }

        if (!this.namedTag.contains("BurnTime") || this.namedTag.getShort("BurnTime") < 0) {
            burnTime = 0;
        } else {
            burnTime = this.namedTag.getShort("BurnTime");
        }

        if (!this.namedTag.contains("CookTime") || this.namedTag.getShort("CookTime") < 0 || (this.namedTag.getShort("BurnTime") == 0 && this.namedTag.getShort("CookTime") > 0)) {
            cookTime = 0;
        } else {
            cookTime = this.namedTag.getShort("CookTime");
        }

        if (!this.namedTag.contains("BurnDuration") || this.namedTag.getShort("BurnDuration") < 0) {
            burnDuration = 0;
        } else {
            burnDuration = this.namedTag.getShort("BurnDuration");
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
        FurnaceBurnEvent ev = new FurnaceBurnEvent(this, fuel, fuel.getFuelTime() == null ? 0 : fuel.getFuelTime());

        this.server.getPluginManager().callEvent(ev);

        if (ev.isCancelled()) {
            return;
        }

        int newBurnTime = (int) Math.ceil(ev.getBurnTime() / (float) getSpeedMultiplier());
        maxTime = newBurnTime;
        burnTime = newBurnTime;
        burnDuration = 0;
        this.setBurning(true);

        if (burnTime > 0 && ev.isBurning()) {
            fuel.setCount(fuel.getCount() - 1);
            if (fuel.getCount() == 0) {
                if (fuel.getId() == Item.BUCKET && fuel.getDamage() == 10) {
                    fuel.setDamage(0);
                    fuel.setCount(1);
                } else {
                    fuel = new ItemBlock(Block.get(BlockID.AIR), 0, 0);
                }
            }
            this.inventory.setFuel(fuel);
        }
    }

    protected SmeltingRecipe matchRecipe(int protocol, Item raw) {
        return RecipeRegistry.matchFurnaceRecipe(raw);
    }

    protected int getSpeedMultiplier() {
        return 1;
    }

    @Override
    public boolean onUpdate() {
        if (this.closed) {
            return false;
        }

        boolean ret = false;
        Item fuel = this.inventory.getFuel();
        Item raw = this.inventory.getSmelting();
        Item product = this.inventory.getResult();
        SmeltingRecipe smelt = this.matchRecipe(ProtocolInfo.CURRENT_PROTOCOL, raw);
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
                    Item result = smelt.getResult();
                    Item newProduct = Item.fromString(result.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL) + ":" + result.getDamage());
                    newProduct.setCount(product.getCount() + 1);
                    product = newProduct;

                    FurnaceSmeltEvent ev = new FurnaceSmeltEvent(this, raw, product, (float) RecipeRegistry.getRecipeXp(smelt));
                    this.server.getPluginManager().callEvent(ev);
                    if (!ev.isCancelled()) {
                        this.inventory.setResult(ev.getResult());
                        this.experience += ev.getXp();
                        raw.setCount(raw.getCount() - 1);
                        if (raw.getCount() == 0) {
                            raw = new ItemBlock(Block.get(BlockID.AIR), 0, 0);
                        }
                        this.inventory.setSmelting(raw);
                    }

                    cookTime -= readyAt;
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