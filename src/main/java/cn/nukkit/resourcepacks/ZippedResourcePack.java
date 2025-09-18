package cn.nukkit.resourcepacks;

import cn.nukkit.Server;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZippedResourcePack extends AbstractResourcePack {

    private File file;
    private byte[] sha256;

    private String encryptionKey = "";
    private String cdnUrl = "";
    private boolean isBehaviourPack = false;

    public boolean isBehaviourPack() {
        return isBehaviourPack;
    }

    public ZippedResourcePack(File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException(Server.getInstance().getLanguage()
                    .translateString("nukkit.resources.zip.not-found", file.getName()));
        }

        this.file = file;

        try (ZipFile zip = new ZipFile(file)) {
            Server.getInstance().getLogger().info("正在查找资源包 " + file.getName() + " 中的manifest文件...");
            
            // 首先尝试在根目录查找manifest文件
            Server.getInstance().getLogger().info("检查根目录是否有manifest.json...");
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                Server.getInstance().getLogger().info("根目录没有manifest.json，检查pack_manifest.json...");
                entry = zip.getEntry("pack_manifest.json");
            }
            
            if (entry != null) {
                Server.getInstance().getLogger().info("在根目录找到manifest文件: " + entry.getName());
            } else {
                Server.getInstance().getLogger().info("根目录没有找到manifest文件，开始搜索子文件夹...");
                
                // 列出所有zip条目用于调试
                zip.stream().forEach(e -> {
                    if (!e.isDirectory()) {
                        String name = e.getName().toLowerCase(Locale.ROOT);
                        if (name.endsWith("manifest.json") || name.endsWith("pack_manifest.json")) {
                            Server.getInstance().getLogger().info("发现可能的manifest文件: " + e.getName());
                        }
                    }
                });
                
                // 在所有子文件夹中查找manifest文件
                entry = zip.stream()
                        .filter(e -> !e.isDirectory())
                        .filter(e -> {
                            String name = e.getName().toLowerCase(Locale.ROOT);
                            boolean isManifest = name.endsWith("manifest.json") || name.endsWith("pack_manifest.json");
                            if (isManifest) {
                                Server.getInstance().getLogger().info("检查文件: " + e.getName() + " (匹配manifest后缀)");
                            }
                            return isManifest;
                        })
                        .filter(e -> {
                            String fileName = new File(e.getName()).getName().toLowerCase(Locale.ROOT);
                            boolean isValidManifest = fileName.equals("manifest.json") || fileName.equals("pack_manifest.json");
                            if (isValidManifest) {
                                Server.getInstance().getLogger().info("找到有效的manifest文件: " + e.getName());
                            } else {
                                Server.getInstance().getLogger().info("文件名不匹配: " + e.getName() + " (文件名: " + fileName + ")");
                            }
                            return isValidManifest;
                        })
                        .findFirst()
                        .orElse(null);
            }

            // 如果找到了manifest文件，则解析它
            if (entry != null) {
                Server.getInstance().getLogger().info("成功找到manifest文件: " + entry.getName() + "，开始解析...");
                this.manifest = new JsonParser()
                        .parse(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                Server.getInstance().getLogger().info("manifest文件解析成功");



                if (this.manifest.has("modules")) {
                    Iterator var2 = this.manifest.getAsJsonArray("modules").iterator();

                    while(var2.hasNext()) {
                        JsonElement moduleElement = (JsonElement)var2.next();

                        try {
                            if (moduleElement.isJsonObject()) {
                                JsonObject module = moduleElement.getAsJsonObject();
                                if (module.has("type")) {
                                    JsonElement typeElement = module.get("type");
                                    if (typeElement.isJsonPrimitive() && typeElement.getAsJsonPrimitive().isString()) {
                                        String typeValue = typeElement.getAsString();
                                        if ("data".equals(typeValue)) {
                                            this.isBehaviourPack = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        } catch (Exception var7) {
                        }
                    }
                }

            } else {
                Server.getInstance().getLogger().error("在资源包 " + file.getName() + " 中未找到任何manifest文件");
                throw new IllegalArgumentException(Server.getInstance().getLanguage()
                        .translateString("nukkit.resources.zip.no-manifest"));
            }
            if (!this.verifyManifest()) {
                throw new IllegalArgumentException(Server.getInstance().getLanguage()
                        .translateString("nukkit.resources.zip.invalid-manifest"));
            }

            File parentFolder = this.file.getParentFile();
            if (parentFolder != null && parentFolder.isDirectory()) {
                File keyFile = new File(parentFolder, this.file.getName() + ".key");
                if (keyFile.exists()) {
                    this.encryptionKey = new String(Files.readAllBytes(keyFile.toPath()), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            Server.getInstance().getLogger().logException(e);
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
        byte[] chunk;
        if (this.getPackSize() - off > len) {
            chunk = new byte[len];
        } else {
            chunk = new byte[this.getPackSize() - off];
        }

        try (FileInputStream fis = new FileInputStream(this.file)) {
            fis.skip(off);
            fis.read(chunk);
        } catch (Exception e) {
            Server.getInstance().getLogger().logException(e);
        }

        return chunk;
    }

    @Override
    public String getEncryptionKey() {
        return this.encryptionKey;
    }

    @Override
    public String getCDNUrl() {
        return this.cdnUrl;
    }

    public void setCdnUrl(String cdnUrl) {
        this.cdnUrl = cdnUrl;
    }

    @Override
    public UUID getPackId() {
        return super.getPackId();
    }
}
