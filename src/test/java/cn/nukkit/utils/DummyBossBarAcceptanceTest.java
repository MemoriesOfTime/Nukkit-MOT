package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.Attribute;
import cn.nukkit.network.protocol.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DummyBossBarAcceptanceTest {
    @BeforeEach void init() { MockServer.init(); Attribute.init(); }
    @AfterEach void cleanup() { MockServer.reset(); }

    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void equalTextRetriesOnlyRefusedTitle(int protocol) {
        Fixture f=new Fixture(protocol); f.bar.create(); f.clear();
        f.refuse=p->p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_TITLE;
        f.bar.setText("new"); assertEquals(2,f.sent.size()); f.clear(); f.refuse=p->false;
        f.bar.setText("new"); assertEquals(List.of("title"),f.kinds());
        f.clear(); f.bar.setText("new"); assertTrue(f.sent.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void equalLengthRetriesOnlyRefusedAttributes(int protocol) {
        Fixture f=new Fixture(protocol); f.bar.create(); f.clear();
        f.refuse=p->p instanceof UpdateAttributesPacket;
        f.bar.setLength(45); f.clear(); f.refuse=p->false;
        f.bar.setLength(45); assertEquals(List.of("attributes"),f.kinds());
        f.clear(); f.bar.setLength(45); assertTrue(f.sent.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void equalColorRetriesOnlyRefusedTexture(int protocol) {
        Fixture f=new Fixture(protocol); f.bar.create(); f.clear();
        f.refuse=p->p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_TEXTURE;
        f.bar.setColor(BossBarColor.RED); f.clear(); f.refuse=p->false;
        f.bar.setColor(BossBarColor.RED); assertEquals(List.of("texture"),f.kinds());
        f.clear(); f.bar.setColor(BossBarColor.RED); assertTrue(f.sent.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void refusedAddStopsDependentCreationPackets(int protocol) {
        Fixture f=new Fixture(protocol); f.refuse=p->p instanceof AddEntityPacket;
        f.bar.create(); assertEquals(List.of("add"),f.kinds());
    }
    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void teleportShowCannotCarryRejectedTitle(int protocol) {
        Fixture f=new Fixture(protocol); f.bar.create(); f.clear();
        f.refuse=p->p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_TITLE;
        f.bar.setText("rejected"); f.clear(); f.bar.reshow();
        BossEventPacket show=f.sent.stream().filter(p->p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_SHOW)
                .map(p->(BossEventPacket)p).findFirst().orElseThrow();
        assertEquals("initial",show.title);
        assertTrue(f.accepted.stream().noneMatch(p->p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_TITLE));
    }
    @ParameterizedTest @ValueSource(ints={360,589,2193})
    void acceptedCreationRetainsWireFieldsAndQuietEqualSetters(int protocol) {
        Fixture f=new Fixture(protocol); f.bar.create();
        assertEquals(protocol>=361?List.of("add","attributes","show","health","texture"):
                List.of("add","attributes","show","texture"),f.kinds());
        BossEventPacket show=(BossEventPacket)f.sent.stream().filter(p->p instanceof BossEventPacket b && b.type==BossEventPacket.TYPE_SHOW).findFirst().orElseThrow();
        assertEquals("initial",show.title); assertEquals(protocol>=361?.5f:50f,show.healthPercent);
        f.clear(); f.bar.setText("initial"); f.bar.setLength(50); f.bar.setColor(BossBarColor.BLUE); assertTrue(f.sent.isEmpty());
    }
    static class Fixture {
        final Player player=mock(Player.class); final List<DataPacket> sent=new ArrayList<>(), accepted=new ArrayList<>();
        Predicate<DataPacket> refuse=p->false; final DummyBossBar bar;
        Fixture(int protocol) { player.protocol=protocol;
            when(player.dataPacket(any(DataPacket.class))).thenAnswer(a->{DataPacket p=a.getArgument(0);sent.add(p);boolean ok=!refuse.test(p);if(ok)accepted.add(p);return ok;});
            bar=new DummyBossBar.Builder(player).text("initial").length(50).color(BossBarColor.BLUE).build();
        }
        void clear(){sent.clear();accepted.clear();}
        List<String> kinds(){return sent.stream().map(Fixture::kind).toList();}
        static String kind(DataPacket p){
            if(p instanceof AddEntityPacket)return "add"; if(p instanceof UpdateAttributesPacket)return "attributes";
            if(p instanceof SetEntityDataPacket)return "name";if(p instanceof MoveEntityAbsolutePacket)return "move";
            if(p instanceof RemoveEntityPacket)return "remove";
            if(p instanceof BossEventPacket b)return switch(b.type){case BossEventPacket.TYPE_SHOW->"show";case BossEventPacket.TYPE_HIDE->"hide";case BossEventPacket.TYPE_TITLE->"title";case BossEventPacket.TYPE_HEALTH_PERCENT->"health";case BossEventPacket.TYPE_TEXTURE->"texture";default->"boss:"+b.type;};
            return p.getClass().getSimpleName();
        }
    }
}
