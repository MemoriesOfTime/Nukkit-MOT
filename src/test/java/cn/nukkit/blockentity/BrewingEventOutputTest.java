package cn.nukkit.blockentity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.inventory.BrewEvent;
import cn.nukkit.inventory.BrewingRecipe;
import cn.nukkit.inventory.CraftingManager;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class BrewingEventOutputTest {

    @BeforeAll
    static void init() {
        Block.init();
    }

    @Test
    void eventAuthoredResultReachesThePotionSlotSynchronously() throws Exception {
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        CraftingManager recipes = mock(CraftingManager.class);
        Level level = mock(Level.class);
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);

        lenient().when(server.getPluginManager()).thenReturn(plugins);
        lenient().when(server.getCraftingManager()).thenReturn(recipes);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().when(provider.getLevel()).thenReturn(level);
        lenient().when(level.getServer()).thenReturn(server);
        lenient().when(level.getBlock(any(Vector3.class)))
                .thenReturn(Block.get(Block.BREWING_STAND_BLOCK));

        Field instance = Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previousServer = instance.get(null);
        instance.set(null, server);
        try {
            BlockEntityBrewingStand stand = new BlockEntityBrewingStand(chunk, standNbt());
            Item ingredient = Item.get(Item.NETHER_WART, 0, 1);
            Item potion = Item.get(Item.POTION, 0, 1);
            Item brewed = Item.get(Item.SPLASH_POTION, 0, 1);
            BrewingRecipe recipe = new BrewingRecipe(potion, ingredient, brewed);
            lenient().when(recipes.matchBrewingRecipe(any(Item.class), any(Item.class)))
                    .thenReturn(recipe);

            stand.getInventory().setIngredient(ingredient);
            stand.getInventory().setItem(1, potion);
            stand.fuelAmount = 1;
            stand.brewTime = 1;

            Mockito.doAnswer(
                            invocation -> {
                                Object called = invocation.getArgument(0);
                                if (called instanceof BrewEvent event) {
                                    assertEquals(Item.POTION, event.getPotion(0).getId());
                                    assertEquals(Item.AIR, event.getPotion(2).getId());
                                    Item authored = event.getResult(0).clone();
                                    authored.setNamedTag(
                                            authored.getOrCreateNamedTag()
                                                    .putString("km_origin", "kit:brew"));
                                    event.setResult(0, authored);
                                }
                                return null;
                            })
                    .when(plugins)
                    .callEvent(any());

            assertTrue(stand.onUpdate());
            Item delivered = stand.getInventory().getItem(1);
            assertEquals(Item.SPLASH_POTION, delivered.getId());
            assertEquals("kit:brew", delivered.getNamedTag().getString("km_origin"));
        } finally {
            instance.set(null, previousServer);
        }
    }

    private static CompoundTag standNbt() {
        return new CompoundTag()
                .putString("id", BlockEntity.BREWING_STAND)
                .putInt("x", 0)
                .putInt("y", 64)
                .putInt("z", 0)
                .putList(new ListTag<CompoundTag>("Items"))
                .putShort("CookTime", BlockEntityBrewingStand.MAX_BREW_TIME)
                .putShort("FuelAmount", 0)
                .putShort("FuelTotal", 0);
    }
}
