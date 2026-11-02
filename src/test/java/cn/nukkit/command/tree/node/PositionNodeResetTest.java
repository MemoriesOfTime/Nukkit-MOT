package cn.nukkit.command.tree.node;

import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.tree.ParamList;
import cn.nukkit.command.tree.ParamTree;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PositionNodeResetTest {
    private FloatPositionNode node;
    private ParamList list;
    private final Location origin = new Location(10, 64, 20, 0, 0);

    @BeforeEach
    void setUp() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.getLocation()).thenReturn(origin);
        ParamTree tree = mock(ParamTree.class);
        when(tree.getSender()).thenReturn(sender);
        list = new ParamList(tree);
        node = new FloatPositionNode();
        node.init(list, "position", false, CommandParamType.POSITION, null, null);
        list.add(node);
    }

    @Test
    void rejectsFourCoordinatesWithoutThrowing() {
        assertDoesNotThrow(() -> fill("~1~2~3~4"));
        assertNotEquals(Integer.MIN_VALUE, list.getError());
        assertFalse(node.hasResult());
    }

    @Test
    void resetAfterTwoCoordinatesStartsAtTheFirstAxis() {
        fill("1", "2");
        assertFalse(node.hasResult());
        list.reset();
        fill("3");
        assertFalse(node.hasResult(), "a prior command must not supply missing axes");
        fill("4", "5");
        assertPosition(3, 4, 5);
    }

    @Test
    void resetAfterMalformedCoordinateClearsPartialRelativeState() {
        fill("~1", "invalid");
        assertNotEquals(Integer.MIN_VALUE, list.getError());
        list.reset();
        fill("~2", "~3", "~4");
        assertEquals(Integer.MIN_VALUE, list.getError());
        assertPosition(12, 67, 24);
    }

    @Test
    void validAbsoluteCoordinatesRemainUnchanged() {
        fill("-1.5", "64", "+2");
        assertPosition(-1.5, 64, 2);
    }

    @Test
    void validCompactRelativeCoordinatesRemainUnchanged() {
        fill("~~~");
        assertPosition(10, 64, 20);
    }

    @Test
    void validLocalCoordinatesRemainUnchangedAfterReset() {
        fill("1");
        list.reset();
        fill("^", "^", "^");
        assertPosition(10, 64, 20);
    }

    private void fill(String... args) {
        for (String arg : args) {
            list.getIndexAndIncrement();
            node.fill(arg);
        }
    }

    private void assertPosition(double x, double y, double z) {
        assertTrue(node.hasResult());
        assertEquals(Integer.MIN_VALUE, list.getError());
        Position position = node.get(origin);
        assertEquals(x, position.x, 1.0e-8);
        assertEquals(y, position.y, 1.0e-8);
        assertEquals(z, position.z, 1.0e-8);
    }
}
