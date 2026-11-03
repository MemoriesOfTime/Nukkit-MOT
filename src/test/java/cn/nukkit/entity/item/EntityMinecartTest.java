package cn.nukkit.entity.item;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntityHopper;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityBlazeFireBall;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.regression.PacketBridgeUtil;
import cn.nukkit.network.protocol.regression.ProtocolCodecMapping;
import cn.nukkit.plugin.PluginManager;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that the command block minecart persists {@code command} and
 * {@code customName} across a {@code saveNBT()} -> reconstruct -> {@code initEntity()}
 * round-trip.
 * <p>
 * Regression guard for the "command lost after reload" bug. The root cause was a
 * Java field-initialization-order trap: {@link Entity}'s constructor calls
 * {@code init() -> initEntity()} up the {@code super()}} chain, which runs
 * <em>before</em> the subclass's field initializers (e.g. {@code command = ""}).
 * Those initializers therefore executed <em>after</em> {@code initEntity()} had
 * loaded the persisted values from NBT, silently clobbering them with defaults.
 */
public class EntityMinecartTest {

    private static Server serverMock;

    @BeforeAll
    static void init() throws Exception {
        MockServer.init();
        serverMock = MockServer.get();

        // Entity.init() dispatches EntitySpawnEvent and schedules an update; both go
        // through the mock server/plugin manager. Default lenient mocks suffice since
        // we only assert that construction completes and metadata is set.
    }

    private static Level newMockLevel() {
        Level level = mock(Level.class);
        lenient().when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        lenient().when(level.getServer()).thenReturn(serverMock);
        lenient().when(serverMock.getTick()).thenReturn(0);
        lenient().doNothing().when(level).addEntity(org.mockito.ArgumentMatchers.any());
        try {
            Field f = Level.class.getDeclaredField("isBeingConverted");
            f.setAccessible(true);
            f.setBoolean(level, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return level;
    }

    private static FullChunk newMockChunk(Level level) {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().doNothing().when(chunk).addEntity(org.mockito.ArgumentMatchers.any());
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

    @Test
    void commandSurvivesSaveLoadRoundTrip() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        EntityMinecartCommandBlock original = new EntityMinecartCommandBlock(chunk, baseNbt());
        original.setCommand("say hello");
        original.setCustomName("MyName");

        // Simulate chunk save: serialize the entity's namedTag to disk form.
        original.saveNBT();

        // Emulate a true disk round-trip via a fresh NBT tree (the original and the
        // reloaded entity must not share the same mutable namedTag). The constructor
        // triggers init() -> initEntity(), the same path createEntity() takes on load.
        EntityMinecartCommandBlock reloaded = new EntityMinecartCommandBlock(chunk, cloneCompound(original.namedTag));

        assertEquals("say hello", reloaded.getCommand(),
                "Command must survive save/load round-trip");
    }

    @Test
    void customNameSurvivesSaveLoadRoundTrip() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        EntityMinecartCommandBlock original = new EntityMinecartCommandBlock(chunk, baseNbt());
        original.setCustomName("MyName");

        original.saveNBT();

        EntityMinecartCommandBlock reloaded = new EntityMinecartCommandBlock(chunk, cloneCompound(original.namedTag));
        assertEquals("MyName", reloaded.getName(),
                "CommandSender custom name must survive save/load round-trip");
    }

    @Test
    void metadataCarriesCommandAfterLoad() {
        // Regression for "command executes but minecart GUI shows it blank": the
        // editor reads the command from entity metadata index 71
        // (DATA_COMMAND_BLOCK_COMMAND), not block-entity NBT. initEntity() must write it.
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        EntityMinecartCommandBlock original = new EntityMinecartCommandBlock(chunk, baseNbt());
        original.setCommand("say hello");
        original.saveNBT();

        EntityMinecartCommandBlock reloaded = new EntityMinecartCommandBlock(chunk, cloneCompound(original.namedTag));

        // Entity.DATA_COMMAND_BLOCK_COMMAND = 71 = Bedrock COMMAND_BLOCK_NAME (modern)
        assertEquals("say hello", reloaded.getDataPropertyString(71),
                "COMMAND_BLOCK_NAME (index 71) must carry the loaded command so the client GUI can show it");
    }

    @Test
    void addEntityPacketForV126CarriesCommandMetadataAfterLoad() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        TestableCommandBlockMinecart original = new TestableCommandBlockMinecart(chunk, baseNbt());
        original.setCommand("say hello");
        original.saveNBT();

        TestableCommandBlockMinecart reloaded = new TestableCommandBlockMinecart(chunk, cloneCompound(original.namedTag));
        DataPacket packet = reloaded.createAddEntityPacketForTest();
        int protocol = ProtocolInfo.v1_26_20;
        GameVersion gameVersion = GameVersion.byProtocol(protocol, false);
        packet.protocol = protocol;
        packet.gameVersion = gameVersion;
        packet.encode();

        org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket decoded = decodeAddEntity(packet, gameVersion);

        assertEquals("minecraft:command_block_minecart", decoded.getIdentifier());
        assertEquals("say hello", decoded.getMetadata().get(EntityDataTypes.COMMAND_BLOCK_NAME),
                "1.26 clients reopen the minecart editor from AddEntity COMMAND_BLOCK_NAME metadata");
        assertEquals(Boolean.TRUE, decoded.getMetadata().get(EntityDataTypes.COMMAND_BLOCK_ENABLED),
                "Command block minecart metadata must mark command-block editing as enabled");
    }

    private static org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket decodeAddEntity(
            DataPacket packet, GameVersion gameVersion) {
        BedrockCodec codec = ProtocolCodecMapping.getCodec(packet.protocol);
        BedrockCodecHelper helper = codec.createHelper();
        int commandBlockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(gameVersion, Block.COMMAND_BLOCK, 0);
        BlockDefinition commandBlock = new SimpleBlockDefinition("minecraft:command_block",
                commandBlockRuntimeId, NbtMap.EMPTY);
        helper.setBlockDefinitions(SimpleDefinitionRegistry.<BlockDefinition>builder().add(commandBlock).build());

        ByteBuf buf = PacketBridgeUtil.nukkitPacketToByteBuf(packet);
        try {
            org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket decoded =
                    new org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket();
            codec.getPacketDefinition(org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket.class)
                    .getSerializer().deserialize(buf, helper, decoded);
            assertEquals(0, buf.readableBytes(), "AddEntityPacket payload should be fully decoded");
            return decoded;
        } finally {
            buf.release();
        }
    }

    private static CompoundTag cloneCompound(CompoundTag tag) {
        // Round-trip through serialization to avoid the original and reloaded entities
        // sharing the same mutable namedTag tree (emulates a real disk save/load).
        try {
            byte[] bytes = cn.nukkit.nbt.NBTIO.write(tag, java.nio.ByteOrder.LITTLE_ENDIAN);
            return cn.nukkit.nbt.NBTIO.read(bytes, java.nio.ByteOrder.LITTLE_ENDIAN);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class TestableCommandBlockMinecart extends EntityMinecartCommandBlock {
        private TestableCommandBlockMinecart(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
        }

        private DataPacket createAddEntityPacketForTest() {
            return super.createAddEntityPacket();
        }
    }

    /**
     * Regression test for the command block minecart (and other minecart variants)
     * showing the wrong display block on spawn.
     * <p>
     * Root cause: the subclass constructor called
     * {@code setDisplayBlock(block, false)} <em>after</em> {@code super(chunk, nbt)},
     * but {@code initEntity()} (which computes {@link Entity#DATA_DISPLAY_ITEM}) runs
     * <em>inside</em> that super call — before {@code blockInside} is assigned. So the
     * spawn metadata carried {@code DATA_HAS_DISPLAY=0} and the client fell back to the
     * entity's own texture (the orange pulse block, which looks like a repeating
     * command block). The fix introduces a {@code getDefaultDisplayBlock()} hook that
     * {@code prepareDataProperty()} consults during {@code initEntity()}.
     */
    @Test
    void freshlySpawnedCommandBlockMinecartShowsCommandBlock() {
        EntityMinecartAbstract minecart = spawnMinecart(chunk -> baseNbt());

        // DATA_HAS_DISPLAY must be 1 (otherwise the client uses the entity's default
        // texture, which for NETWORK_ID 100 is the orange pulse block).
        assertEquals(1, minecart.getDataPropertyByte(Entity.DATA_HAS_DISPLAY),
                "Command block minecart must report a display block on spawn");

        // DATA_DISPLAY_ITEM is packed as legacyId | (meta << 16); for a normal
        // command block that is exactly Block.COMMAND_BLOCK (137).
        int display = minecart.getDataPropertyInt(Entity.DATA_DISPLAY_ITEM);
        int expected = Block.COMMAND_BLOCK;
        assertEquals(expected, display,
                "Display item should be command_block (137), was " + display);
    }

    /**
     * Simulates a minecart restored from NBT where a different display tile was
     * persisted. The CustomDisplayTile branch must win over the default hook.
     */
    @Test
    void loadedFromSaveKeepsPersistedDisplayTile() {
        CompoundTag nbt = baseNbt()
                .putBoolean("CustomDisplayTile", true)
                .putInt("DisplayTile", Block.COMMAND_BLOCK)
                .putInt("DisplayOffset", 8);

        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        EntityMinecartAbstract minecart = new EntityMinecartCommandBlock(chunk, nbt);

        assertEquals(1, minecart.getDataPropertyByte(Entity.DATA_HAS_DISPLAY));
        assertEquals(Block.COMMAND_BLOCK, minecart.getDataPropertyInt(Entity.DATA_DISPLAY_ITEM));
        // Persisted offset must be honored, not the default 6.
        assertEquals(8, minecart.getDataPropertyInt(Entity.DATA_DISPLAY_OFFSET));
    }

    private static EntityMinecartAbstract spawnMinecart(java.util.function.Function<FullChunk, CompoundTag> nbtBuilder) {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);
        CompoundTag nbt = nbtBuilder.apply(chunk);
        return new EntityMinecartCommandBlock(chunk, nbt);
    }

    /**
     * A cart with an inventory notifies the hopper under its rail and the hopper above it. Both scans
     * cover the cells the cart occupies, on either side of zero and below y = 0.
     */
    @Nested
    class HopperScan {

        private static final double[][] CARTS = {
                {10.5, 64.0625, 20.5},
                {-10.5, 64.0625, -20.5},
                {-0.5, -30.9375, 0.5},
                {7.25, -12.0, -3.75},
        };

        private static boolean scan(String method, double[] cart, int hopperX, int hopperY, int hopperZ,
                                    BlockEntityHopper hopper, InventoryHolder holder) throws Exception {
            Level level = mock(Level.class);
            when(level.getBlockEntity(any(BlockVector3.class))).thenAnswer(invocation -> {
                BlockVector3 at = invocation.getArgument(0);
                return at.x == hopperX && at.y == hopperY && at.z == hopperZ ? hopper : null;
            });
            EntityMinecartAbstract minecart = mock(EntityMinecartAbstract.class, CALLS_REAL_METHODS);
            minecart.level = level;

            double top = method.equals("checkPickupHopper") ? cart[1] : cart[1] + 2;
            double bottom = method.equals("checkPickupHopper") ? cart[1] - 1 : cart[1];
            AxisAlignedBB area = new SimpleAxisAlignedBB(cart[0], bottom, cart[2], cart[0], top, cart[2]);

            Method scan = EntityMinecartAbstract.class.getDeclaredMethod(method, AxisAlignedBB.class, InventoryHolder.class);
            scan.setAccessible(true);
            return (boolean) scan.invoke(minecart, area, holder);
        }

        @Test
        void cartNotifiesTheHopperUnderItsRail() throws Exception {
            for (double[] cart : CARTS) {
                BlockEntityHopper hopper = mock(BlockEntityHopper.class);
                InventoryHolder holder = mock(InventoryHolder.class);
                int x = (int) Math.floor(cart[0]);
                int y = (int) Math.floor(cart[1]) - 1;
                int z = (int) Math.floor(cart[2]);

                assertTrue(scan("checkPickupHopper", cart, x, y, z, hopper, holder),
                        "hopper under the cart at " + cart[0] + ", " + cart[1] + ", " + cart[2]);
                verify(hopper).setMinecartInvPickupFrom(holder);
            }
        }

        @Test
        void cartNotifiesTheHopperAboveIt() throws Exception {
            for (double[] cart : CARTS) {
                BlockEntityHopper hopper = mock(BlockEntityHopper.class);
                InventoryHolder holder = mock(InventoryHolder.class);
                int x = (int) Math.floor(cart[0]);
                int y = (int) Math.floor(cart[1]) + 1;
                int z = (int) Math.floor(cart[2]);

                assertTrue(scan("checkPushHopper", cart, x, y, z, hopper, holder),
                        "hopper above the cart at " + cart[0] + ", " + cart[1] + ", " + cart[2]);
                verify(hopper).setMinecartInvPushTo(holder);
            }
        }
    }

    @Nested
    class TNT {

        private static Server serverMock;

        @BeforeAll
        static void init() {
            MockServer.init();
            serverMock = MockServer.get();
        }

        @BeforeEach
        void resetServerBeforeTest() {
            MockServer.reset();
            serverMock = MockServer.get();
        }

        @AfterEach
        void resetServerAfterTest() {
            MockServer.reset();
        }

        @Test
        void lethalFlamingProjectileExplodesInsteadOfDroppingItem() {
            GameRules gameRules = GameRules.getDefault();
            Level level = newMockLevel(gameRules);
            FullChunk chunk = newMockChunk(level);
            TestableTNTMinecart minecart = new TestableTNTMinecart(chunk, baseNbt());
            EntityArrow arrow = newArrow(chunk, true);

            minecart.attack(new EntityDamageByChildEntityEvent(mock(Entity.class), arrow, minecart,
                    EntityDamageEvent.DamageCause.PROJECTILE, 5));

            assertTrue(minecart.exploded, "Flaming projectile should detonate the TNT minecart");
            assertTrue(minecart.aliveWhenExploded,
                    "Flaming projectile should detonate before normal damage kills the minecart");
            assertFalse(minecart.closedWhenExploded, "Flaming projectile should detonate before the minecart is closed");
            verify(level, never()).dropItem(any(Vector3.class), any(Item.class));
        }

        @Test
        void flamingFireChargeProjectileDoesNotUseInstantArrowDetonation() {
            Level level = newMockLevel(GameRules.getDefault());
            FullChunk chunk = newMockChunk(level);
            TestableTNTMinecart minecart = new TestableTNTMinecart(chunk, baseNbt());
            EntityBlazeFireBall fireBall = newBlazeFireBall(chunk, true);

            minecart.attack(new EntityDamageByChildEntityEvent(mock(Entity.class), fireBall, minecart,
                    EntityDamageEvent.DamageCause.PROJECTILE, 0));

            assertFalse(minecart.exploded,
                    "Fire charge projectiles should not use the flaming-arrow instant detonation path");
        }

        @Test
        void flamingProjectileDoesNotExplodeWhenTntExplodesIsDisabled() {
            GameRules gameRules = GameRules.getDefault();
            gameRules.setGameRule(GameRule.TNT_EXPLODES, false);
            Level level = newMockLevel(gameRules);
            FullChunk chunk = newMockChunk(level);
            TestableTNTMinecart minecart = new TestableTNTMinecart(chunk, baseNbt());
            EntityArrow arrow = newArrow(chunk, true);

            minecart.attack(new EntityDamageByChildEntityEvent(mock(Entity.class), arrow, minecart,
                    EntityDamageEvent.DamageCause.PROJECTILE, 5));

            assertFalse(minecart.exploded, "tntExplodes=false should suppress flaming-projectile detonation");
        }

        @Test
        void cancelledFlamingProjectileDamageDoesNotExplode() {
            PluginManager pluginManager = mock(PluginManager.class);
            doAnswer(invocation -> {
                Event event = invocation.getArgument(0);
                if (event instanceof EntityDamageEvent damageEvent) {
                    damageEvent.setCancelled();
                }
                return null;
            }).when(pluginManager).callEvent(any(Event.class));
            when(serverMock.getPluginManager()).thenReturn(pluginManager);

            Level level = newMockLevel(GameRules.getDefault());
            FullChunk chunk = newMockChunk(level);
            TestableTNTMinecart minecart = new TestableTNTMinecart(chunk, baseNbt());
            EntityArrow arrow = newArrow(chunk, true);

            boolean damaged = minecart.attack(new EntityDamageByChildEntityEvent(mock(Entity.class), arrow, minecart,
                    EntityDamageEvent.DamageCause.PROJECTILE, 5));

            assertFalse(damaged, "Cancelled flaming-projectile damage should not be applied");
            assertFalse(minecart.exploded,
                    "Cancelled flaming-projectile damage should not detonate the TNT minecart");
        }

        @Test
        void flamingProjectileKeepsOriginalDamageVisibleToDamageEvent() {
            PluginManager pluginManager = mock(PluginManager.class);
            AtomicReference<Float> finalDamage = new AtomicReference<>();
            doAnswer(invocation -> {
                Event event = invocation.getArgument(0);
                if (event instanceof EntityDamageEvent damageEvent) {
                    finalDamage.set(damageEvent.getFinalDamage());
                }
                return null;
            }).when(pluginManager).callEvent(any(Event.class));
            when(serverMock.getPluginManager()).thenReturn(pluginManager);

            Level level = newMockLevel(GameRules.getDefault());
            FullChunk chunk = newMockChunk(level);
            TestableTNTMinecart minecart = new TestableTNTMinecart(chunk, baseNbt());
            EntityArrow arrow = newArrow(chunk, true);

            minecart.attack(new EntityDamageByChildEntityEvent(mock(Entity.class), arrow, minecart,
                    EntityDamageEvent.DamageCause.PROJECTILE, 5));

            assertEquals(75, finalDamage.get(),
                    "Flaming-projectile damage event should keep minecart's normal x15 damage scale");
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

        private static FullChunk newMockChunk(Level level) {
            FullChunk chunk = mock(FullChunk.class);
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

        private static EntityArrow newArrow(FullChunk chunk, boolean onFire) {
            CompoundTag nbt = baseNbt()
                    .putShort("Fire", onFire ? 45 * 60 : 0)
                    .putDouble("damage", 5);
            EntityArrow arrow = new EntityArrow(chunk, nbt);
            arrow.motionX = 1.2;
            arrow.motionY = 0;
            arrow.motionZ = 0;
            return arrow;
        }

        private static EntityBlazeFireBall newBlazeFireBall(FullChunk chunk, boolean onFire) {
            CompoundTag nbt = baseNbt().putShort("Fire", onFire ? 2 : 0);
            EntityBlazeFireBall fireBall = new EntityBlazeFireBall(chunk, nbt);
            fireBall.motionX = 1.2;
            fireBall.motionY = 0;
            fireBall.motionZ = 0;
            return fireBall;
        }

        private static final class TestableTNTMinecart extends EntityMinecartTNT {
            private boolean exploded;
            private boolean aliveWhenExploded;
            private boolean closedWhenExploded;

            private TestableTNTMinecart(FullChunk chunk, CompoundTag nbt) {
                super(chunk, nbt);
            }

            @Override
            public void explode(double square) {
                this.aliveWhenExploded = isAlive();
                this.closedWhenExploded = isClosed();
                this.exploded = true;
            }
        }
    }
}
