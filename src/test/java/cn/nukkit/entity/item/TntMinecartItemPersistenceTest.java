package cn.nukkit.entity.item;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockRail;
import cn.nukkit.block.BlockAir;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemMinecartTNT;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TntMinecartItemPersistenceTest {
    private static Server serverMock;
    @BeforeEach void setup() { MockServer.init(); MockServer.reset(); serverMock = MockServer.get(); }
    @AfterEach void cleanup() { MockServer.reset(); }

    @Test void placementAndReloadKeepFullItemAndExactlyOneDrop() throws Exception {
        for (String marker : new String[]{"tnt_tier", "special_cart", "tnt_underwater"}) {
            Level level = newMockLevel(GameRules.getDefault());
            BaseFullChunk chunk = newMockChunk(level);
            when(level.getChunk(0, 0)).thenReturn(chunk);
            BlockRail rail = new BlockRail();
            rail.x = 0; rail.y = 64; rail.z = 0;
            ItemMinecartTNT item = new ItemMinecartTNT(0, 2);
            item.setNamedTag(new CompoundTag().putString("custom_payload", marker)
                    .putString("custom_origin", "plugin:fixture").putString("custom_serial", "fixture-instance"));
            item.setCustomName("Special minecart");
            byte[] originalItemNbt = item.getCompoundTag().clone();
            Item expected = item.clone(); expected.setCount(1);
            assertTrue(item.onActivate(level, null, rail, rail, BlockFace.UP, 0, 0, 0));
            assertEquals(1, item.getCount());
            assertArrayEquals(originalItemNbt, item.getCompoundTag(),
                    "Placing one cart must not alter the remaining original item's full NBT");
            ArgumentCaptor<Entity> spawned = ArgumentCaptor.forClass(Entity.class);
            verify(level).addEntity(spawned.capture());
            EntityMinecartTNT placed = (EntityMinecartTNT) spawned.getValue();
            placed.saveNBT();
            CompoundTag reloadedNbt = NBTIO.read(NBTIO.write(placed.namedTag));
            EntityMinecartTNT loaded = new EntityMinecartTNT(chunk, reloadedNbt);
            loaded.dropItem();
            ArgumentCaptor<Item> dropped = ArgumentCaptor.forClass(Item.class);
            verify(level).dropItem(any(Vector3.class), dropped.capture());
            assertEquals(1, dropped.getValue().getCount());
            assertTrue(expected.equalsExact(dropped.getValue()), "Reload/drop must keep charge, seal and origin: " + marker);
        }
    }

    @Test void oldCartStillDropsOrdinaryMinecart() {
        Level level = newMockLevel(GameRules.getDefault());
        EntityMinecartTNT cart = new EntityMinecartTNT(newMockChunk(level), baseNbt());
        cart.dropItem();
        ArgumentCaptor<Item> dropped = ArgumentCaptor.forClass(Item.class);
        verify(level).dropItem(any(Vector3.class), dropped.capture());
        assertTrue(new ItemMinecartTNT().equalsExact(dropped.getValue()));
    }

    @Test void storedInvalidItemCannotDropOtherGoodsOrMultipleCarts() {
        for (Item stored : new Item[]{Item.get(Item.DIAMOND, 0, 64), new ItemMinecartTNT(0, 64)}) {
            Level level = newMockLevel(GameRules.getDefault());
            CompoundTag nbt = baseNbt().putCompound("MinecartItem", NBTIO.putItemHelper(stored));
            EntityMinecartTNT cart = new EntityMinecartTNT(newMockChunk(level), nbt);
            cart.dropItem();
            ArgumentCaptor<Item> dropped = ArgumentCaptor.forClass(Item.class);
            verify(level).dropItem(any(Vector3.class), dropped.capture());
            assertEquals(Item.MINECART_WITH_TNT, dropped.getValue().getId());
            assertEquals(1, dropped.getValue().getCount());
        }
    }

    @Test void droppingAndReadingDecodedCartCannotMutateRetainedFullItemSnapshot() throws Exception {
        for (int sourceCount : new int[]{1, 64}) {
            Level level = newMockLevel(GameRules.getDefault());
            Item source = new ItemMinecartTNT(0, sourceCount).setNamedTag(new CompoundTag()
                    .putString("custom_payload", "special_cart")
                    .putString("custom_origin", "plugin:fixture")
                    .putString("custom_serial", "retained-fixture-instance")
                    .putCompound("paid_data", new CompoundTag().putString("seal", "keep-seal")
                            .putList(new ListTag<CompoundTag>("effects").add(new CompoundTag()
                                    .putString("kind", "custom-effect").putInt("power", 7)))));
            source.setCustomName("Retained custom cart");
            byte[] originalItemNbt = source.getCompoundTag().clone();
            EntityMinecartTNT cart = new EntityMinecartTNT(newMockChunk(level),
                    baseNbt().putCompound("MinecartItem", NBTIO.putItemHelper(source)));
            CompoundTag stored = cart.namedTag.getCompound("MinecartItem");
            byte[] serializedBefore = NBTIO.write(stored);
            CompoundTag independentBefore = NBTIO.read(serializedBefore);

            cart.dropItem();
            ArgumentCaptor<Item> dropped = ArgumentCaptor.forClass(Item.class);
            verify(level).dropItem(any(Vector3.class), dropped.capture());
            Item result = dropped.getValue();
            assertEquals(1, result.getCount(), "Any saved count still drops exactly one cart");
            assertNotNull(result.getNamedTag(), "Native cart drop must retain its full saved item NBT");
            assertEquals("special_cart", result.getNamedTag().getString("custom_payload"));
            assertEquals("retained-fixture-instance", result.getNamedTag().getString("custom_serial"));
            assertArrayEquals(originalItemNbt, result.getCompoundTag(), "Full custom item NBT is retained");
            result.clone().getCompoundTag();

            assertArrayEquals(serializedBefore, NBTIO.write(stored),
                    "Decode, count clamp and item reads must not edit the entity's retained record");
            assertEquals(independentBefore, stored, "Snapshot must also retain all tag names and tree values");
        }
    }

    @Test void redstoneClearsPreviousIgniterWhileManualIgniterSurvivesReload() {
        Level level = newMockLevel(GameRules.getDefault());
        BaseFullChunk chunk = newMockChunk(level);
        Player player = mock(Player.class);
        UUID actor = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(actor);
        EntityMinecartTNT cart = new EntityMinecartTNT(chunk, baseNbt());
        assertTrue(cart.onInteract(player, Item.get(Item.FLINT_AND_STEEL), new Vector3(0, 0, 0)));
        cart.saveNBT();
        EntityMinecartTNT loaded = new EntityMinecartTNT(chunk, cart.namedTag.copy());
        assertEquals(actor.toString(), loaded.namedTag.getString("TntIgniter"));
        loaded.activate(0, 64, 0, true);
        assertFalse(loaded.namedTag.contains("TntIgniter"));
    }

    @Test void malformedStoredItemFallsBackToOneOrdinaryCart() {
        for (CompoundTag malformed : new CompoundTag[]{
                NBTIO.putItemHelper(new ItemMinecartTNT()).putString("Count", "bad"),
                NBTIO.putItemHelper(new ItemMinecartTNT()).putString("Damage", "bad"),
                new CompoundTag().putInt("Name", 4).putByte("Count", 1),
                new CompoundTag().putString("id", "bad").putByte("Count", 1)}) {
            Level level = newMockLevel(GameRules.getDefault());
            EntityMinecartTNT cart = new EntityMinecartTNT(newMockChunk(level),
                    baseNbt().putCompound("MinecartItem", malformed));
            assertDoesNotThrow(cart::dropItem);
            ArgumentCaptor<Item> dropped = ArgumentCaptor.forClass(Item.class);
            verify(level).dropItem(any(Vector3.class), dropped.capture());
            assertTrue(new ItemMinecartTNT().equalsExact(dropped.getValue()));
        }
    }

    @Test void cancelledFuseExplosionLeavesRecoverableCartWithoutRepeatedPrime() {
        PluginManager plugins = mock(PluginManager.class);
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof EntityExplosionPrimeEvent prime) prime.setCancelled();
            return null;
        }).when(plugins).callEvent(any(Event.class));
        when(serverMock.getPluginManager()).thenReturn(plugins);
        Level level = newMockLevel(GameRules.getDefault());
        EntityMinecartTNT cart = new EntityMinecartTNT(newMockChunk(level), baseNbt());
        cart.noClip = true;
        when(level.getBlock(any(Vector3.class))).thenReturn(new BlockAir());
        cart.activate(0, 64, 0, true);
        cart.entityBaseTick(80);
        assertFalse(cart.closed);
        cart.saveNBT();
        assertEquals(-1, cart.namedTag.getInt("fuse"));
        cart.entityBaseTick(1);
        verify(plugins, times(1)).callEvent(isA(EntityExplosionPrimeEvent.class));
        cart.dropItem();
        verify(level).dropItem(any(Vector3.class), any(Item.class));
    }
    private static Level newMockLevel(GameRules gameRules) {
        Level level = mock(Level.class);
        lenient().when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        lenient().when(level.getServer()).thenReturn(serverMock);
        lenient().when(level.getGameRules()).thenReturn(gameRules);
        lenient().when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        lenient().when(serverMock.getTick()).thenReturn(0);
        lenient().doNothing().when(level).addEntity(any());
        try {
            Field f = Level.class.getDeclaredField("isBeingConverted");
            f.setAccessible(true);
            f.setBoolean(level, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return level;
    }

    private static BaseFullChunk newMockChunk(Level level) {
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().doNothing().when(chunk).addEntity(any());
        lenient().when(provider.getLevel()).thenReturn(level);
        return chunk;
    }

    private static CompoundTag baseNbt() {
        return new CompoundTag()
                .putList(new ListTag<>("Pos")
                        .add(new DoubleTag("", 0.5))
                        .add(new DoubleTag("", 64.0))
                        .add(new DoubleTag("", 0.5)))
                .putList(new ListTag<>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)));
    }

}
