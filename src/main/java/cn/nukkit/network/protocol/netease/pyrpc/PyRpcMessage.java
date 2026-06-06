package cn.nukkit.network.protocol.netease.pyrpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decoded method/argument envelope for a NetEase PyRpc payload.
 */
public final class PyRpcMessage {

    private final String method;
    private final List<Object> arguments;
    private final Object rawRoot;
    private final byte[] rawPayload;
    private final PyRpcSubPacket subPacket;

    public PyRpcMessage(String method, List<?> arguments, Object rawRoot, byte[] rawPayload,
                        PyRpcSubPacket subPacket) {
        this.method = method;
        this.arguments = arguments != null
                ? Collections.unmodifiableList(new ArrayList<>(arguments))
                : Collections.emptyList();
        this.rawRoot = rawRoot;
        this.rawPayload = rawPayload != null ? rawPayload.clone() : new byte[0];
        this.subPacket = subPacket;
    }

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

    public PyRpcSubPacket getSubPacket() {
        return subPacket;
    }

    public PyRpcMessage withSubPacket(PyRpcSubPacket subPacket) {
        return new PyRpcMessage(this.method, this.arguments, this.rawRoot, this.rawPayload, subPacket);
    }
}
