package cn.nukkit.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.player.PlayerOffhandInventoryChangeEvent;
import java.util.function.Consumer;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTotem;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.potion.Effect;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Native Player.attack and real inventories; dispatch/networking mocked. Portable behavioral notice contract against native public977 + public1120. Missing API classes are not the baseline assertion. */
class NativeTotemCommittedNoticeTest {
    private static final Set<String> REAL = Set.of("attack", "getLastDamageCause", "setLastDamageCause",
            "addEffect", "removeAllEffects", "removeEffect", "getEffect", "hasEffect", "getEffects",
            "canBeAffected", "setAbsorption", "getAbsorption", "damageArmor",
            "calculateEnchantmentProtectionFactor", "recalculateResistanceDamage", "heal", "setHealth", "getMaxHealth");

    @BeforeAll
    static void initialize() { cn.nukkit.MockServer.init(); Effect.init(); Enchantment.init(); Attribute.init(); }

    @Test void consumedOrdinaryMainhandTotemMustEmitOneDeed() throws Exception { rescue(false, false, false); }
    @Test void consumedOrdinaryOffhandTotemMustEmitOneDeed() throws Exception { rescue(true, false, false); }
    @Test void nativeMainhandConsumptionWithAnotherTotemRemainingMustEmitOneDeed() throws Exception { rescue(false, true, false); }
    @Test void opaqueSnapshotRemainsDetached() throws Exception { rescue(false, false, true); }

    private void rescue(boolean offhandOnly, boolean bothHands, boolean temporary) throws Exception {
        try (Fixture f = new Fixture()) {
            Item totem = new ItemTotem(0);
            if (temporary) totem.setNamedTag(new CompoundTag().putBoolean("opaque_fixture_marker", true));
            if (offhandOnly) f.offhand.setItem(0, totem); else f.inventory.setItemInHand(totem);
            if (bothHands) f.offhand.setItem(0, new ItemTotem(0));
            EntityDamageEvent damage = new EntityDamageEvent(f.player, EntityDamageEvent.DamageCause.MAGIC, 100);
            boolean accepted = f.player.attack(damage);
            f.trace.add("native-return:totems=" + f.totems() + ",deeds=" + f.receipts.size());
            System.out.println("TRACE " + offhandOnly + "/" + bothHands + "/" + temporary + " " + f.trace);
            assertFalse(accepted);
            assertTrue(damage.isCancelled());
            assertEquals(1, f.animations);
            assertTrue(f.player.isAlive());
            assertEquals(1f, f.player.getHealth());
            assertEquals(bothHands ? 1 : 0, f.totems());
            assertEquals(1, f.receipts.size());
            assertEquals(1, noticeItem(f.receipts.get(0)).getCount());
            assertEquals(offhandOnly, (Boolean) noticeValue(f.receipts.get(0), "isOffhand"));
            assertSame(damage, noticeValue(f.receipts.get(0), "getDamageEvent"));
            if (temporary) assertTrue(noticeItem(f.receipts.get(0)).getNamedTag().getBoolean("opaque_fixture_marker"));
        }
    }

    @Test void cancelledHitNeverConsumesOrAwards() throws Exception {
        try (Fixture f = new Fixture()) {
            f.inventory.setItemInHand(new ItemTotem(0)); f.cancel = true;
            assertFalse(f.player.attack(new EntityDamageEvent(f.player, EntityDamageEvent.DamageCause.MAGIC, 100)));
            assertEquals(1, f.totems());
            assertEquals(0, f.animations);
            assertTrue(f.receipts.isEmpty());
        }
    }

    @Test void nonlethalHitNeverConsumesOrAwards() throws Exception {
        try (Fixture f = new Fixture()) {
            f.inventory.setItemInHand(new ItemTotem(0));
            assertTrue(f.player.attack(new EntityDamageEvent(f.player, EntityDamageEvent.DamageCause.MAGIC, 1)));
            assertEquals(1, f.totems());
            assertEquals(0, f.animations);
            assertTrue(f.receipts.isEmpty());
        }
    }

    @Test void inventoryAndOffhandVetoDoNotConfirmConsumption() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            try (Fixture f = new Fixture()) {
                boolean left = mode != 0;
                if (left) f.offhand.setItem(0, new ItemTotem(0)); else f.inventory.setItemInHand(new ItemTotem(0));
                final int kind = mode;
                f.callback = e -> {
                    if (kind != 2 && e instanceof EntityInventoryChangeEvent change) change.setCancelled(true);
                    if (kind == 2 && e instanceof PlayerOffhandInventoryChangeEvent change) change.setCancelled(true);
                };
                f.player.attack(new EntityDamageEvent(f.player, EntityDamageEvent.DamageCause.MAGIC, 100));
                assertEquals(1, f.totems());
                assertEquals(1f, f.player.getHealth());
                assertEquals(1, f.animations);
                assertTrue(f.receipts.isEmpty());
            }
        }
    }

    @Test void unrelatedRemovalDuringNonlethalDamageProducesNoReceipt() throws Exception {
        try (Fixture f = new Fixture()) {
            f.inventory.setItemInHand(new ItemTotem(0));
            f.callback = e -> { if (e instanceof EntityDamageEvent) f.inventory.clear(0); };
            assertTrue(f.player.attack(new EntityDamageEvent(f.player, EntityDamageEvent.DamageCause.MAGIC, 1)));
            assertEquals(9f, f.player.getHealth());
            assertEquals(0, f.totems());
            assertEquals(0, f.animations);
            assertTrue(f.receipts.isEmpty());
        }
    }

    @Test void replacementCountItemAndLiveIdentityCannotConfirmOuterSpend() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            try (Fixture f = new Fixture()) {
                f.inventory.setItemInHand(new ItemTotem(0));
                Item selected = f.inventory.getItemInHandFast();
                final int kind = mode;
                f.callback = e -> {
                    if (e instanceof EntityInventoryChangeEvent change) {
                        if (kind == 0) change.setNewItem(new ItemTotem(0));
                        if (kind == 1) change.setNewItem(new Item(Item.DIAMOND, 0, 1));
                        if (kind == 2) f.inventory.slots.put(0, selected.clone());
                        if (kind == 3) selected.setNamedTag(new CompoundTag().putBoolean("opaque_fixture_marker", true));
                    }
                };
                f.player.attack(new EntityDamageEvent(f.player, EntityDamageEvent.DamageCause.MAGIC, 100));
                assertTrue(f.receipts.isEmpty(), "mutation " + mode);
            }
        }
    }

    @Test void retainedMutableMetadataChangeCannotHideOpaqueMarker() throws Exception {
        try (Fixture f = new Fixture()) {
            Item totem = new ItemTotem(0);
            totem.setNamedTag(new CompoundTag().putString("name", "original"));
            f.inventory.setItemInHand(totem);
            Item selected = f.inventory.getItemInHandFast();
            CompoundTag retained = selected.getNamedTag();
            retained.setName("tag");
            f.callback = e -> {
                if (e instanceof EntityInventoryChangeEvent) retained.putBoolean("opaque_fixture_marker", true);
            };
            f.player.attack(new EntityDamageEvent(f.player, EntityDamageEvent.DamageCause.MAGIC, 100));
            assertTrue(f.receipts.isEmpty());
            assertEquals("tag", retained.getName());
        }
    }

    @Test void eventPayloadIsDetachedAndNotificationFailureCannotRepeatSpend() throws Exception {
        try (Fixture f = new Fixture()) {
            f.inventory.setItemInHand(new ItemTotem(0));
            List<Event> seen = new ArrayList<>();
            f.callback = e -> {
                if (isNotice(e)) {
                    Event receipt = e;
                    seen.add(receipt);
                    Item changed = noticeItem(receipt); changed.setCount(64);
                    changed.setNamedTag(new CompoundTag().putBoolean("opaque_fixture_marker", true));
                    throw new IllegalStateException("test notification failure");
                }
            };
            EntityDamageEvent hit = new EntityDamageEvent(f.player, EntityDamageEvent.DamageCause.MAGIC, 100);
            assertFalse(f.player.attack(hit));
            assertEquals(1, seen.size());
            assertEquals(1, noticeItem(seen.get(0)).getCount());
            assertFalse(noticeItem(seen.get(0)).hasCompoundTag());
            assertTrue(hit.isCancelled());
            assertEquals(0, f.totems());
            assertEquals(1, f.animations);
        }
    }

    @Test void bypassDamageCannotProduceConsumptionReceipt() throws Exception {
        for (EntityDamageEvent.DamageCause cause : List.of(EntityDamageEvent.DamageCause.VOID, EntityDamageEvent.DamageCause.SUICIDE)) {
            try (Fixture f = new Fixture()) {
                f.offhand.setItem(0, new ItemTotem(0));
                f.player.attack(new EntityDamageEvent(f.player, cause, 100));
                assertEquals(1, f.totems());
                assertEquals(0, f.animations);
                assertTrue(f.receipts.isEmpty());
            }
        }
    }

    @Test void effectCallbackChangingHeldSlotKeepsLegacySpendWithoutFalseAttribution() throws Exception {
        try (Fixture f = new Fixture()) {
            f.inventory.setItemInHand(new ItemTotem(0));
            f.inventory.setItem(1, new Item(Item.DIAMOND, 0, 2));
            f.callback = e -> {
                if (e instanceof cn.nukkit.event.entity.EntityPotionEffectEvent) f.inventory.setHeldItemIndex(1);
            };
            f.player.attack(new EntityDamageEvent(f.player, EntityDamageEvent.DamageCause.MAGIC, 100));
            assertEquals(Item.TOTEM, f.inventory.getItem(0).getId());
            assertEquals(1, f.inventory.getItem(1).getCount());
            assertEquals(1, f.animations);
            assertTrue(f.receipts.isEmpty());
        }
    }

    @Test void rollbackOfOuterBulkWriteCannotRetainNestedRescueReceipt() throws Exception {
        try (Fixture f = new Fixture()) {
            f.inventory.setItemInHand(new ItemTotem(0));
            java.util.concurrent.atomic.AtomicBoolean nested = new java.util.concurrent.atomic.AtomicBoolean();
            f.callback = e -> {
                if (e instanceof EntityInventoryChangeEvent change && nested.compareAndSet(false, true)) {
                    f.player.attack(new EntityDamageEvent(f.player, EntityDamageEvent.DamageCause.MAGIC, 100));
                    change.setCancelled(true);
                }
            };
            Item[] refused = f.inventory.removeItem(new ItemTotem(0));
            assertEquals(1, refused.length);
            assertEquals(1, f.totems());
            assertEquals(1f, f.player.getHealth());
            assertEquals(1, f.animations);
            assertTrue(f.receipts.isEmpty(), "Outer rollback must not leave a confirmed spend");
        }
    }

    private static boolean isNotice(Event event) {
        return event.getClass().getName().equals("cn.nukkit.event.player.PlayerTotemConsumeEvent");
    }
    private static Item noticeItem(Event event) { return (Item) noticeValue(event, "getItem"); }
    private static Object noticeValue(Event event, String method) {
        return assertDoesNotThrow(() -> event.getClass().getMethod(method).invoke(event));
    }

    private static final class Fixture implements AutoCloseable {
        final Server server = mock(Server.class, RETURNS_DEEP_STUBS);
        final MockedStatic<Server> singleton = mockStatic(Server.class);
        final Level level = mock(Level.class);
        final List<Event> receipts = new ArrayList<>();
        final List<String> trace = new ArrayList<>();
        Consumer<Event> callback = event -> {};
        final Player player;
        final PlayerInventory inventory;
        final PlayerOffhandInventory offhand;
        int animations;
        boolean cancel;

        Fixture() throws Exception {
            singleton.when(Server::getInstance).thenReturn(server);
            when(level.getServer()).thenReturn(server);
            when(level.getGameRules()).thenReturn(GameRules.getDefault());
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            player = mock(Player.class, i -> {
                if (i.getMethod().getName().equals("dataPacket") && i.getArgument(0) instanceof EntityEventPacket packet
                        && packet.event == EntityEventPacket.CONSUME_TOTEM) animations++;
                return REAL.contains(i.getMethod().getName()) ? i.callRealMethod() : RETURNS_DEFAULTS.answer(i);
            });
            put(player,"server",server); put(player,"level",level); put(player,"health",10f); put(player,"maxHealth",20);
            put(player,"absorption",0f); put(player,"effects",new HashMap<Integer,Effect>());
            put(player,"hasSpawned",new HashMap<Long,Player>()); put(player,"id",11L);
            put(player,"boundingBox",new SimpleAxisAlignedBB(0,0,0,.6,1.8,.6)); player.protocol=2169;
            when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000022"));
            when(player.getId()).thenReturn(11L); when(player.getLevel()).thenReturn(level); when(player.getServer()).thenReturn(server);
            when(player.getSaveId()).thenReturn("Player");
            when(player.isAlive()).thenAnswer(i -> (float) get(player,"health") > 0);
            when(player.getHealth()).thenAnswer(i -> get(player,"health"));
            doAnswer(i -> { put(player,"health",0f); return null; }).when(player).kill();
            inventory=new PlayerInventory(player); offhand=new PlayerOffhandInventory(player);
            put(player,"inventory",inventory); when(player.getInventory()).thenReturn(inventory); when(player.getOffhandInventory()).thenReturn(offhand);
            var pluginManager = server.getPluginManager();
            doAnswer(i -> {
                Event e=i.getArgument(0);
                if (e instanceof EntityDamageEvent damage && cancel) damage.setCancelled(true);
                callback.accept(e);
                if (isNotice(e)) receipts.add(e);
                return null;
            }).when(pluginManager).callEvent(any(Event.class));
        }
        int totems() { return count(inventory.getItemInHand())+count(offhand.getItem(0)); }
        static int count(Item i) { return i.getId()==Item.TOTEM ? i.getCount() : 0; }
        public void close() { singleton.close(); }
    }
    private static Field field(Object target,String name) throws Exception {
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass()) {
            try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f; } catch(NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void put(Object target,String name,Object value) throws Exception { field(target,name).set(target,value); }
    private static Object get(Object target,String name) throws Exception { return field(target,name).get(target); }
}
