package cn.nukkit;

import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerOffhandInventoryChangeEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.transaction.data.ReleaseItemData;
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArrow;
import cn.nukkit.item.ItemCrossbow;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.*;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.AuthInputAction;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.potion.Potion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerCrossbowInputTest extends AbstractPacketRegressionTest {
    private final AtomicInteger tick = new AtomicInteger(100);
    private final List<EntityShootBowEvent> shots = new ArrayList<>();
    private TestPlayer player;
    private Consumer<Event> listener = event -> { };

    @BeforeEach
    void setUp() {
        MockServer.reset();
        Server server = MockServer.get();
        server.doNotLimitInteractions = true;
        when(server.getTick()).thenAnswer(call -> tick.get());
        var plugins = server.getPluginManager();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof EntityShootBowEvent shot) shots.add(shot);
            listener.accept(event);
            return null;
        }).when(plugins).callEvent(any(Event.class));

        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        level.isBeingConverted = true;
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        when(server.getDefaultLevel()).thenReturn(level);
        SourceInterface source = mock(SourceInterface.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
        player = new TestPlayer(source, level, chunk);
        assertTrue(player.getInventory().setItem(0, new ItemCrossbow()));
        assertTrue(player.getInventory().setItem(1, Item.get(Item.ARROW, 0, 3)));
    }

    @AfterEach
    void resetServer() {
        MockServer.reset();
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_124", "V1_21_124_NETEASE"})
    void continuationPacketsChargeOnceWithoutFiringOrConsumingAnotherArrow(GameVersion version) {
        player.gameVersion = version;
        player.protocol = version.getProtocol();
        MockServer.get().doNotLimitInteractions = false;
        AtomicInteger interactions = new AtomicInteger();
        listener = event -> {
            if (event instanceof PlayerInteractEvent) interactions.incrementAndGet();
        };
        click(100, 1);
        click(120, 2);
        for (int t = 121; t <= 180; t++) click(t, 2);

        assertTrue(held().isLoaded(), "Holding the input must keep the crossbow charged");
        assertEquals(2, player.getInventory().getItem(1).getCount());
        assertTrue(shots.isEmpty(), "Simulation ticks must never fire a charged crossbow");
        assertFalse(player.isUsingItem());
        assertEquals(1, interactions.get());
    }

    private ItemCrossbow held() {
        return (ItemCrossbow) player.getInventory().getItemInHand();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void freshClicksRespectTheInteractionLimitDuringLoadedCooldown(boolean authInput) {
        player.gameVersion = GameVersion.V1_21_20;
        player.protocol = player.gameVersion.getProtocol();
        MockServer.get().serverAuthoritativeMovementMode = 0;
        AtomicInteger interactions = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        ItemCrossbow crossbow = new CallbackCrossbow(p -> callbacks.incrementAndGet());
        crossbow.loadArrow(player, Item.get(Item.ARROW));
        listener = event -> {
            if (event instanceof PlayerInteractEvent) interactions.incrementAndGet();
        };
        MockServer.get().doNotLimitInteractions = false;
        for (int i = 0; i < 20; i++) input(authInput, 101, UseItemData.TRIGGER_PLAYER_INPUT);

        assertTrue(held().isLoaded());
        assertTrue(shots.isEmpty());
        assertEquals(1, interactions.get(), "Rapid fresh input must retain the CLICK_AIR limit");
        assertEquals(1, callbacks.get(), "The limit must precede item callbacks");
    }

    @Test
    void earlyContinuationDoesNotRestartOrCancelTheCharge() {
        click(100, 1);
        for (int t = 101; t <= 120; t++) click(t, 2);

        assertTrue(held().isLoaded());
        assertEquals(2, player.getInventory().getItem(1).getCount());
        assertFalse(player.isUsingItem());
    }

    @Test
    void identicalReplacementInTheSameSlotCannotInheritChargeTime() {
        click(100, 1);
        tick.set(119);
        assertTrue(player.getInventory().setItem(0, new ItemCrossbow()));
        click(120, 2);

        assertFalse(held().isLoaded());
        assertEquals(3, player.getInventory().getItem(1).getCount());
        assertFalse(player.isUsingItem());

        click(121, 1);
        click(140, 2);
        assertFalse(held().isLoaded());
        click(141, 2);
        assertTrue(held().isLoaded());
        assertEquals(2, player.getInventory().getItem(1).getCount());
    }

    enum CancelledWrite { CHARGED_ITEM, MAIN_AMMO, OFFHAND_AMMO, OFFHAND_EVENT }
    enum MetadataWrite { CHARGE, UNLOAD, MAIN_AMMO, OFFHAND_AMMO, OFFHAND_EVENT }

    @ParameterizedTest
    @EnumSource(MetadataWrite.class)
    void inventoryEventsCanUpdateMetadataWithoutCancellingTheCrossbow(MetadataWrite write) {
        boolean offhand = write == MetadataWrite.OFFHAND_AMMO || write == MetadataWrite.OFFHAND_EVENT;
        if (offhand) player.getOffhandInventory().setItem(0, Item.get(Item.ARROW, 0, 3));
        listener = event -> {
            if (event instanceof EntityInventoryChangeEvent change) {
                Item next = change.getNewItem();
                boolean update = next instanceof ItemCrossbow bow
                        ? (write == MetadataWrite.CHARGE && bow.isLoaded())
                        || (write == MetadataWrite.UNLOAD && !bow.isLoaded())
                        : next.getId() == Item.ARROW
                        && (write == MetadataWrite.MAIN_AMMO || write == MetadataWrite.OFFHAND_AMMO);
                if (update) {
                    Item replacement = next instanceof ItemCrossbow
                            ? new ItemCrossbow(next.getDamage(), next.getCount()).setCompoundTag(next.getCompoundTag().clone())
                            : next.clone();
                    replacement.setCustomName("Updated by inventory event").setLore("Updated lore");
                    change.setNewItem(replacement);
                }
            } else if (event instanceof PlayerOffhandInventoryChangeEvent change
                    && write == MetadataWrite.OFFHAND_EVENT) {
                change.setNewItem(change.getNewItem().clone()
                        .setCustomName("Updated by inventory event").setLore("Updated lore"));
            }
        };
        click(100, 1);
        click(120, 2);

        assertTrue(held().isLoaded(), "Metadata edits must not cancel a valid charge");
        Item ammunition = offhand ? player.getOffhandInventory().getItem(0) : player.getInventory().getItem(1);
        assertEquals(2, ammunition.getCount());
        if (write == MetadataWrite.CHARGE) {
            assertEquals("Updated by inventory event", held().getCustomName());
            assertArrayEquals(new String[]{"Updated lore"}, held().getLore());
        } else if (write != MetadataWrite.UNLOAD) {
            assertEquals("Updated by inventory event", ammunition.getCustomName());
            assertArrayEquals(new String[]{"Updated lore"}, ammunition.getLore());
        }

        click(130, 1);
        assertTrue(shots.isEmpty(), "Event metadata edits must preserve the loaded cooldown");
        click(131, 1);
        assertEquals(1, shots.size());
        assertFalse(held().isLoaded());
        if (write == MetadataWrite.CHARGE || write == MetadataWrite.UNLOAD) {
            assertEquals("Updated by inventory event", held().getCustomName());
            assertArrayEquals(new String[]{"Updated lore"}, held().getLore());
        }
    }

    @Test
    void offhandEventReceivesAndPreservesTheGenericEventMetadata() {
        player.getOffhandInventory().setItem(0, Item.get(Item.ARROW, 0, 3));
        listener = event -> {
            if (event instanceof EntityInventoryChangeEvent change && change.getNewItem().getId() == Item.ARROW) {
                change.setNewItem(change.getNewItem().clone().setCustomName("Generic event name"));
            } else if (event instanceof PlayerOffhandInventoryChangeEvent change) {
                assertEquals("Generic event name", change.getNewItem().getCustomName());
                change.setNewItem(change.getNewItem().clone().setLore("Offhand event lore"));
            }
        };
        click(100, 1);
        click(120, 2);

        assertTrue(held().isLoaded());
        Item ammunition = player.getOffhandInventory().getItem(0);
        assertEquals(2, ammunition.getCount());
        assertEquals("Generic event name", ammunition.getCustomName());
        assertArrayEquals(new String[]{"Offhand event lore"}, ammunition.getLore());
    }

    enum InvalidChargeEdit { CROSSBOW_COUNT, CROSSBOW_DAMAGE, PROJECTILE, AMMO_COUNT, AMMO_TYPE }

    @ParameterizedTest
    @EnumSource(InvalidChargeEdit.class)
    void metadataCompatibilityCannotChangeChargeCostsOrProjectile(InvalidChargeEdit edit) {
        listener = event -> {
            if (!(event instanceof EntityInventoryChangeEvent change)) return;
            Item next = change.getNewItem();
            if (next instanceof ItemCrossbow bow && bow.isLoaded()) {
                if (edit == InvalidChargeEdit.CROSSBOW_COUNT) bow.setCount(2);
                if (edit == InvalidChargeEdit.CROSSBOW_DAMAGE) bow.setDamage(0);
                if (edit == InvalidChargeEdit.PROJECTILE) bow.createNamedTag(Item.get(Item.FIREWORKS), 1);
            } else if (next.getId() == Item.ARROW) {
                if (edit == InvalidChargeEdit.AMMO_COUNT) next.setCount(3);
                if (edit == InvalidChargeEdit.AMMO_TYPE) change.setNewItem(Item.get(Item.DIAMOND, 0, 2));
            }
        };
        click(100, 1);
        click(120, 2);

        assertFalse(held().isLoaded());
        assertEquals(0, held().getDamage());
        assertEquals(1, held().getCount());
        assertEquals(3, player.getInventory().getItem(1).getCount());
        assertTrue(shots.isEmpty());
    }

    @Test
    void inventoryEventCannotReloadTheCrossbowWhileItIsBeingFired() {
        click(100, 1);
        click(120, 2);
        listener = event -> {
            if (event instanceof EntityInventoryChangeEvent change
                    && change.getNewItem() instanceof ItemCrossbow bow && !bow.isLoaded()) {
                bow.createNamedTag(Item.get(Item.ARROW), 1);
            }
        };
        click(131, 1);
        click(132, 1);

        assertTrue(held().isLoaded());
        assertEquals(2, player.getInventory().getItem(1).getCount());
        assertTrue(shots.isEmpty(), "Reloading the event result must not create free projectiles");
    }

    @Test
    void releaseCompletesTheCurrentChargeWithoutFiring() {
        MockServer.get().doNotLimitInteractions = false;
        click(100, 1);
        release(120);
        release(121);

        assertTrue(held().isLoaded());
        assertEquals(2, player.getInventory().getItem(1).getCount());
        assertTrue(shots.isEmpty());
        assertFalse(player.isUsingItem());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completionDoesNotRepeatTheInitialInteraction(boolean finishByRelease) {
        AtomicInteger interactions = new AtomicInteger();
        listener = event -> {
            if (event instanceof PlayerInteractEvent interaction && interactions.incrementAndGet() > 1) {
                interaction.setCancelled();
            }
        };
        click(100, UseItemData.TRIGGER_PLAYER_INPUT);
        for (int t = 101; t < 120; t++) click(t, UseItemData.TRIGGER_SIMULATION_TICK);
        if (finishByRelease) release(120);
        else click(120, UseItemData.TRIGGER_SIMULATION_TICK);

        assertEquals(1, interactions.get(), "Holding or releasing the same input is not a new click");
        assertFalse(player.isUsingItem());
        assertTrue(held().isLoaded());
        assertEquals(2, player.getInventory().getItem(1).getCount());
    }

    @Test
    void rejectedUnloadingCannotSpawnAProjectile() {
        click(100, 1);
        click(120, 2);
        listener = event -> {
            if (event instanceof EntityInventoryChangeEvent change
                    && change.getOldItem() instanceof ItemCrossbow before && before.isLoaded()
                    && change.getNewItem() instanceof ItemCrossbow after && !after.isLoaded()) {
                change.setCancelled();
            }
        };
        click(131, 1);

        assertTrue(held().isLoaded());
        assertTrue(shots.isEmpty(), "A cancelled unload must not create a free projectile");
        assertEquals(2, player.getInventory().getItem(1).getCount());
    }

    @Test
    void repeatedInitialInputCannotCompleteAnExistingCharge() {
        AtomicInteger interactions = new AtomicInteger();
        listener = event -> {
            if (event instanceof PlayerInteractEvent) interactions.incrementAndGet();
        };
        click(100, 1);
        click(120, 1);

        assertEquals(1, interactions.get(), "Repeated modern input is not a new interaction");
        assertFalse(held().isLoaded(), "Only continuation or release may complete an explicit-input charge");
        assertTrue(player.isUsingItem());
        assertEquals(3, player.getInventory().getItem(1).getCount());
        click(121, 2);
        assertTrue(held().isLoaded());
        assertEquals(1, interactions.get());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_2", "V1_21_2_NETEASE"})
    void legacyClicksCanFireAfterChargingWithoutAReleaseSignal(GameVersion version) {
        player.gameVersion = version;
        player.protocol = version.getProtocol();
        AtomicInteger interactions = new AtomicInteger();
        listener = event -> {
            if (event instanceof PlayerInteractEvent interaction) {
                assertEquals(PlayerInteractEvent.Action.RIGHT_CLICK_AIR, interaction.getAction());
                interactions.incrementAndGet();
            }
        };
        click(100, 0);
        assertEquals(1, interactions.get());
        click(120, 0);
        assertEquals(2, interactions.get(), "The legacy completion click is a new interaction");
        assertTrue(held().isLoaded());
        assertEquals(2, player.getInventory().getItem(1).getCount());

        click(130, 0);
        assertTrue(shots.isEmpty(), "Legacy clicks must still respect the loaded cooldown");
        click(131, 0);
        assertEquals(1, shots.size());
        assertFalse(held().isLoaded());

        click(200, 0);
        click(220, 0);
        click(1000, 0);
        assertEquals(2, shots.size(), "A later click must not require an extra release or slot switch");
        assertFalse(held().isLoaded());
        assertEquals(1, player.getInventory().getItem(1).getCount());
        assertEquals(7, interactions.get(), "Every accepted legacy click must fire an interaction event");
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_2", "V1_21_2_NETEASE"})
    void cancellingLegacyCompletionClickEndsTheChargeWithoutConsumingAmmo(GameVersion version) {
        player.gameVersion = version;
        player.protocol = version.getProtocol();
        AtomicInteger interactions = new AtomicInteger();
        listener = event -> {
            if (event instanceof PlayerInteractEvent interaction && interactions.incrementAndGet() == 2) {
                interaction.setCancelled();
            }
        };
        click(100, 0);
        click(120, 0);

        assertEquals(2, interactions.get());
        assertFalse(held().isLoaded(), "Plugins must be able to veto the legacy completion click");
        assertFalse(player.isUsingItem());
        assertEquals(0, held().getDamage());
        assertEquals(3, player.getInventory().getItem(1).getCount());
        assertTrue(shots.isEmpty());

        click(121, 0);
        click(140, 0);
        assertEquals(3, interactions.get());
        assertFalse(held().isLoaded(), "A new charge must not inherit the cancelled charge time");
        click(141, 0);
        assertEquals(4, interactions.get());
        assertTrue(held().isLoaded());
        assertEquals(2, player.getInventory().getItem(1).getCount());
        assertTrue(shots.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_124", "V1_21_124_NETEASE"})
    void explicitUnknownInputRequiresAReleaseBoundaryAfterCharging(GameVersion version) {
        player.gameVersion = version;
        player.protocol = version.getProtocol();
        click(100, 0);
        click(120, 0);
        for (int t = 121; t <= 150; t++) click(t, 0);

        assertTrue(held().isLoaded());
        assertTrue(shots.isEmpty());
        release(151);
        click(152, 0);
        assertFalse(held().isLoaded());
        assertEquals(1, shots.size());
        assertEquals(2, player.getInventory().getItem(1).getCount());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_2", "V1_21_124", "V1_21_2_NETEASE", "V1_21_124_NETEASE"})
    void switchingCrossbowsClearsThePreviousAmbiguousInputLock(GameVersion version) {
        player.gameVersion = version;
        player.protocol = version.getProtocol();
        ItemCrossbow other = new ItemCrossbow();
        other.createNamedTag(Item.get(Item.ARROW), 1);
        player.getInventory().setItem(2, other);
        click(100, 0);
        click(120, 0);
        click(121, 0);
        assertTrue(shots.isEmpty(), "Tail input must remain blocked on the original held slot");

        player.getInventory().setHeldItemIndex(1, false);
        release(151);
        player.getInventory().setHeldItemIndex(2, false);
        click(152, 0);

        assertEquals(1, shots.size(), "A different loaded crossbow must accept fresh input after switching slots");
        assertFalse(held().isLoaded());
        player.getInventory().setHeldItemIndex(0, false);
        click(153, 0);
        assertEquals(2, shots.size(), "Returning to the original crossbow must also accept fresh input");
        assertFalse(held().isLoaded());
        assertEquals(2, player.getInventory().getItem(1).getCount());
    }

    @Test
    void referenceEncodedAuthInputReachesTheSameChargeController() {
        player.gameVersion = GameVersion.V1_21_20;
        player.protocol = player.gameVersion.getProtocol();
        MockServer.get().serverAuthoritativeMovementMode = 0;
        authClick(100, 1);
        authClick(120, 2);

        assertTrue(held().isLoaded());
        assertEquals(2, player.getInventory().getItem(1).getCount());
        assertTrue(shots.isEmpty());
        authClick(131, 1);
        assertEquals(1, shots.size());
        assertFalse(held().isLoaded());
    }

    @Test
    void authInputShotUsesTheRotationAcceptedFromTheSamePacket() {
        player.gameVersion = GameVersion.V1_21_20;
        player.protocol = player.gameVersion.getProtocol();
        MockServer.get().serverAuthoritativeMovementMode = 1;
        ItemCrossbow crossbow = held();
        crossbow.createNamedTag(Item.get(Item.ARROW), 1);
        player.getInventory().setItemInHand(crossbow);
        player.yaw = 90;
        listener = event -> {
            if (event instanceof EntityShootBowEvent) {
                assertEquals(0, player.yaw, "Apply this packet's rotation before shooting");
            }
        };

        authClick(131, 1);

        assertEquals(1, shots.size());
        Vector3 motion = shots.get(0).getProjectile().getMotion();
        assertTrue(motion.z > Math.abs(motion.x) * 10, "The arrow must follow the new yaw, not the previous yaw");
        assertFalse(held().isLoaded());
    }

    enum AuthMovement { UNCHANGED, CHANGED, FORCE_CORRECTION, PENDING_TELEPORT, TELEPORT_WINDOW, INVALID_POSITION }

    @ParameterizedTest
    @EnumSource(AuthMovement.class)
    void authInputCrossbowRespectsMovementAcceptance(AuthMovement movement) {
        player.gameVersion = GameVersion.V1_21_20;
        player.protocol = player.gameVersion.getProtocol();
        MockServer.get().serverAuthoritativeMovementMode = 1;
        ItemCrossbow crossbow = held();
        crossbow.createNamedTag(Item.get(Item.ARROW), 1);
        player.getInventory().setItemInHand(crossbow);
        player.y = -player.getEyeHeight();
        switch (movement) {
            case CHANGED -> player.x = 1;
            case FORCE_CORRECTION -> player.forceMovement = player.getLocation().add(10, 0, 0);
            case PENDING_TELEPORT -> player.teleportPosition = player.getLocation();
            case TELEPORT_WINDOW -> player.lastTeleportTick = 100;
            case INVALID_POSITION -> player.x = 100;
        }

        authClick(131, 1);

        boolean accepted = movement == AuthMovement.UNCHANGED || movement == AuthMovement.CHANGED;
        assertEquals(accepted ? 1 : 0, shots.size());
        assertEquals(!accepted, held().isLoaded());
        if (movement == AuthMovement.UNCHANGED) {
            assertNull(player.newPosition, "Unchanged movement must still dispatch the item interaction");
        }
    }

    @ParameterizedTest
    @EnumSource(AuthMovement.class)
    void authInputStartsChargingOnlyAfterMovementIsAccepted(AuthMovement movement) {
        player.gameVersion = GameVersion.V1_21_20;
        player.protocol = player.gameVersion.getProtocol();
        MockServer.get().serverAuthoritativeMovementMode = 1;
        player.y = -player.getEyeHeight();
        switch (movement) {
            case CHANGED -> player.x = 1;
            case FORCE_CORRECTION -> player.forceMovement = player.getLocation().add(10, 0, 0);
            case PENDING_TELEPORT -> player.teleportPosition = player.getLocation();
            case TELEPORT_WINDOW -> player.lastTeleportTick = 100;
            case INVALID_POSITION -> player.x = 100;
        }
        AtomicInteger interactions = new AtomicInteger();
        listener = event -> {
            if (event instanceof PlayerInteractEvent) interactions.incrementAndGet();
        };
        PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        packet.protocol = player.protocol;
        packet.gameVersion = player.gameVersion;
        packet.setPosition(new Vector3f());
        packet.setMotion(new Vector2());
        packet.getInputData().add(AuthInputAction.START_USING_ITEM);
        player.handleDataPacket(packet);

        boolean accepted = movement == AuthMovement.UNCHANGED || movement == AuthMovement.CHANGED;
        assertEquals(accepted, player.isUsingItem(), "Rejected movement must not start a charge");
        assertEquals(accepted ? 1 : 0, interactions.get());
        verify(player.getLevel(), times(accepted ? 1 : 0)).addLevelSoundEvent(player,
                LevelSoundEventPacket.SOUND_CROSSBOW_LOADING_START);

        player.forceMovement = null;
        player.teleportPosition = null;
        player.lastTeleportTick = -1;
        player.x = 0;
        authClick(120, UseItemData.TRIGGER_SIMULATION_TICK);

        assertEquals(accepted, held().isLoaded(), "Legal continuation cannot inherit a rejected start");
        assertEquals(accepted ? 2 : 3, player.getInventory().getItem(1).getCount());
        assertEquals(accepted ? 1 : 0, interactions.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void duplicateInputFromTheOtherTransportCannotChargeOrFire(boolean authFirst) {
        player.gameVersion = GameVersion.V1_21_20;
        player.protocol = player.gameVersion.getProtocol();
        MockServer.get().serverAuthoritativeMovementMode = 0;
        input(authFirst, 100, 1);
        input(!authFirst, 120, 2);
        assertFalse(held().isLoaded());
        input(authFirst, 120, 2);
        assertTrue(held().isLoaded());
        input(!authFirst, 131, 1);
        assertTrue(shots.isEmpty());
        input(authFirst, 132, 1);
        assertEquals(1, shots.size());
        assertFalse(held().isLoaded());
    }

    private void input(boolean auth, int atTick, int trigger) {
        if (auth) authClick(atTick, trigger);
        else click(atTick, trigger);
    }

    @Test
    void staleCrossbowClickCannotEquipAnotherHotbarSlot() {
        player.getInventory().setItem(2, new ItemCrossbow());
        click(100, 1);
        tick.set(120);
        InventoryTransactionPacket packet = clickPacket(2);
        ((UseItemData) packet.transactionData).hotbarSlot = 2;
        player.handleInventoryTransactionPacket(packet);

        assertEquals(0, player.getInventory().getHeldItemIndex());
        assertTrue(player.isUsingItem());
        assertEquals(3, player.getInventory().getItem(1).getCount());
        click(121, 2);
        assertTrue(held().isLoaded());
    }

    @Test
    void rapidClicksAndTailPacketsCannotExtendTheLoadedCooldown() {
        click(100, 1);
        click(120, 2);
        for (int t = 121; t <= 130; t++) {
            click(t, 1);
            click(t, 2);
        }
        assertTrue(held().isLoaded());
        assertTrue(shots.isEmpty());
        click(131, 1);
        assertEquals(1, shots.size());
        assertFalse(held().isLoaded());
        click(132, 2);
        click(160, 2);
        assertEquals(2, player.getInventory().getItem(1).getCount());
        assertFalse(player.isUsingItem());
    }

    @Test
    void earlyReleaseCancelsAndAStartSignalCannotFireALoadedCrossbow() {
        click(100, 1);
        release(119);
        click(120, 2);
        assertFalse(held().isLoaded());
        assertFalse(player.isUsingItem());
        assertEquals(3, player.getInventory().getItem(1).getCount());
        click(121, 1);
        release(141);
        tick.set(160);
        assertFalse(player.tryStartUsingHeldItem(held()));
        assertTrue(held().isLoaded());
        assertTrue(shots.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void onClickAirVetoPreventsChargingAndFiring(boolean loaded) {
        ItemCrossbow crossbow = new VetoCrossbow();
        if (loaded) crossbow.createNamedTag(Item.get(Item.ARROW), 1);
        player.getInventory().setItemInHand(crossbow);

        click(100, 1);

        assertFalse(player.isUsingItem(), "A vetoed click must not start charging");
        assertEquals(loaded, held().isLoaded(), "A vetoed click must preserve the loaded state");
        assertTrue(shots.isEmpty(), "A vetoed click must not fire");
        assertEquals(0, held().getDamage());
        assertEquals(3, player.getInventory().getItem(1).getCount());
    }

    @Test
    void startUsingSignalHonoursOnClickAirVeto() {
        player.getInventory().setItemInHand(new VetoCrossbow());

        assertFalse(player.tryStartUsingHeldItem(held()));

        assertFalse(player.isUsingItem());
        assertFalse(held().isLoaded());
        assertEquals(3, player.getInventory().getItem(1).getCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onClickAirCallbackCannotStartChargingAfterInvalidation(boolean replaceHeldItem) {
        player.getInventory().setItemInHand(new CallbackCrossbow(p -> {
            if (replaceHeldItem) p.getInventory().setItemInHand(Item.get(Item.DIAMOND));
            else p.setUsingItem(false);
        }));

        click(100, 1);

        assertFalse(player.isUsingItem());
        assertTrue(shots.isEmpty());
        assertEquals(replaceHeldItem ? Item.DIAMOND : Item.CROSSBOW, player.getInventory().getItemInHand().getId());
        assertEquals(3, player.getInventory().getItem(1).getCount());
    }

    @Test
    void replacementDuringChargeCommitCannotInheritDurabilityOrConsumeAmmo() {
        click(100, 1);
        listener = event -> {
            if (event instanceof EntityInventoryChangeEvent change
                    && change.getNewItem() instanceof ItemCrossbow bow && bow.isLoaded()) {
                player.getInventory().setItemInHand(new ItemCrossbow());
            }
        };
        click(120, 2);
        assertFalse(held().isLoaded());
        assertEquals(0, held().getDamage());
        assertEquals(3, player.getInventory().getItem(1).getCount());
    }

    @Test
    void projectileCallbackReplacementIsNotOverwrittenByTheOldCrossbow() {
        click(100, 1);
        click(120, 2);
        listener = event -> {
            if (event instanceof EntityShootBowEvent) {
                player.getInventory().setItemInHand(Item.get(Item.DIAMOND));
            }
        };
        click(131, 1);
        assertEquals(1, shots.size());
        assertEquals(Item.DIAMOND, player.getInventory().getItemInHand().getId());
    }

    @Test
    void reentrantChargeDuringInventoryEventCommitsOnlyOnce() {
        click(100, 1);
        List<ItemCrossbow.ChargeResult> nested = new ArrayList<>();
        listener = event -> {
            if (event instanceof EntityInventoryChangeEvent change
                    && change.getNewItem() instanceof ItemCrossbow bow && bow.isLoaded()) {
                nested.add(held().tryCharge(player, 20));
            }
        };
        click(120, 2);
        assertEquals(List.of(ItemCrossbow.ChargeResult.CANCELLED), nested);
        assertTrue(held().isLoaded());
        assertEquals(1, held().getDamage());
        assertEquals(2, player.getInventory().getItem(1).getCount());
    }

    @Test
    void modeChangeFromTheInventoryCallbackInvalidatesThePendingCharge() {
        click(100, 1);
        listener = event -> {
            if (event instanceof EntityInventoryChangeEvent change
                    && change.getNewItem() instanceof ItemCrossbow bow && bow.isLoaded()) {
                player.gamemode = Player.CREATIVE;
            }
        };
        click(120, 2);
        assertFalse(held().isLoaded());
        assertEquals(0, held().getDamage());
        assertEquals(3, player.getInventory().getItem(1).getCount());
    }

    @Test
    void stoppingUseFromTheInventoryCallbackCancelsThePendingCommit() {
        click(100, 1);
        listener = event -> {
            if (event instanceof EntityInventoryChangeEvent change
                    && change.getNewItem() instanceof ItemCrossbow bow && bow.isLoaded()) {
                player.setUsingItem(false);
            }
        };
        click(120, 2);
        assertFalse(held().isLoaded());
        assertEquals(0, held().getDamage());
        assertEquals(3, player.getInventory().getItem(1).getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 255})
    void invalidTriggerCannotStartUsingTheCrossbow(int trigger) {
        click(100, trigger);
        click(120, trigger);
        assertFalse(player.isUsingItem());
        assertFalse(held().isLoaded());
        assertEquals(3, player.getInventory().getItem(1).getCount());
    }

    enum ChargeVariant { LAST_ARROW, CREATIVE, MULTISHOT, QUICK_CHARGE, OFFHAND_FIREWORK, OFFHAND_TIPPED_ARROW }

    @ParameterizedTest
    @EnumSource(ChargeVariant.class)
    void chargePreservesAmmoPriorityAndEnchantmentRules(ChargeVariant variant) {
        ItemCrossbow bow = held();
        int duration = 20;
        if (variant == ChargeVariant.LAST_ARROW) player.getInventory().setItem(1, Item.get(Item.ARROW));
        if (variant == ChargeVariant.CREATIVE) {
            player.gamemode = Player.CREATIVE;
            player.getInventory().clear(1);
        }
        if (variant == ChargeVariant.MULTISHOT) {
            bow.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_CROSSBOW_MULTISHOT).setLevel(1),
                    Enchantment.getEnchantment(Enchantment.ID_BOW_INFINITY).setLevel(1));
        }
        if (variant == ChargeVariant.QUICK_CHARGE) {
            bow.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_CROSSBOW_QUICK_CHARGE).setLevel(3));
            duration = 5;
        }
        if (variant == ChargeVariant.OFFHAND_FIREWORK) {
            player.getOffhandInventory().setItem(0, Item.get(Item.FIREWORKS, 0, 3));
        }
        if (variant == ChargeVariant.OFFHAND_TIPPED_ARROW) {
            player.getOffhandInventory().setItem(0, Item.get(Item.ARROW, ItemArrow.TIPPED_ARROW + Potion.SPEED, 3));
        }
        player.getInventory().setItemInHand(bow);
        click(100, 1);
        click(100 + duration - 1, 2);
        assertFalse(held().isLoaded());
        click(100 + duration, 2);
        assertTrue(held().isLoaded());
        assertEquals(variant == ChargeVariant.CREATIVE ? 0 : variant == ChargeVariant.OFFHAND_FIREWORK ? 3 : 1,
                held().getDamage());
        assertEquals(variant == ChargeVariant.MULTISHOT ? 3 : 1, held().getNamedTag().getInt("launchCount"));
        if (variant == ChargeVariant.OFFHAND_FIREWORK || variant == ChargeVariant.OFFHAND_TIPPED_ARROW) {
            assertEquals(3, player.getInventory().getItem(1).getCount());
            assertEquals(2, player.getOffhandInventory().getItem(0).getCount());
            assertTrue(held().getChargedItem().equals(player.getOffhandInventory().getItem(0)));
        } else if (variant == ChargeVariant.CREATIVE || variant == ChargeVariant.LAST_ARROW) {
            assertTrue(player.getInventory().getItem(1).isNull());
        } else {
            assertEquals(2, player.getInventory().getItem(1).getCount());
        }
    }

    private void authClick(int atTick, int trigger) {
        tick.set(atTick);
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket();
        cb.setRotation(org.cloudburstmc.math.vector.Vector3f.ZERO);
        cb.setPosition(org.cloudburstmc.math.vector.Vector3f.ZERO);
        cb.setMotion(org.cloudburstmc.math.vector.Vector2f.ZERO);
        cb.setDelta(org.cloudburstmc.math.vector.Vector3f.ZERO);
        cb.setAnalogMoveVector(org.cloudburstmc.math.vector.Vector2f.ZERO);
        cb.setInputMode(org.cloudburstmc.protocol.bedrock.data.InputMode.MOUSE);
        cb.setPlayMode(org.cloudburstmc.protocol.bedrock.data.ClientPlayMode.NORMAL);
        cb.setInputInteractionModel(org.cloudburstmc.protocol.bedrock.data.InputInteractionModel.CROSSHAIR);
        cb.getInputData().add(org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData.PERFORM_ITEM_INTERACTION);
        cb.setTick(atTick);
        var use = new org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction();
        use.setActionType(InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR);
        use.setTriggerType(org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction.TriggerType.values()[trigger]);
        use.setBlockPosition(org.cloudburstmc.math.vector.Vector3i.ZERO);
        use.setBlockFace(0);
        use.setHotbarSlot(player.getInventory().getHeldItemIndex());
        var definition = new org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition("minecraft:crossbow",
                RuntimeItems.getMapping(player.gameVersion).toRuntime(Item.CROSSBOW, 0).getRuntimeId(), false);
        use.setItemInHand(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.builder()
                .definition(definition).count(1).damage(held().getDamage()).build());
        use.setPlayerPosition(org.cloudburstmc.math.vector.Vector3f.ZERO);
        use.setClickPosition(org.cloudburstmc.math.vector.Vector3f.ZERO);
        use.setBlockDefinition(new org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition(
                "minecraft:air", 0, org.cloudburstmc.nbt.NbtMap.EMPTY));
        use.setClientInteractPrediction(org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction.PredictedResult.SUCCESS);
        cb.setItemUseTransaction(use);
        PlayerAuthInputPacket packet = crossEncode(cb, PlayerAuthInputPacket::new, player.protocol,
                helper -> helper.setItemDefinitions(org.cloudburstmc.protocol.common.SimpleDefinitionRegistry
                        .<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition>builder().add(definition).build()));
        assertEquals(packet.getCount(), packet.getOffset());
        player.handleDataPacket(packet);
    }

    private void release(int atTick) {
        tick.set(atTick);
        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.transactionType = InventoryTransactionPacket.TYPE_RELEASE_ITEM;
        packet.actions = new NetworkInventoryAction[0];
        ReleaseItemData data = new ReleaseItemData();
        data.actionType = InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE;
        data.hotbarSlot = player.getInventory().getHeldItemIndex();
        data.itemInHand = player.getInventory().getItemInHand();
        packet.transactionData = data;
        player.handleInventoryTransactionPacket(packet);
    }

    @ParameterizedTest
    @EnumSource(CancelledWrite.class)
    void cancelledInventoryWriteCannotPartiallyChargeCrossbow(CancelledWrite cancelledWrite) {
        if (cancelledWrite == CancelledWrite.OFFHAND_AMMO || cancelledWrite == CancelledWrite.OFFHAND_EVENT) {
            assertTrue(player.getOffhandInventory().setItem(0, Item.get(Item.ARROW, 0, 3)));
        }
        listener = event -> {
            if (cancelledWrite == CancelledWrite.OFFHAND_EVENT) {
                if (event instanceof PlayerOffhandInventoryChangeEvent change) change.setCancelled();
                return;
            }
            if (!(event instanceof EntityInventoryChangeEvent change)) return;
            if (cancelledWrite == CancelledWrite.CHARGED_ITEM) {
                if (change.getNewItem() instanceof ItemCrossbow crossbow && crossbow.isLoaded()) {
                    change.setCancelled();
                }
            } else if (change.getOldItem().getId() == Item.ARROW
                    && change.getNewItem().getCount() < change.getOldItem().getCount()) {
                change.setCancelled();
            }
        };
        click(100, 1);
        click(120, 2);

        assertFalse(held().isLoaded());
        assertEquals(0, held().getDamage(), "A rejected charge must not spend durability");
        assertEquals(3, player.getInventory().getItem(1).getCount());
        if (cancelledWrite == CancelledWrite.OFFHAND_AMMO || cancelledWrite == CancelledWrite.OFFHAND_EVENT) {
            assertEquals(3, player.getOffhandInventory().getItem(0).getCount());
        }
    }

    private void click(int atTick, int triggerType) {
        tick.set(atTick);
        player.handleInventoryTransactionPacket(clickPacket(triggerType));
    }

    private InventoryTransactionPacket clickPacket(int triggerType) {
        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.protocol = player.protocol;
        packet.gameVersion = player.gameVersion;
        packet.transactionType = InventoryTransactionPacket.TYPE_USE_ITEM;
        packet.actions = new NetworkInventoryAction[0];
        UseItemData data = new UseItemData();
        data.actionType = InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR;
        data.triggerType = triggerType;
        data.hotbarSlot = player.getInventory().getHeldItemIndex();
        data.itemInHand = player.getInventory().getItemInHand();
        data.blockPos = new BlockVector3();
        data.face = BlockFace.DOWN;
        data.playerPos = new Vector3();
        data.clickPos = new Vector3f();
        packet.transactionData = data;
        packet.encode();
        InventoryTransactionPacket decoded = new InventoryTransactionPacket();
        decoded.protocol = packet.protocol;
        decoded.gameVersion = packet.gameVersion;
        decoded.setBuffer(packet.getBuffer(), 1);
        decoded.decode();
        return decoded;
    }

    private static final class VetoCrossbow extends ItemCrossbow {
        @Override
        public boolean onClickAir(Player player, Vector3 directionVector) {
            return false;
        }
    }

    private static final class CallbackCrossbow extends ItemCrossbow {
        private final Consumer<Player> callback;

        CallbackCrossbow(Consumer<Player> callback) {
            this.callback = callback;
        }

        @Override
        public boolean onClickAir(Player player, Vector3 directionVector) {
            callback.accept(player);
            return super.onClickAir(player, directionVector);
        }
    }

    private static final class TestPlayer extends Player {
        TestPlayer(SourceInterface source, Level level, FullChunk chunk) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
            protocol = GameVersion.V1_21_124.getProtocol();
            gameVersion = GameVersion.V1_21_124;
            spawned = loggedIn = loginVerified = true;
            gamemode = SURVIVAL;
            health = 20;
            this.level = level;
            this.chunk = chunk;
            inventory = new PlayerInventory(this);
            offhandInventory = new PlayerOffhandInventory(this);
            adventureSettings = new AdventureSettings(this);
            temporalVector = new Vector3();
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            return true;
        }
    }
}
