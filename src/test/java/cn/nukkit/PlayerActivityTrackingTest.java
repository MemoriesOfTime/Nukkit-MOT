package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerKickEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.process.DataPacketManager;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.*;
import cn.nukkit.network.protocol.types.inventory.InventoryLayout;
import cn.nukkit.network.protocol.types.inventory.InventoryTabLeft;
import cn.nukkit.network.protocol.types.inventory.InventoryTabRight;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerActivityTrackingTest {

    @BeforeAll
    static void setUpRegistry() {
        MockServer.init();
        DataPacketManager.registerDefaultProcessors();
    }

    @ParameterizedTest
    @ValueSource(ints = {
            ProtocolInfo.v1_20_0,
            ProtocolInfo.v1_21_50,
            ProtocolInfo.v1_26_40,
            ProtocolInfo.v1_26_50_27,
            ProtocolInfo.v1_26_60
    })
    void decodedAuthInputSeparatesPassivePhysicsFlagsFromHumanControls(int protocol) {
        EnumSet<AuthInputAction> passive = EnumSet.of(
                AuthInputAction.SPRINT_DOWN,
                AuthInputAction.AUTO_JUMPING_IN_WATER,
                AuthInputAction.SNEAKING,
                AuthInputAction.SNEAK_DOWN,
                AuthInputAction.SPRINTING,
                AuthInputAction.PERSIST_SNEAK,
                AuthInputAction.HANDLE_TELEPORT,
                AuthInputAction.EMOTING,
                AuthInputAction.RECEIVED_SERVER_DATA,
                AuthInputAction.BLOCK_BREAKING_DELAY_ENABLED,
                AuthInputAction.HORIZONTAL_COLLISION,
                AuthInputAction.VERTICAL_COLLISION
        );
        if (protocol >= ProtocolInfo.v1_21_40) {
            passive.add(AuthInputAction.CAMERA_RELATIVE_MOVEMENT_ENABLED);
            passive.add(AuthInputAction.ROT_CONTROLLED_BY_MOVE_DIRECTION);
        }
        if (protocol >= ProtocolInfo.v1_21_50) {
            passive.add(AuthInputAction.HOTBAR_ONLY_TOUCH);
        }
        if (protocol >= ProtocolInfo.v1_26_40) {
            passive.add(AuthInputAction.SNEAK_CURRENT_RAW);
            passive.add(AuthInputAction.INTERNAL_UPDATE);
        }

        PlayerAuthInputPacket passivePacket = decodeAuthInput(protocol, passive, 0f, 0f, 0f, 0f);
        assertFalse(passivePacket.hasHumanInputAction(), "passive physics/toggle flags must not count as human input");
        assertFalse(passivePacket.hasControlInput(), "zero move vectors + passive flags must not count as control input");

        PlayerAuthInputPacket sneakTap = decodeAuthInput(protocol, EnumSet.of(AuthInputAction.START_SNEAKING), 0f, 0f, 0f, 0f);
        assertTrue(sneakTap.hasHumanInputAction());
        assertTrue(sneakTap.hasControlInput());

        PlayerAuthInputPacket stickMove = decodeAuthInput(protocol, passive, 0f, 0f, 0.35f, 0f);
        assertFalse(stickMove.hasHumanInputAction());
        assertTrue(stickMove.hasControlInput(), "non-zero analog stick vector must count as control input");
    }

    @Test
    void waterDisplacementMarksPassiveMoveWhileGenuineMoveMarksActiveInput() {
        try (TestRig rig = new TestRig(ProtocolInfo.v1_26_60, 200)) {
            List<Boolean> movePassiveFlags = new ArrayList<>();
            rig.manager.registerEvent(PlayerMoveEvent.class, new Listener() {}, EventPriority.NORMAL,
                    (listener, event) -> {
                        PlayerMoveEvent move = (PlayerMoveEvent) event;
                        if (move.getPlayer() == rig.player) {
                            movePassiveFlags.add(rig.player.isPassiveDisplacementMove());
                        }
                    }, rig.plugin);

            // Seed firstMove on tick 201 via passive settling without human input.
            rig.setTick(201);
            rig.sendAuthInput(rig.start.add(0.05, 0, 0), rig.player.yaw, rig.player.pitch, rig.player.headYaw,
                    EnumSet.of(AuthInputAction.VERTICAL_COLLISION, AuthInputAction.AUTO_JUMPING_IN_WATER), 0f, 0f);
            rig.player.onUpdate(201);
            assertEquals(200, rig.player.getLastActionTick(), "spawn action tick must not advance on passive drift");
            assertEquals(0, rig.player.getLastClientInputTick());

            // External water stream pushes AFK player on tick 202.
            rig.setTick(202);
            rig.sendAuthInput(rig.start.add(0.25, 0, 0), rig.player.yaw, rig.player.pitch, rig.player.headYaw,
                    EnumSet.of(AuthInputAction.VERTICAL_COLLISION, AuthInputAction.AUTO_JUMPING_IN_WATER, AuthInputAction.PERSIST_SNEAK),
                    0f, 0f);
            rig.player.onUpdate(202);
            assertEquals(1, movePassiveFlags.size());
            assertTrue(movePassiveFlags.get(0), "external water push must expose isPassiveDisplacementMove() == true");
            assertFalse(rig.player.isPassiveDisplacementMove(), "flag must clear immediately after handleMovement");
            assertEquals(0, rig.player.getLastClientInputTick(), "passive displacement must not advance client input tick");

            // Genuine WASD step on tick 205.
            rig.setTick(205);
            rig.sendAuthInput(rig.start.add(0.50, 0, 0), rig.player.yaw, rig.player.pitch, rig.player.headYaw,
                    EnumSet.of(AuthInputAction.UP, AuthInputAction.VERTICAL_COLLISION), 0f, 1.0f);
            rig.player.onUpdate(205);
            assertEquals(2, movePassiveFlags.size());
            assertFalse(movePassiveFlags.get(1), "WASD movement must expose isPassiveDisplacementMove() == false");
            assertEquals(205, rig.player.getLastClientInputTick());
            assertEquals(205, rig.player.getLastActionTick());
        }
    }

    @Test
    void teleportFloatRoundingAndPreTeleportPacketsNeverTriggerFalseCameraTurn() {
        try (TestRig rig = new TestRig(ProtocolInfo.v1_26_60, 300)) {
            double preciseYaw = 137.123456789;
            double precisePitch = -14.987654321;
            rig.setTick(310);
            rig.player.lastTeleportTick = 310;
            rig.player.sendPosition(rig.start, preciseYaw, precisePitch, preciseYaw, MovePlayerPacket.MODE_RESET);

            // Stale pre-teleport packet with old rotation (0, 0) arrives before HANDLE_TELEPORT.
            rig.setTick(311);
            rig.sendAuthInput(rig.start, 0.0, 0.0, 0.0, EnumSet.of(AuthInputAction.VERTICAL_COLLISION), 0f, 0f);
            assertEquals(0, rig.player.getLastClientInputTick(), "stale pre-teleport packet must be ignored");

            // Client acknowledges teleport with float-rounded rotation.
            float wireYaw = (float) preciseYaw;
            float wirePitch = (float) precisePitch;
            rig.setTick(312);
            rig.sendAuthInput(rig.start, wireYaw, wirePitch, wireYaw,
                    EnumSet.of(AuthInputAction.HANDLE_TELEPORT, AuthInputAction.VERTICAL_COLLISION), 0f, 0f);
            assertEquals(0, rig.player.getLastClientInputTick(), "HANDLE_TELEPORT snap must not count as camera input");

            // Subsequent idle ticks echoing the exact float rotation.
            rig.setTick(313);
            rig.sendAuthInput(rig.start, wireYaw, wirePitch, wireYaw,
                    EnumSet.of(AuthInputAction.VERTICAL_COLLISION), 0f, 0f);
            assertEquals(0, rig.player.getLastClientInputTick(), "echoed post-teleport float rotation must not count as input");

            // Real mouse look (+3.5 deg yaw) immediately registers input even while standing still.
            rig.setTick(314);
            rig.sendAuthInput(rig.start, wireYaw + 3.5f, wirePitch, wireYaw + 3.5f,
                    EnumSet.of(AuthInputAction.VERTICAL_COLLISION), 0f, 0f);
            assertEquals(314, rig.player.getLastClientInputTick(), "stationary mouse look must advance client input tick");
        }
    }

    @Test
    void mouseoverHotbarEchoAndBusyFormsStaySilentWhileGenuineUiPacketsRecordActivity() {
        try (TestRig rig = new TestRig(ProtocolInfo.v1_26_60, 400)) {
            // 1. InteractPacket.ACTION_MOUSEOVER (passive crosshair hover) and initial background inventory options sync must NOT count.
            rig.setTick(405);
            InteractPacket mouseOver = new InteractPacket();
            mouseOver.action = InteractPacket.ACTION_MOUSEOVER;
            mouseOver.target = 0;
            mouseOver.protocol = rig.player.protocol;
            mouseOver.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(mouseOver);
            assertEquals(0, rig.player.getLastClientInputTick());
            assertEquals(400, rig.player.getLastActionTick());

            SetPlayerInventoryOptionsPacket initialInvOpts = new SetPlayerInventoryOptionsPacket();
            initialInvOpts.leftTab = InventoryTabLeft.SURVIVAL;
            initialInvOpts.rightTab = InventoryTabRight.CRAFTING;
            initialInvOpts.layout = InventoryLayout.SURVIVAL;
            initialInvOpts.craftingLayout = InventoryLayout.SURVIVAL;
            initialInvOpts.protocol = rig.player.protocol;
            initialInvOpts.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(initialInvOpts);
            assertEquals(0, rig.player.getLastClientInputTick(), "initial background inventory options sync must not count");

            assertFalse(rig.player.hasActiveUiScreen());

            // 2. InteractPacket.ACTION_OPEN_INVENTORY counts as client UI input.
            rig.setTick(406);
            InteractPacket openInv = new InteractPacket();
            openInv.action = InteractPacket.ACTION_OPEN_INVENTORY;
            openInv.target = rig.player.getId();
            openInv.protocol = rig.player.protocol;
            openInv.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(openInv);
            assertEquals(406, rig.player.getLastClientInputTick());

            ContainerClosePacket closeInv = new ContainerClosePacket();
            closeInv.windowId = -1;
            closeInv.wasServerInitiated = false;
            closeInv.protocol = rig.player.protocol;
            closeInv.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(closeInv);

            // 3. MobEquipmentPacket echoing current hotbar slot 0 stays silent; switching to slot 2 records input.
            rig.setTick(410);
            MobEquipmentPacket sameSlot = new MobEquipmentPacket();
            sameSlot.windowId = ContainerIds.INVENTORY;
            sameSlot.hotbarSlot = 0;
            sameSlot.inventorySlot = 0;
            sameSlot.item = rig.player.getInventory().getItem(0);
            sameSlot.protocol = rig.player.protocol;
            sameSlot.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(sameSlot);
            assertEquals(406, rig.player.getLastClientInputTick(), "hotbar echo on same slot must not count");

            rig.setTick(411);
            MobEquipmentPacket newSlot = new MobEquipmentPacket();
            newSlot.windowId = ContainerIds.INVENTORY;
            newSlot.hotbarSlot = 2;
            newSlot.inventorySlot = 2;
            newSlot.item = rig.player.getInventory().getItem(2);
            newSlot.protocol = rig.player.protocol;
            newSlot.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(newSlot);
            assertEquals(411, rig.player.getLastClientInputTick(), "switching hotbar slot must count");

            // 4. ModalFormResponsePacket: USER_BUSY ignored, USER_CLOSED counts as client input, submit updates action time.
            assertFalse(rig.player.hasActiveUiScreen());
            int busyFormId = rig.player.showFormWindow(new FormWindowSimple("Busy", ""));
            assertTrue(rig.player.hasActiveUiScreen(), "open form must mark hasActiveUiScreen()");
            rig.setTick(415);
            ModalFormResponsePacket busy = new ModalFormResponsePacket();
            busy.formId = busyFormId;
            busy.data = "null";
            busy.hasCancelReason = true;
            busy.cancelReason = ModalFormResponsePacket.CANCEL_REASON_USER_BUSY;
            busy.protocol = rig.player.protocol;
            busy.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(busy);
            assertFalse(rig.player.hasActiveUiScreen(), "closed form must clear hasActiveUiScreen()");
            assertEquals(411, rig.player.getLastClientInputTick());
            assertEquals(411, rig.player.getLastActionTick());

            int closedFormId = rig.player.showFormWindow(new FormWindowSimple("Close", ""));
            rig.setTick(416);
            ModalFormResponsePacket userClosed = new ModalFormResponsePacket();
            userClosed.formId = closedFormId;
            userClosed.data = "null";
            userClosed.hasCancelReason = true;
            userClosed.cancelReason = ModalFormResponsePacket.CANCEL_REASON_USER_CLOSED;
            userClosed.protocol = rig.player.protocol;
            userClosed.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(userClosed);
            assertEquals(416, rig.player.getLastClientInputTick());

            int submitFormId = rig.player.showFormWindow(new FormWindowSimple("Submit", ""));
            rig.setTick(418);
            ModalFormResponsePacket submitted = new ModalFormResponsePacket();
            submitted.formId = submitFormId;
            submitted.data = "0";
            submitted.protocol = rig.player.protocol;
            submitted.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(submitted);
            assertEquals(416, rig.player.getLastClientInputTick(), "form submit delegates gameplay input gate to FormGateway");
            assertEquals(418, rig.player.getLastActionTick(), "form submit resets core action timestamp");

            // 5. CommandRequestPacket resets core action time without bypassing domain command AFK checks.
            rig.setTick(420);
            CommandRequestPacket cmd = new CommandRequestPacket();
            cmd.command = "/afk";
            cmd.protocol = rig.player.protocol;
            cmd.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(cmd);
            assertEquals(416, rig.player.getLastClientInputTick());
            assertEquals(420, rig.player.getLastActionTick());

            // 6. Modern UI packets (inventory options, stonecutter recipe, cursor drag).
            rig.setTick(426);
            SetPlayerInventoryOptionsPacket changedInvOpts = new SetPlayerInventoryOptionsPacket();
            changedInvOpts.leftTab = InventoryTabLeft.RECIPE_EQUIPMENT;
            changedInvOpts.rightTab = InventoryTabRight.CRAFTING;
            changedInvOpts.filtering = true;
            changedInvOpts.layout = InventoryLayout.RECIPE_BOOK;
            changedInvOpts.craftingLayout = InventoryLayout.SURVIVAL;
            changedInvOpts.protocol = rig.player.protocol;
            changedInvOpts.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(changedInvOpts);
            assertEquals(426, rig.player.getLastClientInputTick(), "changing recipe tab/filter must count");

            rig.setTick(427);
            ServerboundStonecutterSetRecipePacket stonecutter = new ServerboundStonecutterSetRecipePacket();
            stonecutter.containerId = 2;
            stonecutter.recipeIndex = 1;
            stonecutter.protocol = rig.player.protocol;
            stonecutter.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(stonecutter);
            assertEquals(427, rig.player.getLastClientInputTick());

            rig.setTick(428);
            ServerboundCursorItemDragPacket drag = new ServerboundCursorItemDragPacket();
            drag.state = ServerboundCursorItemDragPacket.STATE_START;
            drag.protocol = rig.player.protocol;
            drag.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(drag);
            assertEquals(428, rig.player.getLastClientInputTick());

            rig.setTick(429);
            SettingsCommandPacket settingsCommand = new SettingsCommandPacket();
            settingsCommand.command = "/gamerule dodaylightcycle true";
            settingsCommand.suppressOutput = true;
            settingsCommand.protocol = rig.player.protocol;
            settingsCommand.gameVersion = rig.player.getGameVersion();
            rig.player.handleDataPacket(settingsCommand);
            assertEquals(429, rig.player.getLastClientInputTick());
        }
    }

    @Test
    void playerIdleTimeoutKicksOnlyExpiredNonBypassPlayersWhenConfigured() {
        try (TestRig rig = new TestRig(ProtocolInfo.v1_26_60, 500)) {
            AtomicReference<PlayerKickEvent.Reason> kickReason = new AtomicReference<>();
            AtomicBoolean cancelKick = new AtomicBoolean(true);
            rig.manager.registerEvent(PlayerKickEvent.class, new Listener() {}, EventPriority.NORMAL,
                    (listener, event) -> {
                        PlayerKickEvent kick = (PlayerKickEvent) event;
                        if (kick.getPlayer() == rig.player) {
                            kickReason.set(kick.getReasonEnum());
                            if (cancelKick.get()) {
                                kick.setCancelled(true);
                            }
                        }
                    }, rig.plugin);

            // Default 0 -> disabled even when getIdleMillis() is huge.
            when(rig.server.getPlayerIdleTimeout()).thenReturn(0);
            rig.player.forcedIdleMillis = 600_000L;
            rig.setTick(525);
            rig.player.onUpdate(525);
            assertNull(kickReason.get());

            // Enabled (1 minute), idle = 59s -> not kicked yet.
            when(rig.server.getPlayerIdleTimeout()).thenReturn(1);
            rig.player.forcedIdleMillis = 59_999L;
            rig.setTick(550);
            rig.player.onUpdate(550);
            assertNull(kickReason.get());

            // Bypass permission prevents idle kick.
            rig.player.forcedIdleMillis = 61_000L;
            rig.player.bypassIdle = true;
            rig.setTick(575);
            rig.player.onUpdate(575);
            assertNull(kickReason.get());

            // Without bypass and >= 60_000ms -> fires PlayerKickEvent(Reason.IDLE_TIMEOUT).
            rig.player.bypassIdle = false;
            rig.setTick(600);
            rig.player.onUpdate(600);
            assertEquals(PlayerKickEvent.Reason.IDLE_TIMEOUT, kickReason.get());
        }
    }

    private static PlayerAuthInputPacket decodeAuthInput(
            int protocol,
            EnumSet<AuthInputAction> actions,
            float moveX,
            float moveY,
            float analogX,
            float analogY
    ) {
        GameVersion version = GameVersion.byProtocol(protocol, false);
        BinaryStream stream = new BinaryStream();
        stream.putLFloat(0f); // pitch
        stream.putLFloat(0f); // yaw
        stream.putVector3f(new Vector3f(0f, 65.62f, 0f)); // position
        stream.putLFloat(moveX);
        stream.putLFloat(moveY);
        stream.putLFloat(0f); // headYaw

        boolean v2168 = protocol >= ProtocolInfo.v1_26_40;
        boolean v2192 = protocol >= ProtocolInfo.v1_26_50_27;
        if (v2168) {
            if (!v2192) {
                stream.putBoolean(true);
            }
            stream.putUnsignedVarInt(actions.size());
            for (AuthInputAction action : actions) {
                stream.putVarInt(action.ordinal());
            }
        } else {
            long mask = 0L;
            for (AuthInputAction action : actions) {
                if (action.ordinal() < Long.SIZE) {
                    mask |= (1L << action.ordinal());
                }
            }
            stream.putUnsignedVarLong(mask);
        }

        stream.putUnsignedVarInt(InputMode.MOUSE.ordinal());
        stream.putUnsignedVarInt(ClientPlayMode.SCREEN.ordinal());
        if (protocol >= ProtocolInfo.v1_19_0_29) {
            if (v2168) {
                stream.putVarInt(AuthInteractionModel.CROSSHAIR.ordinal());
            } else {
                stream.putUnsignedVarInt(AuthInteractionModel.CROSSHAIR.ordinal());
            }
        }
        if (protocol >= ProtocolInfo.v1_21_40) {
            stream.putVector2f(new Vector2f(0f, 0f));
        }
        stream.putUnsignedVarLong(1L); // tick
        stream.putVector3f(new Vector3f(0f, 0f, 0f)); // delta

        if (v2168) {
            for (int i = 0; i < 5; i++) {
                if (!v2192) {
                    stream.putBoolean(true);
                }
                stream.putBoolean(false);
            }
        }
        if (protocol >= ProtocolInfo.v1_19_70_24) {
            stream.putVector2f(new Vector2f(analogX, analogY));
            if (protocol >= ProtocolInfo.v1_21_40) {
                stream.putVector3f(new Vector3f(0f, 0f, 1f));
            }
            if (protocol >= ProtocolInfo.v1_21_50) {
                stream.putVector2f(new Vector2f(moveX, moveY));
            }
        }

        PlayerAuthInputPacket pk = new PlayerAuthInputPacket();
        pk.protocol = protocol;
        pk.gameVersion = version;
        pk.setBuffer(stream.getBuffer(), 0);
        pk.decode();
        return pk;
    }

    private static final class TestRig implements AutoCloseable {
        final Server server;
        final PluginManager manager;
        final Plugin plugin = mock(Plugin.class);
        final Level level = mock(Level.class);
        final Vector3 start = new Vector3(100.5, 64.0, 100.5);
        final TestPlayer player;

        TestRig(int protocol, int initialTick) {
            MockServer.reset();
            server = MockServer.get();
            manager = new PluginManager(server, null);
            when(server.getPluginManager()).thenReturn(manager);
            when(server.isPrimaryThread()).thenReturn(true);
            when(server.getViewDistance()).thenReturn(2);
            when(plugin.isEnabled()).thenReturn(true);
            setTick(initialTick);

            level.isBeingConverted = true;
            when(server.getDefaultLevel()).thenReturn(level);
            when(level.getServer()).thenReturn(server);
            when(level.getName()).thenReturn("afk-test");
            when(level.getFolderName()).thenReturn("afk-test");
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getGameRules()).thenReturn(GameRules.getDefault());
            when(level.getSafeSpawn(any(Vector3.class))).thenAnswer(c -> Position.fromObject(start, level));
            when(level.getSafeSpawn()).thenReturn(Position.fromObject(start, level));
            when(level.getVibrationManager()).thenReturn(mock(cn.nukkit.level.vibration.VibrationManager.class));

            LevelProvider provider = mock(LevelProvider.class);
            when(provider.getLevel()).thenReturn(level);
            when(level.getProvider()).thenReturn(provider);
            BaseFullChunk chunk = mock(BaseFullChunk.class);
            when(chunk.getProvider()).thenReturn(provider);
            when(chunk.isInitialized()).thenReturn(true);
            when(chunk.isGenerated()).thenReturn(true);
            when(chunk.isPopulated()).thenReturn(true);
            when(chunk.getX()).thenReturn(start.getChunkX());
            when(chunk.getZ()).thenReturn(start.getChunkZ());

            when(level.getChunk(anyInt(), anyInt(), anyBoolean())).thenReturn(chunk);
            when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(chunk);
            when(level.getChunkPlayers(anyInt(), anyInt())).thenAnswer(c -> new HashMap<Integer, Player>());
            when(level.getChunkEntities(anyInt(), anyInt())).thenReturn(new HashMap<>());
            when(level.getChunkEntities(anyInt(), anyInt(), anyBoolean())).thenReturn(new HashMap<>());
            when(level.getNearbyEntities(any(AxisAlignedBB.class), any(Entity.class))).thenReturn(new Entity[0]);
            when(level.getNearbyEntities(any(AxisAlignedBB.class))).thenReturn(new Entity[0]);
            when(level.getNearbyRideables(any(AxisAlignedBB.class), any(Entity.class))).thenReturn(new Entity[0]);
            when(level.getCollidingEntities(any(AxisAlignedBB.class))).thenReturn(new Entity[0]);
            when(level.getBlock(any(Vector3.class))).thenAnswer(c -> block(c.getArgument(0)));
            when(level.getBlock(any(Vector3.class), anyBoolean())).thenAnswer(c -> block(c.getArgument(0)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(c ->
                    block(new Vector3(c.getArgument(0, Integer.class), c.getArgument(1, Integer.class), c.getArgument(2, Integer.class))));
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(c ->
                    block(new Vector3(c.getArgument(0, Integer.class), c.getArgument(1, Integer.class), c.getArgument(2, Integer.class))));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(c ->
                    block(new Vector3(c.getArgument(0, Integer.class), c.getArgument(1, Integer.class), c.getArgument(2, Integer.class))));

            SourceInterface source = mock(SourceInterface.class);
            when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
            player = new TestPlayer(source, protocol);
            player.initialize(chunk, start);
        }

        void setTick(int tick) {
            when(server.getTick()).thenReturn(tick);
        }

        void sendAuthInput(Vector3 feetPos, double yaw, double pitch, double headYaw,
                           EnumSet<AuthInputAction> actions, float moveX, float moveY) {
            PlayerAuthInputPacket pk = new PlayerAuthInputPacket();
            pk.protocol = player.protocol;
            pk.gameVersion = player.getGameVersion();
            pk.setPosition(new Vector3f((float) feetPos.x, (float) (feetPos.y + player.wireOffset()), (float) feetPos.z));
            pk.setYaw((float) yaw);
            pk.setPitch((float) pitch);
            pk.setHeadYaw((float) headYaw);
            pk.setMotion(new Vector2(moveX, moveY));
            pk.setAnalogMoveVector(new Vector2f(moveX, moveY));
            pk.setRawMoveVector(new Vector2f(moveX, moveY));
            pk.setInputMode(InputMode.MOUSE);
            pk.setPlayMode(ClientPlayMode.SCREEN);
            pk.setInteractionModel(AuthInteractionModel.CROSSHAIR);
            pk.setDelta(new Vector3f(0f, 0f, 0f));
            pk.setInputData(actions);
            player.handleDataPacket(pk);
        }

        private Block block(Vector3 p) {
            return Block.get(p.getFloorY() < 64 ? Block.STONE : Block.AIR, 0, level,
                    p.getFloorX(), p.getFloorY(), p.getFloorZ());
        }

        @Override
        public void close() {
            HandlerList.unregisterAll(plugin);
            MockServer.reset();
        }
    }

    private static final class TestPlayer extends Player {
        long forcedIdleMillis = -1L;
        boolean bypassIdle;

        TestPlayer(SourceInterface source, int selectedProtocol) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19198));
            this.protocol = selectedProtocol;
            this.gameVersion = GameVersion.byProtocol(selectedProtocol, false);
            this.username = "AfkProbe";
            this.iusername = "afkprobe";
            this.displayName = "AfkProbe";
            this.loginVerified = true;
            this.loggedIn = true;
            this.adventureSettings = new AdventureSettings(this);
        }

        void initialize(BaseFullChunk chunk, Vector3 point) {
            init(chunk, Entity.getDefaultNBT(point));
            this.foodData = new PlayerFood(this, 20, 20);
            this.server.serverAuthoritativeMovementMode = 1;
            doFirstSpawn();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    this.usedChunks.put(Level.chunkHash(getChunkX() + dx, getChunkZ() + dz), Boolean.TRUE);
                }
            }
            if (this.teleportPosition != null) {
                checkTeleportPosition();
            }
            this.lastTeleportTick = -100;
        }

        float wireOffset() {
            return getBaseOffset();
        }

        @Override
        public long getIdleMillis() {
            return forcedIdleMillis >= 0L ? forcedIdleMillis : super.getIdleMillis();
        }

        @Override
        public boolean hasPermission(String name) {
            if ("nukkit.idle.bypass".equals(name)) {
                return bypassIdle;
            }
            return super.hasPermission(name);
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            return true;
        }
    }
}
