package cn.nukkit;

import cn.nukkit.block.*;
import cn.nukkit.event.block.WaterFrostEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerFrostReadCostTest {
    @BeforeAll static void init() { MockServer.init(); }
    static class Harness extends Player {
        Harness() { super(null,0L,null); }
        @Override public boolean isSpectator() { return false; }
        void setup(Level world) {
            level=world;server=Server.getInstance();y=-20;
            inventory=mock(PlayerInventory.class);
            Item boots=Item.get(Item.DIAMOND_BOOTS);
            boots.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_FROST_WALKER).setLevel(2));
            when(inventory.getBootsFast()).thenReturn(boots);
        }
    }
    static Harness player(Level level) throws Exception {
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        Harness p=(Harness)((sun.misc.Unsafe)field.get(null)).allocateInstance(Harness.class);
        p.setup(level);return p;
    }
    void verifyScan(Block below,Block above,int upperReads,int frostEvents) throws Exception {
        Level level=mock(Level.class);
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        AtomicInteger lower=new AtomicInteger(),upper=new AtomicInteger(),events=new AtomicInteger();
        when(level.getBlock(anyInt(),anyInt(),anyInt())).thenAnswer(call->{
            int y=call.getArgument(1);
            if(y==-21) { lower.incrementAndGet();return below; }
            assertEquals(-20,y);upper.incrementAndGet();return above;
        });
        var manager=Server.getInstance().getPluginManager();
        doAnswer(call->{
            if(call.getArgument(0) instanceof WaterFrostEvent event) {
                events.incrementAndGet();event.setCancelled(true);
            }
            return null;
        }).when(manager).callEvent(any());
        player(level).handleEnchantmentInMove();
        assertEquals(81,lower.get());assertEquals(upperReads,upper.get());assertEquals(frostEvents,events.get());
        verify(level,never()).setBlock(any(),any(),anyBoolean(),anyBoolean());
    }
    @Test void dryGroundNeverReadsAbove() throws Exception { verifyScan(new BlockStone(),new BlockAir(),0,0); }
    @Test void flowingWaterNeverReadsAbove() throws Exception { verifyScan(new BlockWater(2),new BlockAir(),0,0); }
    @Test void coveredSourceDoesNotFreeze() throws Exception { verifyScan(new BlockWater(),new BlockStone(),81,0); }
    @Test void sourceWaterKeepsCancellableEvents() throws Exception { verifyScan(new BlockWater(),new BlockAir(),81,81); }
    @Test void acceptedEventsFreezeInScanOrderAndScheduleExactlyOnce() throws Exception {
        Level level=mock(Level.class);
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        Map<String,Block> frozen=new HashMap<>();List<String> order=new ArrayList<>();
        when(level.getBlock(anyInt(),anyInt(),anyInt())).thenAnswer(call->{
            int x=call.getArgument(0),y=call.getArgument(1),z=call.getArgument(2);
            Block block=y==-20 ? new BlockAir() : frozen.getOrDefault(x+":"+z,new BlockWater());
            block.x=x;block.y=y;block.z=z;block.level=level;return block;
        });
        when(level.getBlock(any(cn.nukkit.math.Vector3.class))).thenAnswer(call->{
            cn.nukkit.math.Vector3 pos=call.getArgument(0);
            return level.getBlock(pos.getFloorX(),pos.getFloorY(),pos.getFloorZ());
        });
        when(level.setBlock(any(),any(),eq(true),eq(false))).thenAnswer(call->{
            cn.nukkit.math.Vector3 pos=call.getArgument(0);Block ice=call.getArgument(1);
            assertEquals(Block.FROSTED_ICE,ice.getId());
            frozen.put(pos.getFloorX()+":"+pos.getFloorZ(),ice);return true;
        });
        var manager=Server.getInstance().getPluginManager();
        doAnswer(call->{
            if(call.getArgument(0) instanceof WaterFrostEvent event) {
                order.add(event.getBlock().getFloorX()+":"+event.getBlock().getFloorZ());
            }
            return null;
        }).when(manager).callEvent(any());
        Harness p=player(level);p.handleEnchantmentInMove();
        List<String> expected=new ArrayList<>();
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)expected.add(x+":"+z);
        assertEquals(expected,order);assertEquals(81,frozen.size());
        verify(level,times(81)).scheduleUpdate(any(Block.class),intThat(n->n>=20&&n<40));
        p.handleEnchantmentInMove();
        assertEquals(expected,order,"Already frozen cells must not fire again");
        verify(level,times(81)).scheduleUpdate(any(Block.class),anyInt());
    }
}
