package cn.nukkit.network;

import io.netty.channel.EventLoop;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetServerSignaling;
import org.cloudburstmc.netty.util.nethernet.ServerIdentity;

import java.net.ConnectException;
import java.net.SocketAddress;
import java.util.List;

/**
 * 纯委托的信令装饰器基类：子类只覆盖需要拦截的出口（应答改写、offer 改写等），其余全部透传。
 * Pure-delegation base for signaling decorators: subclasses override only the exits they
 * intercept (answer rewriting, offer rewriting), everything else passes straight through.
 */
abstract class NetherNetDelegatingSignaling implements NetherNetServerSignaling {

    protected final NetherNetServerSignaling delegate;

    protected NetherNetDelegatingSignaling(NetherNetServerSignaling delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sendFullSdp(String targetNetworkId, String sdp) {
        this.delegate.sendFullSdp(targetNetworkId, sdp);
    }

    @Override
    public void bind(SocketAddress localAddress, EventLoop eventLoop) throws ConnectException {
        this.delegate.bind(localAddress, eventLoop);
    }

    @Override
    public void setNewConnectionHandler(NewConnectionHandler handler) {
        this.delegate.setNewConnectionHandler(handler);
    }

    @Override
    public void setAdvertisementData(PongData pongData) {
        this.delegate.setAdvertisementData(pongData);
    }

    @Override
    public List<IceServerInfo> getIceServers() {
        return this.delegate.getIceServers();
    }

    @Override
    public ServerIdentity serverIdentity() {
        return this.delegate.serverIdentity();
    }

    @Override
    public boolean allowsIceOnLocalPort() {
        return this.delegate.allowsIceOnLocalPort();
    }

    @Override
    public boolean usesTrickleIce() {
        return this.delegate.usesTrickleIce();
    }

    @Override
    public void sendSignal(String targetNetworkId, String data) {
        this.delegate.sendSignal(targetNetworkId, data);
    }

    @Override
    public void setSignalHandler(long connectionId, SignalHandler handler) {
        this.delegate.setSignalHandler(connectionId, handler);
    }

    @Override
    public void removeSignalHandler(long connectionId) {
        this.delegate.removeSignalHandler(connectionId);
    }

    @Override
    public String getLocalNetworkId() {
        return this.delegate.getLocalNetworkId();
    }

    @Override
    public boolean isActive() {
        return this.delegate.isActive();
    }

    @Override
    public void close() {
        this.delegate.close();
    }
}
