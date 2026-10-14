package cn.nukkit.level.explosion;

import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Original five confirmed native regressions, independent of the new processor API. */
@Isolated("native Server.instance, item registries and plugin handler lists")
public final class NativeStoredPayloadTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
    @BeforeAll static void bootstrap() throws Exception { NativeExplosionFixture.boot(); }
    @BeforeEach void resetObservation() { NativeExplosionFixture.found=0; }
    @Test void lecternBookExactNbt() throws Exception { NativeExplosionFixture.payload("lectern",Block.LECTERN,Item.WRITTEN_BOOK,1);assertEquals(0,NativeExplosionFixture.found); }
    @Test void jukeboxRecordExactNbt() throws Exception { NativeExplosionFixture.payload("jukebox",Block.JUKEBOX,Item.RECORD_13,1);assertEquals(0,NativeExplosionFixture.found); }
    @Test void decoratedPotContentsExactNbt() throws Exception { NativeExplosionFixture.payload("pot",Block.DECORATED_POT,Item.DIAMOND,64);assertEquals(0,NativeExplosionFixture.found); }
    @Test void chestOwnItemAndInventory() throws Exception { NativeExplosionFixture.chestOwnBlock();assertEquals(0,NativeExplosionFixture.found); }
    @Test void rejectedSourceNeverPublishes() throws Exception { NativeExplosionFixture.rejectedWorldRemoval();assertEquals(0,NativeExplosionFixture.found); }
}
