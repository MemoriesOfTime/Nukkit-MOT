package cn.nukkit.form.window;

import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementInput;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FormWindowReuseTest {
    static Stream<Integer> protocols() {
        return ProtocolInfo.SUPPORTED_PROTOCOLS.stream()
                .filter(protocol -> protocol >= ProtocolInfo.v1_20_0
                        && protocol <= ProtocolInfo.v1_26_50);
    }

    private static FormWindowSimple simple() {
        return new FormWindowSimple("Menu", "Choose", List.of(new ElementButton("One")));
    }

    private static FormWindowCustom custom() {
        return new FormWindowCustom("Input", List.of(new ElementInput("Name")));
    }

    private static void assertResponseThenClose(FormWindow form, int protocol, String answer) {
        form.setResponse(protocol, answer);
        assertNotNull(form.getResponse());
        form.setResponse(protocol, "null");
        assertNull(form.getResponse(), "Closing must not retain a previous action");
        assertTrue(form.wasClosed());
    }

    private static void assertCloseThenResponse(FormWindow form, int protocol, String answer) {
        form.setResponse(protocol, "null");
        assertTrue(form.wasClosed());
        form.setResponse(protocol, answer);
        assertFalse(form.wasClosed(), "A later answer must not inherit closed state");
        assertNotNull(form.getResponse());
    }

    @ParameterizedTest @MethodSource("protocols")
    void simpleResponseThenClose(int protocol) {
        assertResponseThenClose(simple(), protocol, "0");
    }

    @ParameterizedTest @MethodSource("protocols")
    void simpleCloseThenResponse(int protocol) {
        assertCloseThenResponse(simple(), protocol, "0");
    }

    @ParameterizedTest @MethodSource("protocols")
    void modalResponseThenClose(int protocol) {
        assertResponseThenClose(new FormWindowModal("Confirm", "Continue", "Yes", "No"), protocol, "true");
    }

    @ParameterizedTest @MethodSource("protocols")
    void modalCloseThenResponse(int protocol) {
        assertCloseThenResponse(new FormWindowModal("Confirm", "Continue", "Yes", "No"), protocol, "false");
    }

    @ParameterizedTest @MethodSource("protocols")
    void customResponseThenClose(int protocol) {
        assertResponseThenClose(custom(), protocol, "[\"first\"]");
    }

    @ParameterizedTest @MethodSource("protocols")
    void customCloseThenResponse(int protocol) {
        assertCloseThenResponse(custom(), protocol, "[\"second\"]");
    }

    @ParameterizedTest @MethodSource("protocols")
    void malformedSimpleAnswerCannotReplayPreviousChoice(int protocol) {
        FormWindowSimple form = simple();
        form.setResponse(protocol, "0");
        form.setResponse(protocol, "not-an-index");
        assertNull(form.getResponse());
        assertFalse(form.wasClosed());
    }

    @ParameterizedTest @MethodSource("protocols")
    void negativeSimpleIndexCannotThrowOrReplay(int protocol) {
        FormWindowSimple form = simple();
        for (String answer : List.of("-1", "-2", "-2147483648")) {
            form.setResponse(protocol, "0");
            assertDoesNotThrow(() -> form.setResponse(protocol, answer));
            assertNull(form.getResponse());
            assertFalse(form.wasClosed());
        }
    }

    @ParameterizedTest @MethodSource("protocols")
    void customParserFailureClearsPreviousChoice(int protocol) {
        FormWindowCustom form = custom();
        form.setResponse(protocol, "[\"first\"]");
        assertThrows(RuntimeException.class, () -> form.setResponse(protocol, "{"));
        assertNull(form.getResponse());
    }

    @ParameterizedTest @MethodSource("protocols")
    void positiveOutOfRangeKeepsExistingSentinelContract(int protocol) {
        FormWindowSimple form = simple();
        form.setResponse(protocol, "5");
        assertNotNull(form.getResponse());
        assertEquals(5, form.getResponse().getClickedButtonId());
        assertNull(form.getResponse().getClickedButton());
    }
}
