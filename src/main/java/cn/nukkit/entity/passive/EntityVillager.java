package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.data.profession.Profession;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.inventory.TradeInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import lombok.Getter;

public class EntityVillager extends EntityWalkingAnimal implements InventoryHolder {

    public static final int PROFESSION_FARMER = 0;
    public static final int PROFESSION_LIBRARIAN = 1;
    public static final int PROFESSION_PRIEST = 2;
    public static final int PROFESSION_BLACKSMITH = 3;
    public static final int PROFESSION_BUTCHER = 4;
    public static final int PROFESSION_GENERIC = 5;

    public static final int NETWORK_ID = 15;

    /**
     * 代表交易配方
     */
    @Getter
    protected ListTag<Tag> recipes = new ListTag<>("Recipes");
    /**
     * 用于控制村民的等级成长所需要的经验
     * 例如[0,10,20,30,40] 村民达到1级所需经验0,2级为10,这里的经验是{@link EntityVillager#tradeExp}.
     */
    public int[] tierExpRequirement;

    protected TradeInventory inventory;
    /**
     * 用于控制该村民是否可以交易
     */
    protected Boolean canTrade;
    /**
     * 代表交易UI上方所显示的名称,在原版为村民的职业名
     */
    protected String displayName;
    /**
     * 代表村民当前的交易等级
     */
    protected int tradeTier;
    /**
     * 代表村民所允许的最大交易等级
     */
    protected int maxTradeTier;
    /**
     * 代表当前村民的经验,不允许为负数
     */
    protected int tradeExp;

    protected int tradeSeed;

    /**
     * 代表村民的职业<br>
     * 0 generic 普通<br>
     * 1 farmer 农民<br>
     * 2 fisherman 渔民<br>
     * 3 shepherd 牧羊人<br>
     * 4 fletcher 制箭师<br>
     * 5 librarian 图书管理员<br>
     * 6 cartographer 制图师<br>
     * 7 cleric 牧师<br>
     * 8 armor 盔甲匠<br>
     * 9 weapon 武器匠<br>
     * 10 tool 工具匠<br>
     * 11 butcher 屠夫<br>
     * 12 butcher 皮匠<br>
     * 13 mason 石匠<br>
     * 14 nitwit 傻子<br>
     */
    protected int profession;

    {
        this.tierExpRequirement = new int[]{0, 10, 70, 150, 250};
    }

    public EntityVillager(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getKillExperience() {
        return 0;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.3f;
        }
        return 0.6f;
    }

    @Override
    public float getHeight() {
        if (this.isBaby()) {
            return 0.95f;
        }
        return 1.9f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(20);
        super.initEntity();
        setTradingPlayer(0L);
        if (!this.namedTag.contains("profession")) {
            this.setProfession(0);
        } else {
            var profession = this.namedTag.getInt("profession");
            this.profession = profession;
            this.setDataProperty(new IntEntityData(DATA_VARIANT, profession));
        }
        if (!this.namedTag.contains("tradeSeed")) {
            this.setTradeSeed(new NukkitRandom().nextBoundedInt(Integer.MAX_VALUE));
        } else {
            this.tradeSeed = this.namedTag.getInt("tradeSeed");
        }
        if (!this.namedTag.contains("canTrade")) {
            this.setCanTrade(!(profession == 0 || profession == 14));
        } else {
            this.canTrade = this.namedTag.getBoolean("canTrade");
        }
        if (!this.namedTag.contains("displayName") && profession != 0) {
            this.setDisplayName(getProfessionName(profession));
        } else {
            this.displayName = this.namedTag.getString("displayName");
        }
        if (!this.namedTag.contains("tradeTier")) {
            this.setTradeTier(1);
        } else {
            this.tradeTier = this.namedTag.getInt("tradeTier");
        }
        if (!this.namedTag.contains("maxTradeTier")) {
            this.setMaxTradeTier(5);
        } else {
            var maxTradeTier = this.namedTag.getInt("maxTradeTier");
            this.maxTradeTier = maxTradeTier;
            this.setDataProperty(new IntEntityData(DATA_MAX_TRADE_TIER, maxTradeTier));
        }
        if (!this.namedTag.contains("tradeExp")) {
            this.setTradeExp(0);
        } else {
            var tradeExp = this.namedTag.getInt("tradeExp");
            this.tradeExp = tradeExp;
            this.setDataProperty(new IntEntityData(DATA_TRADE_EXPERIENCE, tradeExp));
        }
        Profession profession = Profession.getProfession(this.profession);
        if (profession != null) {
            applyProfession(profession);
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putByte("Profession", this.getProfession());
        this.namedTag.putBoolean("isTrade", this.getCanTrade());
        this.namedTag.putString("displayName", this.getDisplayName());
        this.namedTag.putInt("tradeTier", this.getTradeTier());
        this.namedTag.putInt("maxTradeTier", this.getMaxTradeTier());
        this.namedTag.putInt("tradeExp", this.getTradeExp());
        this.namedTag.putInt("tradeSeed", this.getTradeSeed());
    }

    /**
     * 获取村民职业id对应的displayName硬编码
     */
    private String getProfessionName(int profession) {
        return switch (profession) {
            case 1 -> "entity.villager.farmer";
            case 2 -> "entity.villager.fisherman";
            case 3 -> "entity.villager.shepherd";
            case 4 -> "entity.villager.fletcher";
            case 5 -> "entity.villager.librarian";
            case 6 -> "entity.villager.cartographer";
            case 7 -> "entity.villager.cleric";
            case 8 -> "entity.villager.armor";
            case 9 -> "entity.villager.weapon";
            case 10 -> "entity.villager.tool";
            case 11 -> "entity.villager.butcher";
            case 12 -> "entity.villager.leather";
            case 13 -> "entity.villager.mason";
            default -> null;
        };
    }

    /**
     * @return 村民的职业id
     */
    public int getProfession() {
        return profession;
    }

    /**
     * 设置村民职业
     *
     * @param profession 请查看{@link EntityVillager#profession}
     */
    public void setProfession(int profession) {
        this.profession = profession;
        this.setDataProperty(new IntEntityData(DATA_VARIANT, profession));
        this.namedTag.putInt("profession", this.profession);
    }

    /**
     * 这个方法插件一般不用
     */
    public void setTradingPlayer(Long eid) {
        this.setDataProperty(new LongEntityData(DATA_TRADING_PLAYER_EID, eid));
    }

    /**
     * @return 该村民是否可以交易
     */
    public boolean getCanTrade() {
        return false;
    }

    /**
     * 设置村民是否可以交易
     *
     * @param canTrade true 可以交易
     */
    public void setCanTrade(boolean canTrade) {
        this.canTrade = canTrade;
        this.namedTag.putBoolean("canTrade", canTrade);
    }

    /**
     * @return 交易UI的显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param displayName 设置交易UI的显示名称
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.namedTag.putString("displayName", displayName);
    }

    /**
     * @return 该村民的交易等级
     */
    public int getTradeTier() {
        return tradeTier;
    }

    /**
     * @param tradeTier <p>村民的交易等级(1-{@link EntityVillager#maxTradeTier})</p>
     */
    public void setTradeTier(int tradeTier) {
        this.tradeTier = --tradeTier;
        this.namedTag.putInt("tradeTier", this.tradeTier);
    }

    /**
     * @return 村民所允许的最大交易等级
     */
    public int getMaxTradeTier() {
        return maxTradeTier;
    }

    /**
     * @param maxTradeTier 设置村民所允许的最大交易等级
     */
    public void setMaxTradeTier(int maxTradeTier) {
        this.maxTradeTier = maxTradeTier;
        this.setDataProperty(new IntEntityData(DATA_MAX_TRADE_TIER, 5));
        this.namedTag.putInt("maxTradeTier", this.tradeTier);
    }

    /**
     * @return 村民当前的经验值
     */
    public int getTradeExp() {
        return tradeExp;
    }

    /**
     * @param tradeExp 设置村民当前的经验值
     */
    public void setTradeExp(int tradeExp) {
        this.tradeExp = tradeExp;
        this.setDataProperty(new IntEntityData(DATA_TRADE_EXPERIENCE, 10));
        this.namedTag.putInt("tradeExp", this.tradeTier);
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (this.getCanTrade()) {
            var inv = new TradeInventory(this);
            player.addWindow(inv, Player.TRADE_WINDOW_ID);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public TradeInventory getInventory() {
        return inventory;
    }

    public int getTradeSeed() {
        return tradeSeed;
    }

    protected void setTradeSeed(int tradeSeed) {
        this.tradeSeed = tradeSeed;
        this.namedTag.putInt("tradeSeed", tradeSeed);
    }

    public void addExperience(int xp) {
        this.tradeExp += xp;
        this.setDataProperty(new IntEntityData(DATA_TRADE_EXPERIENCE, this.tradeExp));
        int next = getTradeTier()+1;
        if (next < this.tierExpRequirement.length) {
            if (tradeExp >= this.tierExpRequirement[next]) {
                setTradeTier(next+1);
            }
        }
    }

    @Override
    public boolean onUpdate(int tick) {
        if (tick % 100 == 0) {
            if (profession != 0) {
                if (recipes.getAll().size() == 0) {
                    applyProfession(Profession.getProfession(this.profession));
                }
            }
            if (tradeExp == 0 && !this.namedTag.contains("traded")) {
                NukkitRandom nukkitRandom = new NukkitRandom();
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        Block block = getLocation().add(x, 0, z).getLevelBlock();
                        int id = block.getId();
                        for (Profession profession : Profession.getProfessions().values()) {
                            if (id == profession.getBlockID()) {
                                if (this.profession != profession.getIndex()) {
                                    this.setTradeSeed(nukkitRandom.nextBoundedInt(Integer.MAX_VALUE));
                                    this.setProfession(profession.getIndex());
                                    this.applyProfession(profession);

                                    this.namedTag.putInt("blockX", block.getFloorX());
                                    this.namedTag.putInt("blockY", block.getFloorY());
                                    this.namedTag.putInt("blockZ", block.getFloorZ());
                                }
                                break;
                            }
                        }
                    }
                }
                if (this.profession != 0 && !this.namedTag.contains("traded")) {
                    int x = this.namedTag.getInt("blockX");
                    int y = this.namedTag.getInt("blockY");
                    int z = this.namedTag.getInt("blockZ");
                    if (level.getBlock(x, y, z).getId() != Profession.getProfession(this.profession).getBlockID()) {
                        setProfession(0);
                        setCanTrade(false);
                    }
                }
            }
        }
        return super.onUpdate(tick);
    }

    public void applyProfession(Profession profession) {
        setDisplayName(profession.getName());
        recipes = profession.buildTrades(getTradeSeed());
        this.setCanTrade(true);
    }
}
