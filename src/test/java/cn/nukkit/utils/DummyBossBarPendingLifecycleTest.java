package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Attribute;
import cn.nukkit.network.protocol.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DummyBossBarPendingLifecycleTest {
    @BeforeEach void init(){MockServer.init();Attribute.init();}
    @AfterEach void cleanup(){MockServer.reset();}
    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void refusedShowRetainsItsSnapshotAndCannotReplaceCancelledTitle(int protocol){
        var f=new DummyBossBarAcceptanceTest.Fixture(protocol);
        f.refuse=p->p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_SHOW;
        f.bar.create(); assertTrue(f.bar.hasPendingPackets());f.clear();
        f.refuse=p->p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_TITLE;
        assertFalse(f.bar.updateState("desired",45,BossBarColor.RED));
        var show=(BossEventPacket)f.accepted.stream().filter(p->p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_SHOW).findFirst().orElseThrow();
        assertEquals("initial",show.title); assertEquals(protocol>=361?.5f:50f,show.healthPercent);
        f.clear();assertFalse(f.bar.retryPendingPackets());assertEquals(List.of("title"),f.kinds());
        f.clear();f.refuse=p->false;assertTrue(f.bar.retryPendingPackets());assertEquals(List.of("title"),f.kinds());
        f.clear();assertTrue(f.bar.retryPendingPackets());assertTrue(f.sent.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void hideAndRemoveAreIndependentAndOnlyRefusedKindRetries(int protocol){
        var f=new DummyBossBarAcceptanceTest.Fixture(protocol);f.bar.create();f.clear();
        f.refuse=p->p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_HIDE;
        f.bar.destroy();assertEquals(List.of("hide","remove"),f.kinds());assertTrue(f.bar.hasPendingPackets());
        f.clear();f.refuse=p->false;assertTrue(f.bar.retryPendingPackets());assertEquals(List.of("hide"),f.kinds());
        f.clear();assertTrue(f.bar.retryPendingPackets());assertTrue(f.sent.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void reentrantCreateCannotClearTheNewAddGeneration(int protocol){
        var f=new DummyBossBarAcceptanceTest.Fixture(protocol);var once=new AtomicBoolean();
        doAnswer(a->{DataPacket p=a.getArgument(0);f.sent.add(p);if(p instanceof AddEntityPacket && once.compareAndSet(false,true))f.bar.create();f.accepted.add(p);return true;}).when(f.player).dataPacket(any(DataPacket.class));
        f.bar.create();assertEquals(List.of("add"),f.kinds());assertTrue(f.bar.hasPendingPackets());
        f.clear();assertTrue(f.bar.retryPendingPackets());assertEquals("add",f.kinds().get(0));
        assertEquals(1,f.kinds().stream().filter("show"::equals).count());assertFalse(f.bar.hasPendingPackets());
    }
    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void reentrantDestroyCannotLeaveOldCreationStagesPending(int protocol){
        var f=new DummyBossBarAcceptanceTest.Fixture(protocol);var once=new AtomicBoolean();
        doAnswer(a->{DataPacket p=a.getArgument(0);f.sent.add(p);if(p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_SHOW && once.compareAndSet(false,true))f.bar.destroy();return true;}).when(f.player).dataPacket(any(DataPacket.class));
        f.bar.create();assertTrue(f.bar.hasPendingPackets());f.clear();assertTrue(f.bar.retryPendingPackets());
        assertEquals(List.of("hide","remove"),f.kinds());assertFalse(f.bar.hasPendingPackets());
    }
    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void throwingTitleKeepsOriginalExceptionAndOnlyThatKindPending(int protocol){
        var f=new DummyBossBarAcceptanceTest.Fixture(protocol);f.bar.create();f.clear();var thrown=new IllegalStateException("listener");
        doAnswer(a->{DataPacket p=a.getArgument(0);f.sent.add(p);if(p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_TITLE)throw thrown;return true;}).when(f.player).dataPacket(any(DataPacket.class));
        assertSame(thrown,assertThrows(IllegalStateException.class,()->f.bar.setText("new")));assertTrue(f.bar.hasPendingPackets());f.clear();
        doAnswer(a->{f.sent.add(a.getArgument(0));return true;}).when(f.player).dataPacket(any(DataPacket.class));
        assertTrue(f.bar.retryPendingPackets());assertEquals(List.of("title"),f.kinds());
    }
}
