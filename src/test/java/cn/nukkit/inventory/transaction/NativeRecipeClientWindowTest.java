package cn.nukkit.inventory.transaction;
import cn.nukkit.GameVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NativeRecipeClientWindowTest {
 @BeforeAll static void init(){CraftAnvilAtomicNativeAuditTest.init();}
 @ParameterizedTest @EnumSource(value=GameVersion.class,mode=EnumSource.Mode.MATCH_ANY,names={"V1_(20|21|26)_[0-9]+"})
 void nativeCraftAndRenameKeepSessionVersion(GameVersion game){
  var f=new CraftAnvilAtomicNativeAuditTest.Fixture();f.player.protocol=game.getProtocol();when(f.player.getGameVersion()).thenReturn(game);
  assertTrue(f.planks().execute());assertEquals(4,f.main.getItem(0).getCount());
  var g=new CraftAnvilAtomicNativeAuditTest.Fixture();g.player.protocol=game.getProtocol();when(g.player.getGameVersion()).thenReturn(game);
  assertTrue(g.rename().execute());assertEquals("Renamed",g.main.getItem(0).getCustomName());
 }
}
