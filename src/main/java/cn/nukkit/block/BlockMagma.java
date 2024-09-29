package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.block.BlockFormEvent;
import cn.nukkit.event.entity.EntityDamageByBlockEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Level;
import cn.nukkit.potion.Effect;

public class BlockMagma extends BlockSolid {

    public BlockMagma(){

    }

    @Override
    public int getId() {
        return MAGMA;
    }

    @Override
    public String getName() {
        return "Magma Block";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getHardness() {
        return 0.5;
    }

    @Override
    public double getResistance() {
        return 30;
    }

    @Override
    public int getLightLevel() {
        return 3;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_WOODEN) {
            return new Item[]{
                    toItem()
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }

    @Override
    public void onEntityCollide(Entity entity) {
        if (entity.hasEffect(Effect.FIRE_RESISTANCE)) {
            return;
        }

        if (entity instanceof Player p) {
            if (p.getInventory().getBoots().getEnchantment(Enchantment.ID_FROST_WALKER) != null
                    || p.isCreative() || p.isSpectator() || p.isSneaking() || !p.level.gameRules.getBoolean(GameRule.FIRE_DAMAGE)) {
                return;
            }
        }

        entity.attack(new EntityDamageByBlockEvent(this, entity, EntityDamageEvent.DamageCause.HOT_FLOOR, 1));
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            Block up = up();
            if (up instanceof BlockWater && (up.getDamage() == 0 || up.getDamage() == 8)) {
                BlockFormEvent event = new BlockFormEvent(up, new BlockBubbleColumn(1));
                if (!event.isCancelled()) {
                    if (event.getNewState().getWaterloggingType() != WaterloggingType.NO_WATERLOGGING) {
                        this.getLevel().setBlock(up, 1, new BlockWater(), true, false);
                    }
                    this.getLevel().setBlock(up, 0, event.getNewState(), true, true);
                }
            }
        }
        return 0;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

}