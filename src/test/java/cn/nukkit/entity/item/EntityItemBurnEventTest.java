package cn.nukkit.entity.item;

import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.ItemBurnEvent;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class EntityItemBurnEventTest {

    private static EntityItem entity(PluginManager plugins) {
        EntityItem entity = mock(EntityItem.class);
        when(entity.onUpdate(anyInt())).thenCallRealMethod();
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(plugins);
        setServer(entity, server);
        entity.lastUpdate = 10;
        return entity;
    }

    private static void setServer(EntityItem entity, Server server) {
        try {
            Field field = Entity.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(entity, server);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    void aStackInFireIsReportedBeforeItIsRemoved() {
        PluginManager plugins = mock(PluginManager.class);
        EntityItem entity = entity(plugins);
        when(entity.isInsideOfFire()).thenReturn(true);

        assertTrue(entity.onUpdate(11));

        ArgumentCaptor<Event> fired = ArgumentCaptor.forClass(Event.class);
        var order = inOrder(plugins, entity);
        order.verify(plugins).callEvent(fired.capture());
        order.verify(entity).close();
        ItemBurnEvent event = assertInstanceOf(ItemBurnEvent.class, fired.getValue());
        assertSame(entity, event.getEntity());
    }

    @Test
    void aFireproofStackStaysSilent() {
        PluginManager plugins = mock(PluginManager.class);
        EntityItem entity = entity(plugins);
        entity.fireProof = true;
        when(entity.isInsideOfFire()).thenReturn(true);
        when(entity.entityBaseTick(anyInt())).thenReturn(false);
        when(entity.isAlive()).thenReturn(false);

        entity.onUpdate(11);

        verify(plugins, never()).callEvent(any(ItemBurnEvent.class));
    }
}
