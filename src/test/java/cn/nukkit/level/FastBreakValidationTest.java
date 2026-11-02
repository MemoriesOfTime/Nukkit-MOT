package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.block.*;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 反快速破坏校验：接受 65% 进度容差内的合法预测，同时拒绝瞬破。
 * <p>
 * Fast-break validation: accepts legitimate predictions within the 65% progress
 * tolerance while still rejecting instant breaks.
 */
class FastBreakValidationTest {

    @BeforeAll
    static void init() {
        MockServer.init();
        Effect.init();
    }

    @Nested
    class RealUseBreakOnPath {

        @Test
        void instaBreakOfCommonBlocksIsRejected() {
            assertNull(breakElapsedMs(new BlockDirt(), Item.get(Item.AIR), false, 0),
                    "insta-break of dirt with a bare hand must be rejected");
            assertNull(breakElapsedMs(new BlockStone(), Item.get(Item.WOODEN_PICKAXE), false, 0),
                    "insta-break of stone with a wooden pickaxe must be rejected");
            assertNull(breakElapsedMs(new BlockObsidian(), efficiencyVDiamondPick(), true, 0),
                    "insta-break of obsidian with efficiency V + haste II must be rejected");
            assertNull(breakElapsedMs(new BlockObsidian(), Item.get(Item.DIAMOND_PICKAXE), false, 0),
                    "insta-break of unenchanted obsidian must be rejected");
        }

        @Test
        void reportedObsidianSetupSurvivesNetworkJitter() {
            // 16 tick（800ms）的合法完成扣除 250ms 上行抖动后仍须高于阈值（≈498ms）
            assertNotNull(breakElapsedMs(new BlockObsidian(), efficiencyVDiamondPick(), true, 550),
                    "obsidian + efficiency V + haste II finished at 550ms must be accepted");
            assertNull(breakElapsedMs(new BlockObsidian(), efficiencyVDiamondPick(), true, 200),
                    "obsidian + efficiency V + haste II finished at 200ms must stay rejected");
        }

        @Test
        void shortButNotInstantBlocksKeepAPositiveThreshold() {
            // 石铲挖泥土、金镐挖石头均 187.5ms（4 tick）：宽限固定 150ms，阈值须为正的 37.5ms
            assertNull(breakElapsedMs(new BlockDirt(), Item.get(Item.STONE_SHOVEL), false, 0),
                    "insta-break of dirt with a stone shovel must be rejected");
            assertNull(breakElapsedMs(new BlockStone(), Item.get(Item.GOLD_PICKAXE), false, 0),
                    "insta-break of stone with a golden pickaxe must be rejected");
            assertNotNull(breakElapsedMs(new BlockDirt(), Item.get(Item.STONE_SHOVEL), false, 50),
                    "dirt with a stone shovel finished at 50ms must be accepted");
        }

        @Test
        void firstBreakEverPassesWithoutATimer() {
            Player player = survivalPlayer(false);
            Item result = levelWith(new BlockObsidian()).useBreakOn(
                    new Vector3(0, 64, 0), BlockFace.UP, efficiencyVDiamondPick(), player, true);
            assertNotNull(result, "the first break has no timer yet and must pass");
        }

        @Test
        void highMiningFatigueStillRejectsInstantBreak() {
            // 高等级疲劳把阈值推到远超 long：截止时间若经 long 构造会溢出回绕而放行瞬破
            for (int amplifier : new int[]{40, 255}) {
                Level level = levelWith(new BlockObsidian());
                Player player = survivalPlayer(false);
                player.addEffect(Effect.getEffect(Effect.MINING_FATIGUE)
                        .setAmplifier(amplifier).setDuration(20 * 60));
                player.lastBreak = System.currentTimeMillis();
                assertNull(level.useBreakOn(new Vector3(0, 64, 0), BlockFace.UP,
                                Item.get(Item.DIAMOND_PICKAXE), player, true),
                        "insta-break under mining fatigue amplifier " + amplifier + " must be rejected");
            }
        }

        /**
         * Drives the real {@code Level.useBreakOn} with {@code lastBreak} set {@code elapsedMs}
         * ago; null means the break was refused as fast-break.
         */
        private static Item breakElapsedMs(Block block, Item tool, boolean haste2, long elapsedMs) {
            Level level = levelWith(block);
            Player player = survivalPlayer(haste2);
            player.lastBreak = System.currentTimeMillis() - elapsedMs;
            return level.useBreakOn(new Vector3(0, 64, 0), BlockFace.UP, tool, player, true);
        }

        private static Level levelWith(Block block) {
            Level level = Mockito.mock(Level.class);
            setField(level, "server", MockServer.get());
            setField(level, "gameRules", GameRules.getDefault());
            setField(level, "vibrationManager", Mockito.mock(VibrationManager.class));
            Mockito.doCallRealMethod().when(level).useBreakOn(
                    Mockito.any(Vector3.class), Mockito.any(BlockFace.class),
                    Mockito.any(Item.class), Mockito.any(Player.class), Mockito.anyBoolean());
            block.level = level;
            Mockito.when(level.getBlock(Mockito.any(Vector3.class))).thenReturn(block);
            Mockito.when(level.getChunkPlayers(Mockito.anyInt(), Mockito.anyInt())).thenReturn(new HashMap<>());
            return level;
        }

        private static Player survivalPlayer(boolean haste2) {
            Player player = new TestPlayer();
            if (haste2) {
                player.addEffect(Effect.getEffect(Effect.HASTE).setAmplifier(1).setDuration(20 * 60));
            }
            return player;
        }

        private static final class TestPlayer extends Player {
            TestPlayer() {
                super(Mockito.mock(SourceInterface.class), 1L, new InetSocketAddress("127.0.0.1", 19132));
                this.protocol = 819;
                this.spawned = true;
                this.loggedIn = true;
                this.loginVerified = true;
                this.gamemode = Player.SURVIVAL;
                this.health = 20;
                this.y = 64;
                this.foodData = Mockito.mock(cn.nukkit.PlayerFood.class);
                this.temporalVector = new Vector3();
                this.inventory = Mockito.mock(PlayerInventory.class);
                Mockito.when(this.inventory.getItemInHand()).thenReturn(Item.get(Item.AIR));
            }

            @Override
            public boolean dataPacket(DataPacket packet) {
                return true;
            }
        }

        private static void setField(Object target, String name, Object value) {
            try {
                var field = Level.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        private static Item efficiencyVDiamondPick() {
            Item pick = Item.get(Item.DIAMOND_PICKAXE);
            pick.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_EFFICIENCY).setLevel(5));
            return pick;
        }
    }

    @Nested
    class ValidationFloor {

        private static Player playerWith(int hasteAmplifier, boolean submerged) {
            Player player = Mockito.mock(Player.class);
            PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
            Mockito.when(player.getInventory()).thenReturn(inventory);
            Mockito.when(inventory.getHelmet()).thenReturn(Item.AIR_ITEM);
            Mockito.when(player.isSubmerged()).thenReturn(submerged);
            if (hasteAmplifier >= 0) {
                Effect haste = Mockito.mock(Effect.class);
                Mockito.when(player.getEffect(Effect.HASTE)).thenReturn(haste);
                Mockito.when(haste.getAmplifier()).thenReturn(hasteAmplifier);
            }
            return player;
        }

        @Test
        void positionalPenaltiesStayOutOfTheValidationFloor() {
            BlockObsidian obsidian = new BlockObsidian();
            Item pick = RealUseBreakOnPath.efficiencyVDiamondPick();
            double unsubmerged = obsidian.calculateFastestLegitBreakTime(pick, playerWith(1, false));
            double submerged = obsidian.calculateFastestLegitBreakTime(pick, playerWith(1, true));

            assertEquals(unsubmerged, submerged, 1e-12,
                    "the underwater penalty is applied per tick by the client and must not enter the validation floor");

            double submergedNotInAir = obsidian.calculateBreakTimeNotInAir(pick, playerWith(1, true));
            assertEquals(unsubmerged * 5d, submergedNotInAir, 1e-12,
                    "calculateBreakTimeNotInAir keeps the underwater penalty for prediction/display use");
        }

        @Test
        void bedrockHasteFormulaIsPreservedThroughTheFloor() {
            BlockUndyedShulkerBox shulkerBox = new BlockUndyedShulkerBox();
            int hasteLevel = 4;
            double floor = shulkerBox.calculateFastestLegitBreakTime(Item.AIR_ITEM, playerWith(hasteLevel - 1, false));
            double expected = 3d / ((1d + 0.2d * hasteLevel) * Math.pow(1.2d, hasteLevel));

            assertEquals(expected, floor, 1e-12, "haste amplifier 3 must keep the Bedrock break speed");
        }

        @Test
        void obsidianFloorKeepsTheToleranceBounded() {
            BlockObsidian obsidian = new BlockObsidian();
            double floor = obsidian.calculateFastestLegitBreakTime(RealUseBreakOnPath.efficiencyVDiamondPick(), playerWith(1, false));

            assertEquals(35d * 1.5d / (34d * 1.4d * 1.44d), floor, 1e-12,
                    "floor must match the Bedrock client progress");

            // 766ms 落在 65% 进度容差区段：required = 65% × t
            double requiredSeconds = floor - Math.min(Math.max(0.15, floor * 0.35), 0.45);
            assertTrue(requiredSeconds > 0.45d && requiredSeconds < 0.55d,
                    "the required elapsed time must stay a bounded fraction of the break time");
        }
    }
}
