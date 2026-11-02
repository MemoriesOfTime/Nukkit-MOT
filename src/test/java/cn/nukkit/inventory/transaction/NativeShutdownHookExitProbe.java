package cn.nukkit.inventory.transaction;

import cn.nukkit.Server;
import java.nio.file.*;
import static org.mockito.Mockito.*;

/** Launched only in a finite child JVM so Runtime.exit and real shutdown hooks can be characterized. */
public final class NativeShutdownHookExitProbe {
    public static void main(String[] arguments) throws Exception {
        NativeWorkstationOverflowTest.initialize();
        var fixture = NativePlayerProfileLifecycleTest.fixture();
        var store = new NativePlayerProfileLifecycleTest.ProfileStore(fixture);
        fixture.station.setItemForce(0, cn.nukkit.item.Item.AIR_ITEM);
        Server server = NativeShutdownRequestTest.configureTeardown(fixture);
        if (arguments[0].equals("hook-failure") || arguments[0].equals("terminated-hang") || arguments[0].equals("terminated-healthy") || arguments[0].equals("stock-writer-hang")) {
            Thread owner = new Thread(() -> { }); owner.start(); owner.join();
            NativeShutdownRequestTest.inject(server, "currentThread", owner);
            if (arguments[0].equals("hook-failure")) fixture.events(event -> {
                if (event instanceof cn.nukkit.event.server.ServerStopEvent) throw new IllegalStateException("native hook extension failure");
            });
            if (arguments[0].equals("terminated-hang")) fixture.events(event -> {
                if (event instanceof cn.nukkit.event.server.ServerStopEvent) new java.util.concurrent.CountDownLatch(1)
                        .await(45, java.util.concurrent.TimeUnit.SECONDS);
            });
            if (arguments[0].equals("terminated-healthy")) doAnswer(call -> {
                if (!fixture.player.closed || store.writes != 1) throw new AssertionError("dependencies retired before final profile");
                return null;
            }).when(server).disablePlugins();
        }
        if (arguments[0].equals("stock-writer-hang")) {
            Path directory=Path.of(arguments[1]).getParent().resolve("stock-data");
            Files.createDirectories(directory.resolve("players"));
            var serializer=new cn.nukkit.utils.DefaultPlayerDataSerializer(directory.toString());
            try(var output=serializer.write("nativecheckpoint",fixture.player.getUniqueId())) {
                cn.nukkit.nbt.NBTIO.writeGZIPCompressed(new cn.nukkit.nbt.tag.CompoundTag().putString("marker","old-complete"),output);
            }
            NativeShutdownRequestTest.inject(server,"playerDataSerializer",serializer);
            fixture.player.namedTag=new DelayedProfile(Path.of(arguments[1]).getParent().resolve("stock-writer-entered.txt"));
        }
        when(server.getPropertyString(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        doCallRealMethod().when(server).forceShutdown();
        var method = Server.class.getDeclaredMethod("runShutdownHook"); method.setAccessible(true);
        doCallRealMethod().when(server); method.invoke(server);
        var await = Server.class.getDeclaredMethod("awaitShutdownCompletion", long.class); await.setAccessible(true);
        doCallRealMethod().when(server); await.invoke(server, anyLong());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                method.invoke(server);
                if (!arguments[0].equals("held-start")) Files.writeString(Path.of(arguments[1]),
                        arguments[0].equals("terminated-hang") && store.writes == 0
                                ? "hook returned without a new profile" : "hook returned");
            } catch (Throwable failure) { failure.printStackTrace(); }
        }, "native actual shutdown hook"));
        if (arguments[0].equals("held-start")) {
            var bans = mock(cn.nukkit.permission.BanList.class);
            when(bans.getEntires()).thenReturn(new java.util.LinkedHashMap<>());
            NativeShutdownRequestTest.inject(server, "banByIP", bans);
            doCallRealMethod().when(server).start();
            fixture.events(event -> {
                if (event instanceof cn.nukkit.event.server.ServerStopEvent) {
                    var claim = cn.nukkit.inventory.InventoryMutationGuard.tryClaim(
                            java.util.List.of(fixture.player.getInventory(), fixture.player.getUIInventory()));
                    if (claim == null) throw new AssertionError("native stop claim");
                    Thread release = new Thread(() -> {
                        try { Thread.sleep(100); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                        finally { claim.close(); }
                    });
                    release.setDaemon(true); release.start();
                }
            });
            server.start();
            Files.writeString(Path.of(arguments[1]), store.writes == 1 && fixture.player.closed
                    ? "profile saved before owner exit" : "owner returned before the admitted mutation finished");
        }
        System.exit(0);
    }

    /** An immutable native CompoundTag extension delays completion after writing its actual payload. */
    static final class DelayedProfile extends cn.nukkit.nbt.tag.CompoundTag {
        private final Path marker;
        DelayedProfile(Path marker){this.marker=marker;}
        @Override public cn.nukkit.nbt.tag.CompoundTag copy() {
            var copy=new DelayedProfile(marker);
            for(var tag:this.getAllTags())copy.put(tag.getName(),tag.copy());
            return copy;
        }
        @Override public void write(cn.nukkit.nbt.stream.NBTOutputStream output) throws java.io.IOException {
            super.write(output);Files.writeString(marker,"native writer entered");
            try {new java.util.concurrent.CountDownLatch(1).await(45,java.util.concurrent.TimeUnit.SECONDS);}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.IOException(interrupted);}
        }
    }
}
