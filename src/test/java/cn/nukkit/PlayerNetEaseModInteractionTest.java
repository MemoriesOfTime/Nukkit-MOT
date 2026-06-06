package cn.nukkit;

import cn.nukkit.event.Event;
import cn.nukkit.event.player.PlayerNetEaseModEventC2SEvent;
import cn.nukkit.event.player.PlayerNetEaseStoreBuySuccessEvent;
import cn.nukkit.network.Network;
import cn.nukkit.network.process.DataPacketManager;
import cn.nukkit.network.process.processor.netease.PyRpcProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.netease.PyRpcPacket;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerNetEaseModInteractionTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void modNotifyToClientSendsPyRpcPacket() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        setPlayerGameVersion(player, GameVersion.V1_20_50_NETEASE);
        doReturn(true).when(player).dataPacket(any(DataPacket.class));

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("animName", "wave");

        assertTrue(player.modNotifyToClient("Minecraft", "emote", "PlayEmoteEvent", eventData));

        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        verify(player).dataPacket(captor.capture());
        PyRpcPacket packet = assertInstanceOf(PyRpcPacket.class, captor.getValue());
        assertEquals(PyRpcPacket.DEFAULT_MSG_ID, packet.getMsgId());
        assertTrue(packet.getData().length > 0);
    }

    @Test
    void modNotifyToClientEncryptedSendsPyRpcPacket() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        setPlayerGameVersion(player, GameVersion.V1_20_50_NETEASE);
        doReturn(true).when(player).dataPacket(any(DataPacket.class));

        assertTrue(player.modNotifyToClientEncrypted(
                "Minecraft", "secure", "EncryptedEvent", "payload", value -> "enc:" + value));

        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        verify(player).dataPacket(captor.capture());
        PyRpcPacket packet = assertInstanceOf(PyRpcPacket.class, captor.getValue());
        assertEquals(PyRpcPacket.DEFAULT_MSG_ID, packet.getMsgId());
        assertTrue(packet.getData().length > 0);
    }

    @Test
    void modNotifyToClientDoesNotSendPyRpcPacketToInternationalPlayer() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        setPlayerGameVersion(player, GameVersion.V1_20_50);

        assertFalse(player.modNotifyToClient("Minecraft", "emote", "PlayEmoteEvent", Map.of()));

        verify(player, never()).dataPacket(any(DataPacket.class));
    }

    @Test
    void sendPyRpcDataDoesNotSendPacketToInternationalPlayer() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        setPlayerGameVersion(player, GameVersion.V1_20_50);

        assertFalse(player.sendPyRpcData(new byte[]{1, 2, 3}));

        verify(player, never()).dataPacket(any(DataPacket.class));
    }

    @Test
    void pyRpcPacketIsHandledByDataPacketProcessor() {
        new Network(MockServer.get());

        assertTrue(DataPacketManager.canProcess(630, PyRpcPacket.class));
    }

    @Test
    void pyRpcProcessorCallsPluginEvents() throws Exception {
        MockServer.reset();
        Server server = MockServer.get();
        PluginManager pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        Player player = mock(Player.class);
        setPlayerGameVersion(player, GameVersion.V1_20_50_NETEASE);

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("animName", "wave");
        PyRpcPacket packet = new PyRpcPacket();
        packet.subPackets = List.of(
                new PyRpcPacket.ModEventSubPacket("Minecraft", "emote", "PlayEmoteEvent", eventData),
                new PyRpcPacket.StoreBuySuccessSubPacket());

        PyRpcProcessor.INSTANCE.handle(new PlayerHandle(player), packet);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(pluginManager, times(2)).callEvent(captor.capture());

        List<Event> events = captor.getAllValues();
        PlayerNetEaseModEventC2SEvent modEvent = assertInstanceOf(
                PlayerNetEaseModEventC2SEvent.class, events.get(0));
        assertSame(player, modEvent.getPlayer());
        assertEquals("Minecraft", modEvent.getModName());
        assertEquals("emote", modEvent.getSystemName());
        assertEquals("PlayEmoteEvent", modEvent.getCustomEventName());
        assertEquals("wave", modEvent.getEventData().get("animName"));

        PlayerNetEaseStoreBuySuccessEvent storeEvent = assertInstanceOf(
                PlayerNetEaseStoreBuySuccessEvent.class, events.get(1));
        assertSame(player, storeEvent.getPlayer());
        assertFalse(storeEvent.isCancelled());
        storeEvent.setCancelled();
        assertTrue(storeEvent.isCancelled());
    }

    @Test
    void pyRpcProcessorIgnoresNonNetEaseVersion() throws Exception {
        MockServer.reset();
        Server server = MockServer.get();
        PluginManager pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        Player player = mock(Player.class);
        setPlayerGameVersion(player, GameVersion.V1_20_50);

        PyRpcPacket packet = new PyRpcPacket();
        packet.subPackets = List.of(new PyRpcPacket.StoreBuySuccessSubPacket());

        PyRpcProcessor.INSTANCE.handle(new PlayerHandle(player), packet);

        verify(pluginManager, never()).callEvent(any(Event.class));
    }

    @Test
    void pyRpcSubPacketDecodeRejectsOversizedPayload() {
        byte[] eventPayload = msgpackArrayString(PyRpcPacket.STORE_BUY_SUCCESS_EVENT);
        byte[] oversizedPayload = new byte[70 * 1024];
        System.arraycopy(eventPayload, 0, oversizedPayload, 0, eventPayload.length);

        assertTrue(PyRpcPacket.decodeSubPackets(oversizedPayload).isEmpty());
    }

    @Test
    void pyRpcSubPacketDecodeRejectsTooDeepPayloadWithoutStackOverflow() {
        byte[] payload = new byte[20_001];
        for (int i = 0; i < payload.length - 1; i++) {
            payload[i] = (byte) 0x91;
        }
        payload[payload.length - 1] = (byte) 0xc0;

        assertDoesNotThrow(() -> assertTrue(PyRpcPacket.decodeSubPackets(payload).isEmpty()));
    }

    @Test
    void pyRpcSubPacketDecodeRejectsHugeDeclaredBinaryLength() {
        byte[] payload = new byte[]{
                (byte) 0xc6,
                0x7f,
                (byte) 0xff,
                (byte) 0xff,
                (byte) 0xff
        };

        assertDoesNotThrow(() -> assertTrue(PyRpcPacket.decodeSubPackets(payload).isEmpty()));
    }

    private static void setPlayerGameVersion(Player player, GameVersion gameVersion) throws Exception {
        Field field = Player.class.getDeclaredField("gameVersion");
        field.setAccessible(true);
        field.set(player, gameVersion);
        player.protocol = gameVersion.getProtocol();
    }

    private static byte[] msgpackArrayString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x91);
        out.write(0xa0 | bytes.length);
        out.writeBytes(bytes);
        return out.toByteArray();
    }
}
