package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LegacyTorchCanonicalDropTest {
    private static final int[] TYPES = {Block.TORCH, Block.REDSTONE_TORCH, Block.SOUL_TORCH,
            Block.COPPER_TORCH, Block.UNDERWATER_TORCH, Block.COLORED_TORCH_RG, Block.COLORED_TORCH_BP};

    @BeforeAll static void bootstrap() { MockServer.init(); }
    @AfterAll static void cleanup() { MockServer.reset(); }

    static Stream<Arguments> padding() { return IntStream.of(TYPES).boxed()
            .flatMap(id -> Stream.of(Arguments.of(id, 6), Arguments.of(id, 7))); }
    static Stream<Arguments> ordinary() { return IntStream.of(TYPES).boxed()
            .flatMap(id -> IntStream.rangeClosed(0, 5).mapToObj(meta -> Arguments.of(id, meta))); }
    static Stream<Arguments> colors() { return IntStream.of(Block.COLORED_TORCH_RG, Block.COLORED_TORCH_BP).boxed()
            .flatMap(id -> IntStream.rangeClosed(8, 15).mapToObj(meta -> Arguments.of(id, meta))); }

    @ParameterizedTest @MethodSource("padding")
    void paddingDropsCarryCanonicalAttachedStateWithoutChangingTheSource(int id, int meta) {
        Level world = mock(Level.class);
        Block source = Block.get(id, meta, world, 21, 64, -9);
        Item item = source.toItem();
        ItemBlock drop = assertInstanceOf(ItemBlock.class, item);
        assertEquals(source.getItemId(), drop.getId());
        assertEquals(0, drop.getDamage());
        assertEquals(1, drop.getCount());
        assertEquals(id, drop.getBlock().getId());
        assertEquals(0, drop.getBlock().getDamage(), "attached block must agree with canonical item metadata");
        assertNull(drop.getBlock().getLevel(), "drop must not retain the obsolete source location");
        assertEquals(meta, source.getDamage());
        assertSame(world, source.getLevel());
        assertEquals(21, source.x);
        Item clone = drop.clone();
        clone.setDamage(3);
        assertEquals(0, drop.getBlock().getDamage());
        assertEquals(meta, source.getDamage());
    }

    @ParameterizedTest @MethodSource("ordinary")
    void ordinaryFacingBehaviorRemainsUnchanged(int id, int meta) {
        Block source = Block.get(id, meta);
        Item item = source.toItem();
        assertEquals(source.getItemId(), item.getId());
        assertEquals(0, item.getDamage());
        assertEquals(meta, item.getBlock().getDamage());
        assertEquals(meta, source.getDamage());
        Item[] drops = source.getDrops(Item.get(Item.AIR));
        assertEquals(1, drops.length);
        assertEquals(item.getId(), drops[0].getId());
        assertEquals(item.getDamage(), drops[0].getDamage());
    }

    @ParameterizedTest @MethodSource("colors")
    void coloredTorchHighBitStateIsOutsideThePaddingChange(int id, int meta) {
        Block source = Block.get(id, meta);
        Item item = source.toItem();
        assertEquals(source.getItemId(), item.getId());
        assertEquals(0, item.getDamage());
        assertEquals(meta, item.getBlock().getDamage());
        assertEquals(meta, source.getDamage());
    }

    @ParameterizedTest @ValueSource(ints = {6, 7})
    void unlitRedstoneOverrideKeepsTheLitCanonicalItem(int meta) {
        Block source = Block.get(Block.UNLIT_REDSTONE_TORCH, meta);
        Item item = source.toItem();
        assertEquals(Block.REDSTONE_TORCH, item.getId());
        assertEquals(0, item.getDamage());
        assertEquals(Block.REDSTONE_TORCH, item.getBlock().getId());
        assertEquals(0, item.getBlock().getDamage());
        assertEquals(meta, source.getDamage());
    }
}
