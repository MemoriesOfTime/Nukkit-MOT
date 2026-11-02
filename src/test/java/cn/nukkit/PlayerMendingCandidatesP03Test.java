package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.math.SimpleAxisAlignedBB;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerMendingCandidatesP03Test {
    @Test
    void eachSoleEligibleSlotStillConsumesOneRandomDrawAndRepairsThatSlot() throws Exception {
        for(int selected=0;selected<6;selected++) {
            Fixture f=new Fixture(1<<selected);ThreadLocalRandom random=mock(ThreadLocalRandom.class);
            when(random.nextInt(1)).thenReturn(0);clearInvocations(f.inventory,f.offhand,random);
            try(MockedStatic<ThreadLocalRandom> current=mockStatic(ThreadLocalRandom.class)) {
                current.when(ThreadLocalRandom::current).thenReturn(random);
                assertTrue(f.player.pickupEntity(f.orb,true));current.verify(ThreadLocalRandom::current,times(1));
            }
            verify(random).nextInt(1);f.verifyRepair(selected);verify(f.orb).close();assertEquals(1,f.player.pickedXPOrb);
            verify(f.inventory,times(selected<4?1:0)).getSize();verify(f.inventory,times(selected==4?1:0)).getHeldItemIndex();
        }
    }

    @Test
    void allSixCandidatesKeepGetterOrderAndOneUniformDraw() throws Exception {
        for(int selected=0;selected<6;selected++) {
            Fixture f=new Fixture(63);ThreadLocalRandom random=mock(ThreadLocalRandom.class);
            when(random.nextInt(6)).thenReturn(selected);clearInvocations(f.inventory,f.offhand,random);
            try(MockedStatic<ThreadLocalRandom> current=mockStatic(ThreadLocalRandom.class)) {
                current.when(ThreadLocalRandom::current).thenReturn(random);assertTrue(f.player.pickupEntity(f.orb,true));current.verify(ThreadLocalRandom::current,times(1));
            }
            InOrder order=inOrder(f.inventory,f.offhand,random);
            for(int slot=0;slot<4;slot++){order.verify(f.inventory).getArmorItem(slot);order.verify(f.inventory).getSize();}
            order.verify(f.inventory).getItemInHandFast();order.verify(f.inventory).getHeldItemIndex();order.verify(f.offhand).getItem(0);order.verify(random).nextInt(6);
            if(selected<5){order.verify(f.inventory).getItem(f.slot(selected));order.verify(f.inventory).setItem(eq(f.slot(selected)),any(Item.class));}
            else order.verify(f.offhand).setItem(eq(0),any(Item.class));
            order.verifyNoMoreInteractions();f.verifyRepair(selected);
        }
    }

    @Test
    void acceptedArmorStoresItsOriginalSlotAndRereadsTheSelectedItem() throws Exception {
        Fixture f=new Fixture(15);when(f.inventory.getSize()).thenReturn(36,100,200,300);
        Item replacement=Item.get(Item.DIAMOND_SWORD,0,1);when(f.inventory.getItem(202)).thenReturn(replacement);
        ThreadLocalRandom random=mock(ThreadLocalRandom.class);when(random.nextInt(4)).thenReturn(2);clearInvocations(f.inventory,f.offhand,random);
        try(MockedStatic<ThreadLocalRandom> current=mockStatic(ThreadLocalRandom.class)) {
            current.when(ThreadLocalRandom::current).thenReturn(random);assertTrue(f.player.pickupEntity(f.orb,true));current.verify(ThreadLocalRandom::current,times(1));
        }
        verify(f.inventory,times(4)).getSize();verify(f.inventory).getItem(202);assertEquals(0,replacement.getDamage());
        verify(f.inventory,never()).setItem(anyInt(),any(Item.class));verify(f.offhand,never()).setItem(anyInt(),any(Item.class));verify(f.player).addExperience(3);
    }

    @Test
    void duplicateStoredSlotIdsKeepAllSixRandomPositions() throws Exception {
        for (int selected = 0; selected < 6; selected++) {
            Fixture f = new Fixture(63);
            when(f.inventory.getSize()).thenReturn(36, 35, 34, 33);
            when(f.inventory.getHeldItemIndex()).thenReturn(36);
            Item replacement = Item.get(Item.DIAMOND_SWORD, 100, 1);
            when(f.inventory.getItem(36)).thenReturn(replacement);
            ThreadLocalRandom random = mock(ThreadLocalRandom.class);
            when(random.nextInt(6)).thenReturn(selected);
            clearInvocations(f.inventory, f.offhand, random);
            try (MockedStatic<ThreadLocalRandom> current = mockStatic(ThreadLocalRandom.class)) {
                current.when(ThreadLocalRandom::current).thenReturn(random);
                assertTrue(f.player.pickupEntity(f.orb, true));
                current.verify(ThreadLocalRandom::current, times(1));
            }
            verify(random).nextInt(6);
            if (selected < 5) {
                verify(f.inventory).getItem(36);
                verify(f.inventory).setItem(eq(36), argThat(item -> item.getDamage() == 94));
                verify(f.offhand, never()).setItem(anyInt(), any(Item.class));
            } else {
                verify(f.offhand).setItem(eq(0), argThat(item -> item.getDamage() == 94));
                verify(f.inventory, never()).getItem(anyInt());
            }
            verify(f.player, never()).addExperience(anyInt());
        }
    }

    @Test
    void emptyCandidateSetDoesNotDrawAndCreditsTheOrbOnce() throws Exception {
        Fixture f=new Fixture(0);
        try(MockedStatic<ThreadLocalRandom> current=mockStatic(ThreadLocalRandom.class)) {
            assertTrue(f.player.pickupEntity(f.orb,true));current.verifyNoInteractions();
        }
        verify(f.inventory,never()).getSize();verify(f.inventory,never()).getHeldItemIndex();verify(f.inventory,never()).getItem(anyInt());
        verify(f.inventory,never()).setItem(anyInt(),any(Item.class));verify(f.offhand,never()).setItem(anyInt(),any(Item.class));verify(f.player).addExperience(3);verify(f.orb).close();
        assertFalse(f.player.pickupEntity(f.orb,true));verify(f.player,times(1)).addExperience(3);
    }

    private static final class Fixture {
        final Server server=mock(Server.class);final Level level=mock(Level.class);
        final Player player=mock(Player.class,CALLS_REAL_METHODS);final PlayerInventory inventory=mock(PlayerInventory.class);final PlayerOffhandInventory offhand=mock(PlayerOffhandInventory.class);final EntityXPOrb orb=mock(EntityXPOrb.class);
        final Item[] equipment=new Item[6];
        Fixture(int mask)throws Exception {
            set(player,"server",server);set(player,"inventory",inventory);set(player,"offhandInventory",offhand);player.spawned=true;player.boundingBox=new SimpleAxisAlignedBB(-1,-1,-1,1,1,1);
            when(server.getTick()).thenReturn(1);doReturn(true).when(player).isAlive();doReturn(true).when(player).isOnline();doReturn(false).when(player).isSpectator();doReturn(level).when(player).getLevel();doNothing().when(player).addExperience(anyInt());when(orb.getExp()).thenReturn(3);when(inventory.getSize()).thenReturn(36);when(inventory.getHeldItemIndex()).thenReturn(7);
            int[] ids={Item.DIAMOND_HELMET,Item.DIAMOND_CHESTPLATE,Item.DIAMOND_LEGGINGS,Item.DIAMOND_BOOTS,Item.DIAMOND_SWORD,Item.SHIELD};
            for(int i=0;i<6;i++){equipment[i]=(mask&(1<<i))==0?Item.AIR_ITEM:Item.get(ids[i],100,1);if(!equipment[i].isNull())equipment[i].addEnchantment(Enchantment.get(Enchantment.ID_MENDING));if(i<4)when(inventory.getArmorItem(i)).thenReturn(equipment[i]);else if(i==4)when(inventory.getItemInHandFast()).thenReturn(equipment[i]);else when(offhand.getItem(0)).thenReturn(equipment[i]);if(i<5)when(inventory.getItem(slot(i))).thenReturn(equipment[i].clone());}
        }
        int slot(int position){return position<4?36+position:7;}
        void verifyRepair(int selected){if(selected==5){verify(offhand).setItem(eq(0),argThat(item->item.getDamage()==94));verify(inventory,never()).setItem(anyInt(),any(Item.class));}else{verify(inventory).setItem(eq(slot(selected)),argThat(item->item.getDamage()==94));verify(offhand,never()).setItem(anyInt(),any(Item.class));}verify(player,never()).addExperience(anyInt());}
        static void set(Object target,String name,Object value)throws Exception {for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    }
}
