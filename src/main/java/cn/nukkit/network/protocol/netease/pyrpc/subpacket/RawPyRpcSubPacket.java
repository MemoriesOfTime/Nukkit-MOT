package cn.nukkit.network.protocol.netease.pyrpc.subpacket;

import cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Fallback sub-packet for PyRpc methods that do not have a registered codec.
 */
public final class RawPyRpcSubPacket implements PyRpcSubPacket {

    private final String method;
    private final List<Object> arguments;
    private final Object rawRoot;
    private final byte[] rawPayload;

    public RawPyRpcSubPacket(String method, List<?> arguments, Object rawRoot, byte[] rawPayload) {
        this.method = Objects.requireNonNull(method, "method");
        this.arguments = arguments != null
                ? Collections.unmodifiableList(new ArrayList<>(arguments))
                : Collections.emptyList();
        this.rawRoot = rawRoot;
        this.rawPayload = rawPayload != null ? rawPayload.clone() : new byte[0];
    }

    @Override
    public String getMethod() {
        return method;
    }

    public List<Object> getArguments() {
        return arguments;
    }

    public Object getRawRoot() {
        return rawRoot;
    }

    public byte[] getRawPayload() {
        return rawPayload.clone();
    }
}
