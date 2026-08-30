package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.entity.mob.EntityWolf;
import cn.nukkit.entity.passive.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 校验 {@code isBreedingItem}/{@code isTamingItem} 与 onInteract 实际繁殖/驯服行为一致。
 * <p>
 * Verifies that {@code isBreedingItem}/{@code isTamingItem} match what onInteract actually
 * breeds or tames with — in particular the lure-vs-breed divergence (pig's carrot on a
 * stick, strider/cat/ocelot which never breed).
 */
public class EntityBreedingItemTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    public void pigBreedingExcludesCarrotOnAStick() {
        EntityPig pig = new EntityPig(newMockChunk(), baseNbt());

        assertTrue(pig.isBreedingItem(Item.get(Item.CARROT)));
        assertTrue(pig.isBreedingItem(Item.get(Item.POTATO)));
        assertTrue(pig.isBreedingItem(Item.get(Item.BEETROOT)));
        assertFalse(pig.isBreedingItem(Item.get(Item.CARROT_ON_A_STICK)),
                "carrot on a stick only steers a saddled pig, it must not answer as breeding");
        assertTrue(pig.isFeedItem(Item.get(Item.CARROT_ON_A_STICK)),
                "the lure list keeps the stick");
    }

    @Test
    public void animalsWithoutBreedingAnswerFalse() {
        assertFalse(new EntityStrider(newMockChunk(), baseNbt())
                .isBreedingItem(Item.get(Item.WARPED_FUNGUS_ON_A_STICK)));
        assertFalse(new EntityCat(newMockChunk(), baseNbt())
                .isBreedingItem(Item.get(Item.RAW_FISH)));
        assertFalse(new EntityOcelot(newMockChunk(), baseNbt())
                .isBreedingItem(Item.get(Item.RAW_FISH)));
    }

    @Test
    public void breedingAnimalsAnswerTheirFood() {
        assertTrue(new EntityChicken(newMockChunk(), baseNbt()).isBreedingItem(Item.get(Item.SEEDS)));
        assertTrue(new EntityCow(newMockChunk(), baseNbt()).isBreedingItem(Item.get(Item.WHEAT)));
        assertTrue(new EntitySheep(newMockChunk(), baseNbt()).isBreedingItem(Item.get(Item.WHEAT)));
        assertTrue(new EntityMooshroom(newMockChunk(), baseNbt()).isBreedingItem(Item.get(Item.WHEAT)));
        assertTrue(new EntityFox(newMockChunk(), baseNbt()).isBreedingItem(Item.get(Item.SWEET_BERRIES)));
        assertTrue(new EntityHorse(newMockChunk(), baseNbt()).isBreedingItem(Item.get(Item.GOLDEN_CARROT)));
        assertTrue(new EntityRabbit(newMockChunk(), baseNbt()).isBreedingItem(Item.get(Item.DANDELION)));
        assertTrue(new EntityDolphin(newMockChunk(), baseNbt()).isBreedingItem(Item.get(Item.RAW_FISH)));
    }

    @Test
    public void unrelatedItemsAnswerFalse() {
        assertFalse(new EntityCow(newMockChunk(), baseNbt()).isBreedingItem(Item.get(Item.SEEDS)));
        assertFalse(new EntityChicken(newMockChunk(), baseNbt()).isBreedingItem(Item.get(Item.WHEAT)));
    }

    @Test
    public void wolfAnswersTamingAndBreedingItems() {
        EntityWolf wolf = new EntityWolf(newMockChunk(), baseNbt());

        assertTrue(wolf.isTamingItem(Item.get(Item.BONE)));
        assertFalse(wolf.isTamingItem(Item.get(Item.RAW_BEEF)));
        assertTrue(wolf.isBreedingItem(Item.get(Item.RAW_BEEF)));
        assertFalse(new EntityOcelot(newMockChunk(), baseNbt()).isTamingItem(Item.get(Item.RAW_FISH)),
                "ocelots have no taming implementation, so no item tames them");
    }

    private static FullChunk newMockChunk() {
        Level level = mock(Level.class);
        lenient().when(level.getServer()).thenReturn(MockServer.get());
        lenient().when(level.getMinBlockY()).thenReturn(-64);
        lenient().when(level.getMaxBlockY()).thenReturn(319);
        lenient().when(level.getGameRules()).thenReturn(GameRules.getDefault());
        lenient().when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        lenient().when(level.getNearbyEntities(any(), any())).thenReturn(new Entity[0]);
        lenient().when(level.getNearbyEntities(any(), any(), anyBoolean(), anyBoolean())).thenReturn(new Entity[0]);
        lenient().when(level.getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt())).thenReturn(0);
        lenient().when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        try {
            Field f = Level.class.getDeclaredField("isBeingConverted");
            f.setAccessible(true);
            f.setBoolean(level, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
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
