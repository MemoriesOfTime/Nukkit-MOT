package cn.nukkit.resourcepacks;

import cn.nukkit.Server;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZippedResourcePack extends AbstractResourcePack {

    private File file;
    private byte[] packBytes;
    private byte[] sha256;

    public ZippedResourcePack(File file) {
        this(file, SupportType.UNIVERSAL);
    }

    /**
     * @deprecated Use {@link #ZippedResourcePack(File, SupportType)} instead
     */
    @Deprecated
    public ZippedResourcePack(File file, boolean isNetEase) {
        this(file, isNetEase ? SupportType.NETEASE : SupportType.UNIVERSAL);
    }

    public ZippedResourcePack(File file, SupportType packType) {
        if (!file.exists()) {
            throw new IllegalArgumentException(Server.getInstance().getLanguage()
                    .translateString("nukkit.resources.zip.not-found", file.getName()));
        }

        this.file = file;
        this.setSupportType(packType);

        try {
            this.packBytes = Files.readAllBytes(file.toPath());
            byte[] manifestBytes = findManifest(this.packBytes);
            this.manifest = new JsonParser()
                    .parse(new InputStreamReader(
                            new ByteArrayInputStream(manifestBytes), StandardCharsets.UTF_8))
                    .getAsJsonObject();

            File parentFolder = this.file.getParentFile();
            if (parentFolder == null || !parentFolder.isDirectory()) {
                throw new IOException("Invalid resource pack path");
            }
            // 加密密钥只能来自 packs.yml（由 ResourcePackManager.applyPackConfig 注入）。
            // Encryption keys now come exclusively from packs.yml (injected by ResourcePackManager.applyPackConfig).
        } catch (IOException e) {
            Server.getInstance().getLogger().logException(e);
        }

        if (!this.verifyManifest()) {
            throw new IllegalArgumentException(Server.getInstance().getLanguage()
                    .translateString("nukkit.resources.zip.invalid-manifest"));
        }
    }

    private static byte[] findManifest(byte[] archive) throws IOException {
        byte[] rootPackManifest = null;
        byte[] nestedManifest = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.equals("manifest.json")) {
                    return zip.readAllBytes();
                }
                if (name.equals("pack_manifest.json")) {
                    rootPackManifest = zip.readAllBytes();
                    continue;
                }
                if (nestedManifest != null) {
                    continue;
                }
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (!lowerName.endsWith("manifest.json")
                        && !lowerName.endsWith("pack_manifest.json")) {
                    continue;
                }
                File manifestFile = new File(name);
                if (!manifestFile.getName().equalsIgnoreCase("manifest.json")
                        && !manifestFile.getName().equalsIgnoreCase("pack_manifest.json")) {
                    continue;
                }
                if (manifestFile.getParent() == null
                        || manifestFile.getParentFile().getParent() == null) {
                    nestedManifest = zip.readAllBytes();
                }
            }
        }
        byte[] result = rootPackManifest != null ? rootPackManifest : nestedManifest;
        if (result == null) {
            throw new IllegalArgumentException(Server.getInstance().getLanguage()
                    .translateString("nukkit.resources.zip.no-manifest"));
        }
        return result;
    }

    @Override
    public int getPackSize() {
        return this.packBytes.length;
    }

    @Override
    public byte[] getSha256() {
        if (this.sha256 == null) {
            try {
                this.sha256 = MessageDigest.getInstance("SHA-256").digest(this.packBytes);
            } catch (Exception e) {
                Server.getInstance().getLogger().logException(e);
            }
        }
        return this.sha256;
    }

    @Override
    public byte[] getPackChunk(int off, int len) {
        int size = this.packBytes.length;
        byte[] chunk;
        if (size - off > len) {
            chunk = new byte[len];
        } else {
            chunk = new byte[size - off];
        }
        return Arrays.copyOfRange(this.packBytes, off, off + chunk.length);
    }

    /**
     * @deprecated Use {@link #setCDNUrl(String)} instead
     */
    @Deprecated
    public void setCdnUrl(String cdnUrl) {
        this.setCDNUrl(cdnUrl);
    }
}
