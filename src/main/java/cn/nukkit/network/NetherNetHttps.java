package cn.nukkit.network;

import cn.nukkit.utils.serverconfig.category.NetherNetSettings;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPServerSignaling;
import org.cloudburstmc.netty.util.nethernet.SecretValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 内置信令端口的 HTTPS（TLS）装配：按配置把 PEM 证书链或 PKCS12 密钥库交给信令 builder，
 * 并应用 https-reject-plaintext 的明文策略。未配置证书时端口保持纯明文，与历史行为一致。
 * <p>
 * 证书与私钥路径相对服务器根目录解析（与 identity-file 同基准）；密码支持 file:/path/to/password
 * 改从文件读取。文件不可读或与私钥不匹配时抛出 {@link IllegalArgumentException}，由启动失败路径处理。
 * <p>
 * Wiring for HTTPS on the built-in signaling port: hands a PEM chain or a PKCS12 keystore to the
 * signaling builder per configuration and applies the plaintext policy. Without a certificate the
 * port stays plaintext, as before.
 * <p>
 * Certificate and key paths resolve against the server root (the same base as identity-file); the
 * password accepts file:/path/to/password to be read from a file instead. Unreadable or mismatched
 * files raise {@link IllegalArgumentException} and fail startup through the regular path.
 */
final class NetherNetHttps {

    private NetherNetHttps() {
    }

    /**
     * @return 配置了 TLS 证书时 true；false 表示保持纯明文。
     * True when TLS was configured; false keeps the port plaintext.
     */
    static boolean apply(NetherNetHTTPServerSignaling.Builder builder, NetherNetSettings settings, Path basePath) {
        String certificate = settings.httpsCertificate();
        if (certificate == null || certificate.isBlank()) {
            return false;
        }

        File certificateFile = basePath.resolve(certificate).toFile();
        String password;
        try {
            password = SecretValue.resolve(settings.httpsPassword(), basePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read the NetherNet HTTPS password: " + e.getMessage(), e);
        }

        String privateKey = settings.httpsPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            // PKCS12 密钥库一体承载证书与私钥
            // A PKCS12 keystore carries the certificate and its key together
            builder.setHttpsKeystore(certificateFile, password);
        } else {
            // PEM 形态：证书链叶证书在前；未加密私钥以 null 传递
            // PEM form: the chain is leaf first; an unencrypted key passes as null
            builder.setHttpsPem(certificateFile, basePath.resolve(privateKey).toFile(),
                    password == null || password.isBlank() ? null : password);
        }
        builder.setRequiresTls(settings.httpsRejectPlaintext());
        return true;
    }
}
