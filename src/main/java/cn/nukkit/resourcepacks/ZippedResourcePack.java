package cn.nukkit.resourcepacks;

import cn.nukkit.Server;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZippedResourcePack extends AbstractResourcePack {

    private File file;
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

        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                entry = zip.getEntry("pack_manifest.json");
            }
            if (entry == null) {
                entry = zip.stream()
                        .filter(e-> !e.isDirectory() &&
                                (e.getName().toLowerCase(Locale.ROOT).endsWith("manifest.json") || e.getName().toLowerCase(Locale.ROOT).endsWith("pack_manifest.json")))
                        .filter(e-> {
                            File fe = new File(e.getName());
                            if (!fe.getName().equalsIgnoreCase("manifest.json") && !fe.getName().equalsIgnoreCase("pack_manifest.json")) {
                                return false;
                            }
                            return fe.getParent() == null || fe.getParentFile().getParent() == null;
                        })
                        .findFirst()
                        .orElseThrow(()-> new IllegalArgumentException(
                                Server.getInstance().getLanguage().translateString("nukkit.resources.zip.no-manifest")));
            }

            this.manifest = new JsonParser()
                    .parse(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))
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

    @Override
    public int getPackSize() {
        return (int) this.file.length();
    }

    @Override
    public byte[] getSha256() {
        if (this.sha256 == null) {
            try {
                this.sha256 = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(this.file.toPath()));
            } catch (Exception e) {
                Server.getInstance().getLogger().logException(e);
            }
        }
        return this.sha256;
    }

    @Override
    public byte[] getPackChunk(int off, int len) {
        int size = this.getPackSize();
        byte[] chunk;
        if (size - off > len) {
            chunk = new byte[len];
        } else {
            chunk = new byte[size - off];
        }

        if (chunk.length == 0) {
            return chunk;
        }
        try (RandomAccessFile raf = new RandomAccessFile(this.file, "r")) {
            raf.seek(off);
            raf.readFully(chunk);
        } catch (Exception e) {
            Server.getInstance().getLogger().logException(e);
        }

        return chunk;
    }

    /**
     * @deprecated Use {@link #setCDNUrl(String)} instead
     */
    @Deprecated
    public void setCdnUrl(String cdnUrl) {
        this.setCDNUrl(cdnUrl);
    }
}
