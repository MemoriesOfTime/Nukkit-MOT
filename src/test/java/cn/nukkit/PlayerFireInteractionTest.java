package cn.nukkit;

import cn.nukkit.block.*;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.*;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PlayerFireInteractionTest {
    private Player player;
    private Level level;
    private PluginManager plugins;
    private Server server;
    private PluginManager previousPluginManager;
    private boolean previousUseClientSpectator;
    private boolean changedClientSpectator;

    private Block support;
    private Block fire;
    private Block air;
    @BeforeAll static void init() { MockServer.init(); }
    @BeforeEach void setup() throws Exception {
        player = mock(Player.class, CALLS_REAL_METHODS);
        level = mock(Level.class);
        plugins = mock(PluginManager.class);
        server = MockServer.get();
        previousPluginManager = server.getPluginManager();
        previousUseClientSpectator = server.useClientSpectator;
        when(server.getPluginManager()).thenReturn(plugins);
        set(player, "server", server);
        set(player, "lastBreakPosition", new BlockVector3());
        player.level=level;
        player.y=64;
        player.gamemode=1;
        doReturn(false).when(player).isUsingItem();
        doNothing().when(player).setUsingItem(anyBoolean());
        PlayerInventory inventory=mock(PlayerInventory.class);
        set(player, "inventory", inventory);
        when(inventory.getItemInHand()).thenReturn(Item.get(Item.AIR));
        support=mock(Block.class);
        when(support.getId()).thenReturn(Block.STONE);
        support.y=64;
        support.level=level;
        fire=mock(Block.class);
        when(fire.getId()).thenReturn(Block.FIRE);
        fire.y=65;
        fire.level=level;
        when(fire.add(0.5,0.5,0.5)).thenReturn(new cn.nukkit.level.Position(0.5,65.5,0.5,level));
        when(fire.getFloorY()).thenReturn(65);
        when(fire.getLevel()).thenReturn(level);
        air=mock(Block.class);
        when(air.getId()).thenReturn(Block.AIR);
        when(support.getSide(BlockFace.UP)).thenReturn(fire);
        when(fire.getSide(BlockFace.UP)).thenReturn(air);
        when(level.getBlock(any(Vector3.class))).thenAnswer(i -> ((Vector3)i.getArgument(0)).getFloorY()==65 ? fire : support);
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true))).thenReturn(true);
        doReturn(true).when(player).canInteract(any(Vector3.class),anyDouble());
    }
    @AfterEach
    void restoreServerState() {
        if (server == null) {
            return;
        }
        if (server.getPluginManager() == plugins) {
            when(server.getPluginManager()).thenReturn(previousPluginManager);
        }
        if (changedClientSpectator && !server.useClientSpectator) {
            server.useClientSpectator = previousUseClientSpectator;
        }
    }

    @Test void adjacentFireHonorsDenialAtFirePosition() throws Exception {
        doAnswer(i -> {if (i.getArgument(0) instanceof PlayerInteractEvent e && e.getBlock()==fire) e.setCancelled();return null;}).when(plugins).callEvent(any());
        start(support);
        verify(level,never()).setBlock(eq(fire),any(Block.class),eq(true));
    }
    @Test void supportDenialStillPreventsAdjacentFireRemoval() throws Exception {
        doAnswer(i -> {if (i.getArgument(0) instanceof PlayerInteractEvent e && e.getBlock()==support) e.setCancelled();return null;}).when(plugins).callEvent(any());
        start(support);
        verify(level,never()).setBlock(eq(fire),any(Block.class),eq(true));
    }
    @Test void directFireHitExtinguishes() throws Exception {
        start(fire);
        verify(level).setBlock(eq(fire),any(Block.class),eq(true));
        assertFalse(player.isBreakingBlock());
    }
    @Test void acceptedAdjacentFireExtinguishes() throws Exception {
        start(support);
        verify(level).setBlock(eq(fire),any(Block.class),eq(true));
    }
    @Test void extinguishClearsOldBreakState() throws Exception {
        set(player,"breakingBlock",support);
        start(support);
        assertFalse(player.isBreakingBlock());
    }
    @Test void extinguishRequiresActualFireReach() throws Exception {
        doReturn(false).when(player).canInteract(any(Vector3.class),anyDouble());
        start(support);
        verify(level,never()).setBlock(eq(fire),any(Block.class),eq(true));
    }
    @Test void protectionCallbackReplacementSurvives() throws Exception {
        doAnswer(i -> { if (i.getArgument(0) instanceof PlayerInteractEvent e && e.getBlock()==fire) {
            when(level.getBlock(eq(fire))).thenReturn(support);
        } return null; }).when(plugins).callEvent(any());
        start(support);
        verify(level,never()).setBlock(any(Vector3.class),any(Block.class),eq(true));
    }
    @Test void protectionCallbackWorldChangeCannotClearNewWorld() throws Exception {
        Level next=mock(Level.class);
        doAnswer(i -> { if (i.getArgument(0) instanceof PlayerInteractEvent e && e.getBlock()==fire) player.level=next; return null; }).when(plugins).callEvent(any());
        start(support);
        verify(level,never()).setBlock(any(Vector3.class),any(Block.class),eq(true));
        verify(next,never()).setBlock(any(Vector3.class),any(Block.class),eq(true));
    }
    @Test void failedRemovalDoesNotPlayExtinguishSound() throws Exception {
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true))).thenReturn(false);
        start(support);
        verify(level,never()).addLevelSoundEvent(any(Vector3.class),eq(LevelSoundEventPacket.SOUND_EXTINGUISH_FIRE));
    }
    @Test void fakeSpectatorCannotExtinguish() throws Exception {
        player.gamemode=3;
        changedClientSpectator = true;
        server.useClientSpectator = false;
        start(support);
        verify(level,never()).setBlock(any(Vector3.class),any(Block.class),eq(true));
    }
    @Test void directSoulFireHitExtinguishes() throws Exception {
        when(fire.getId()).thenReturn(Block.SOUL_FIRE);
        start(fire);
        verify(level).setBlock(eq(fire),any(Block.class),eq(true));
    }
    @ParameterizedTest @CsvSource({"786,false","786,true","2193,false","2193,true"})
    void bothInputPathsAuthorizeActualFire(int protocol, boolean auth) throws Exception {
        set(player,"connected",true); set(player,"loginVerified",true); set(player,"loggedIn",true);
        set(player,"gameVersion",GameVersion.byProtocol(protocol,false));
        set(player,"temporalVector",new Vector3());
        player.protocol=protocol; player.spawned=true;
        doReturn(true).when(player).isAlive();
        doReturn(auth).when(player).isMovementServerAuthoritative();
        doReturn(auth).when(player).isServerAuthoritativeBlockBreaking();
        doReturn(true).when(player).isLockMovementInput();
        doNothing().when(player).setDataFlag(anyInt(),anyInt(),anyBoolean());
        doAnswer(i -> { if (i.getArgument(0) instanceof PlayerInteractEvent e && e.getBlock()==fire) e.setCancelled(); return null; }).when(plugins).callEvent(any());
        DataPacket packet;
        if(auth) {
            PlayerAuthInputPacket p=new PlayerAuthInputPacket();
            p.setPosition(new Vector3f(0,64,0));
            p.getBlockActionData().put(PlayerActionType.START_DESTROY_BLOCK,new PlayerBlockActionData(PlayerActionType.START_DESTROY_BLOCK,new BlockVector3(0,64,0),1));
            packet=p;
        } else {
            PlayerActionPacket p=new PlayerActionPacket();
            p.action=PlayerActionPacket.ACTION_START_BREAK; p.x=0;p.y=64;p.z=0;p.face=1;
            packet=p;
        }
        packet.protocol=protocol; packet.gameVersion=GameVersion.byProtocol(protocol,false);
        player.handleDataPacket(packet);
        verify(plugins,atLeastOnce()).callEvent(argThat(e -> e instanceof PlayerInteractEvent i && i.getBlock()==fire));
        verify(level,never()).setBlock(eq(fire),any(Block.class),eq(true));
    }
    void start(Block target) throws Exception {
        Method m=Player.class.getDeclaredMethod("onBlockBreakStart",Vector3.class,BlockFace.class);
        m.setAccessible(true);
        m.invoke(player,new Vector3(target.x,target.y,target.z),BlockFace.UP);
    }
    static void set(Object target,String name,Object value) throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()) {
            try {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;} catch(NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
