package cn.nukkit;

import cn.nukkit.event.Event;
import cn.nukkit.event.player.PlayerNetEaseModEventC2SEvent;
import cn.nukkit.event.player.PlayerNetEasePyRpcReceivedEvent;
import cn.nukkit.event.player.PlayerNetEaseStoreBuySuccessEvent;
import cn.nukkit.network.Network;
import cn.nukkit.network.process.DataPacketManager;
import cn.nukkit.network.process.processor.netease.PyRpcProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.netease.PyRpcPacket;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.ModEventPyRpcSubPacket;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.RawPyRpcSubPacket;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.StoreBuySuccessPyRpcSubPacket;
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
    void sendPyRpcSubPacketSendsTypedPyRpcPacket() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        setPlayerGameVersion(player, GameVersion.V1_20_50_NETEASE);
        doReturn(true).when(player).dataPacket(any(DataPacket.class));

        assertTrue(player.sendPyRpc(new RawPyRpcSubPacket(
                "CustomEngineCall",
                List.of("alpha"),
                null,
                null), 0x12345678L));

        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        verify(player).dataPacket(captor.capture());
        PyRpcPacket packet = assertInstanceOf(PyRpcPacket.class, captor.getValue());
        assertEquals(0x12345678L, packet.getMsgId());
        assertTrue(packet.getData().length > 0);
        assertEquals("CustomEngineCall", packet.getSubPacket().getMethod());
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
        PyRpcPacket packet = PyRpcPacket.createSubPacket(new ModEventPyRpcSubPacket(
                ModEventPyRpcSubPacket.CLIENT_TO_SERVER_METHOD,
                "Minecraft",
                "emote",
                "PlayEmoteEvent",
                eventData), PyRpcPacket.DEFAULT_MSG_ID);

        PyRpcProcessor.INSTANCE.handle(new PlayerHandle(player), packet);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(pluginManager, times(2)).callEvent(captor.capture());

        List<Event> events = captor.getAllValues();
        PlayerNetEasePyRpcReceivedEvent firstPyRpcEvent = assertInstanceOf(
                PlayerNetEasePyRpcReceivedEvent.class, events.get(0));
        assertSame(player, firstPyRpcEvent.getPlayer());
        assertEquals(PyRpcPacket.DEFAULT_MSG_ID, firstPyRpcEvent.getMsgId());
        assertEquals(ModEventPyRpcSubPacket.CLIENT_TO_SERVER_METHOD, firstPyRpcEvent.getMethod());

        PlayerNetEaseModEventC2SEvent modEvent = assertInstanceOf(
                PlayerNetEaseModEventC2SEvent.class, events.get(1));
        assertSame(player, modEvent.getPlayer());
        assertEquals(PyRpcPacket.DEFAULT_MSG_ID, modEvent.getMsgId());
        assertSame(packet.getMessage(), modEvent.getMessage());
        assertSame(packet.getSubPacket(), modEvent.getSubPacket());
        assertEquals(ModEventPyRpcSubPacket.CLIENT_TO_SERVER_METHOD, modEvent.getMethod());
        assertEquals("Minecraft", modEvent.getModName());
        assertEquals("emote", modEvent.getSystemName());
        assertEquals("PlayEmoteEvent", modEvent.getCustomEventName());
        assertEquals("wave", modEvent.getEventData().get("animName"));
    }

    @Test
    void pyRpcProcessorCallsGenericEventForCustomSubPacket() throws Exception {
        MockServer.reset();
        Server server = MockServer.get();
        PluginManager pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        Player player = mock(Player.class);
        setPlayerGameVersion(player, GameVersion.V1_20_50_NETEASE);

        RawPyRpcSubPacket customSubPacket = new RawPyRpcSubPacket(
                "CustomEngineCall",
                List.of("alpha", 42),
                null,
                new byte[]{1, 2, 3});
        PyRpcPacket packet = PyRpcPacket.createSubPacket(customSubPacket, 0x12345678L);

        PyRpcProcessor.INSTANCE.handle(new PlayerHandle(player), packet);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(pluginManager).callEvent(captor.capture());

        PlayerNetEasePyRpcReceivedEvent pyRpcEvent = assertInstanceOf(
                PlayerNetEasePyRpcReceivedEvent.class, captor.getValue());
        assertSame(player, pyRpcEvent.getPlayer());
        assertEquals(0x12345678L, pyRpcEvent.getMsgId());
        assertSame(customSubPacket, pyRpcEvent.getSubPacket());
        assertEquals("CustomEngineCall", pyRpcEvent.getMethod());
    }

    @Test
    void pyRpcProcessorCallsStoreBuySuccessEvent() throws Exception {
        MockServer.reset();
        Server server = MockServer.get();
        PluginManager pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        Player player = mock(Player.class);
        setPlayerGameVersion(player, GameVersion.V1_20_50_NETEASE);

        PyRpcPacket packet = PyRpcPacket.createSubPacket(
                new StoreBuySuccessPyRpcSubPacket(),
                PyRpcPacket.DEFAULT_MSG_ID);

        PyRpcProcessor.INSTANCE.handle(new PlayerHandle(player), packet);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(pluginManager, times(2)).callEvent(captor.capture());

        List<Event> events = captor.getAllValues();
        PlayerNetEasePyRpcReceivedEvent pyRpcEvent = assertInstanceOf(
                PlayerNetEasePyRpcReceivedEvent.class, events.get(0));
        assertSame(player, pyRpcEvent.getPlayer());
        assertEquals(StoreBuySuccessPyRpcSubPacket.METHOD, pyRpcEvent.getMethod());

        PlayerNetEaseStoreBuySuccessEvent storeEvent = assertInstanceOf(
                PlayerNetEaseStoreBuySuccessEvent.class, events.get(1));
        assertSame(player, storeEvent.getPlayer());
        assertEquals(PyRpcPacket.DEFAULT_MSG_ID, storeEvent.getMsgId());
        assertSame(packet.getMessage(), storeEvent.getMessage());
        assertSame(packet.getSubPacket(), storeEvent.getSubPacket());
        assertEquals(StoreBuySuccessPyRpcSubPacket.METHOD, storeEvent.getMethod());
    }

    @Test
    void pyRpcProcessorIgnoresNonNetEaseVersion() throws Exception {
        MockServer.reset();
        Server server = MockServer.get();
        PluginManager pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        Player player = mock(Player.class);
        setPlayerGameVersion(player, GameVersion.V1_20_50);

        PyRpcPacket packet = PyRpcPacket.createSubPacket(new StoreBuySuccessPyRpcSubPacket());

        PyRpcProcessor.INSTANCE.handle(new PlayerHandle(player), packet);

        verify(pluginManager, never()).callEvent(any(Event.class));
    }

    @Test
    void pyRpcSubPacketDecodeRejectsOversizedPayload() {
        byte[] eventPayload = msgpackArrayString(StoreBuySuccessPyRpcSubPacket.METHOD);
        byte[] oversizedPayload = new byte[70 * 1024];
        System.arraycopy(eventPayload, 0, oversizedPayload, 0, eventPayload.length);

        assertNull(PyRpcPacket.decodeMessage(oversizedPayload));
    }

    @Test
    void pyRpcSubPacketDecodeRejectsTooDeepPayloadWithoutStackOverflow() {
        byte[] payload = new byte[20_001];
        for (int i = 0; i < payload.length - 1; i++) {
            payload[i] = (byte) 0x91;
        }
        payload[payload.length - 1] = (byte) 0xc0;

        assertDoesNotThrow(() -> assertNull(PyRpcPacket.decodeMessage(payload)));
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

        assertDoesNotThrow(() -> assertNull(PyRpcPacket.decodeMessage(payload)));
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
