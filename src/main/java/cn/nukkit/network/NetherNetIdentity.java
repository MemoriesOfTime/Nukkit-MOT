package cn.nukkit.network;

import cn.nukkit.Server;
import cn.nukkit.utils.TextFormat;
import cn.nukkit.utils.serverconfig.category.NetherNetSettings;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.util.nethernet.ServerIdentity;

import java.io.File;
import java.nio.file.Path;

/**
 * 服务器对外呈现的 NetherNet 运营者身份。
 * <p>
 * 客户端会固定信任其公钥，PEM 只生成一次后长期保留：更换会使所有回归玩家重新弹信任提示；
 * 服务器组共享同一文件可呈现为同一运营者。
 * <p>
 * The operator identity clients pin; generate once and keep it, replacing it re-prompts every
 * returning player. Share one file across a fleet to present as a single operator.
 */
@Log4j2
final class NetherNetIdentity {

    private NetherNetIdentity() {
    }

    static ServerIdentity load(Server server, NetherNetSettings settings) {
        File pem = Path.of(server.getDataPath()).resolve(settings.identityFile()).toFile();
        File parent = pem.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create the NetherNet identity directory " + parent);
        }

        try {
            boolean existed = pem.isFile();
            ServerIdentity identity = ServerIdentity.fromPemOrCreate(pem, domain(server, settings));
            if (!existed) {
                log.info("Generated a NetherNet identity at {}. Keep it: replacing it re-prompts every returning player", pem);
            }
            return identity;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load or create the NetherNet identity at " + pem, e);
        }
    }

    /**
     * 客户端信任提示中显示的运营者名称，仅用于展示，可变更而无需换钥匙。
     * Operator name shown in the client trust prompt; display text only.
     */
    private static String domain(Server server, NetherNetSettings settings) {
        String configured = settings.identityDomain();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return TextFormat.clean(server.getMotd());
    }
}
