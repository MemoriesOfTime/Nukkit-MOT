package cn.nukkit.resourcepacks;

import cn.nukkit.Server;
import cn.nukkit.resourcepacks.AbstractResourcePack;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Log4j2
public class NetEaseResourcePack extends AbstractResourcePack {

    private File file;
    private byte[] sha256;
    private String encryptionKey = "";
    private String cdnUrl = "";
    private String packType = "unknown"; // "resources" 或 "data"
    private File manifestFile;
    private List<String> packTypes = new ArrayList<>(); // 支持多种包类型
    private List<File> manifestFiles = new ArrayList<>();
    private List<JsonObject> manifests = new ArrayList<>();

    public NetEaseResourcePack(File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException(Server.getInstance().getLanguage()
                    .translateString("nukkit.resources.zip.not-found", file.getName()));
        }

        this.file = file;

        try {
            if (file.isDirectory()) {
                loadFromDirectory(file);
            } else {
                loadFromZip(file);
            }
        } catch (IOException e) {
            Server.getInstance().getLogger().logException(e);
            throw new IllegalArgumentException("Failed to load NetEase resource pack: " + e.getMessage());
        }

        if (!this.verifyNetEaseManifest()) {
            throw new IllegalArgumentException("Invalid NetEase resource pack manifest");
        }
    }

    private void loadFromDirectory(File directory) throws IOException {
        // 网易格式：查找子文件夹中的manifest文件
        File[] subDirs = directory.listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) {
            throw new IllegalArgumentException("No subdirectories found in NetEase resource pack");
        }

        for (File subDir : subDirs) {
            File manifestFile = findManifestInDirectory(subDir);
            if (manifestFile != null) {
                JsonObject manifestObj = new JsonParser()
                        .parse(new InputStreamReader(new FileInputStream(manifestFile), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                
                this.manifestFiles.add(manifestFile);
                this.manifests.add(manifestObj);
                
                // 确定包类型
                String packType = determinePackTypeFromManifest(manifestObj);
                if (packType != null && !this.packTypes.contains(packType)) {
                    this.packTypes.add(packType);
                    log.info("Detected NetEase pack type: " + packType + " in " + subDir.getName());
                }
            }
        }

        if (this.manifests.isEmpty()) {
            throw new IllegalArgumentException("No valid manifest found in NetEase resource pack subdirectories");
        }
        
        // 设置主manifest为第一个找到的
        this.manifest = this.manifests.get(0);
        this.manifestFile = this.manifestFiles.get(0);

        // 检查加密密钥文件
        File keyFile = new File(directory, directory.getName() + ".key");
        if (keyFile.exists()) {
            this.encryptionKey = new String(Files.readAllBytes(keyFile.toPath()), StandardCharsets.UTF_8);
        }
    }

    private void loadFromZip(File zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            // 网易格式：忽略根目录的manifest，查找子文件夹中的所有manifest
            List<ZipEntry> manifestEntries = zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> {
                        String name = entry.getName().toLowerCase(Locale.ROOT);
                        return (name.endsWith("manifest.json") || name.endsWith("pack_manifest.json"));
                    })
                    .filter(entry -> {
                        // 确保manifest不在根目录
                        String[] pathParts = entry.getName().split("/");
                        return pathParts.length >= 2; // 至少在一级子目录中
                    })
                    .collect(Collectors.toList());

            if (manifestEntries.isEmpty()) {
                throw new IllegalArgumentException("No valid manifest found in NetEase resource pack subdirectories");
            }

            for (ZipEntry manifestEntry : manifestEntries) {
                JsonObject manifestObj = new JsonParser()
                        .parse(new InputStreamReader(zip.getInputStream(manifestEntry), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                
                this.manifests.add(manifestObj);
                
                // 确定包类型
                String packType = determinePackTypeFromManifest(manifestObj);
                if (packType != null && !this.packTypes.contains(packType)) {
                    this.packTypes.add(packType);
                    log.info("Detected NetEase pack type: " + packType + " in " + manifestEntry.getName());
                }
            }
            
            // 设置主manifest为第一个找到的
            this.manifest = this.manifests.get(0);

            // 检查加密密钥文件
            File parentFolder = zipFile.getParentFile();
            if (parentFolder != null && parentFolder.isDirectory()) {
                File keyFile = new File(parentFolder, zipFile.getName() + ".key");
                if (keyFile.exists()) {
                    this.encryptionKey = new String(Files.readAllBytes(keyFile.toPath()), StandardCharsets.UTF_8);
                }
            }
        }
    }

    private File findManifestInDirectory(File directory) {
        File manifestFile = new File(directory, "manifest.json");
        if (manifestFile.exists() && manifestFile.isFile()) {
            return manifestFile;
        }
        
        manifestFile = new File(directory, "pack_manifest.json");
        if (manifestFile.exists() && manifestFile.isFile()) {
            return manifestFile;
        }
        
        return null;
    }

    private String determinePackTypeFromManifest(JsonObject manifest) {
        if (manifest != null && manifest.has("modules")) {
            JsonArray modules = manifest.getAsJsonArray("modules");
            if (modules.size() > 0) {
                JsonObject firstModule = modules.get(0).getAsJsonObject();
                if (firstModule.has("type")) {
                    return firstModule.get("type").getAsString();
                }
            }
        }
        return null;
    }

    protected boolean verifyNetEaseManifest() {
        if (this.manifest == null) {
            return false;
        }
        
        // 网易格式验证：必须有format_version, header, modules
        if (!this.manifest.has("format_version") || 
            !this.manifest.has("header") || 
            !this.manifest.has("modules")) {
            return false;
        }

        JsonObject header = this.manifest.getAsJsonObject("header");
        // 网易格式不强制要求name和description，只验证必要字段
        boolean hasRequiredFields = header.has("uuid") && 
                                   header.has("version") && 
                                   header.getAsJsonArray("version").size() == 3;
        
        if (!hasRequiredFields) {
            return false;
        }

        // 验证modules
        JsonArray modules = this.manifest.getAsJsonArray("modules");
        if (modules.size() == 0) {
            return false;
        }
        
        // 验证第一个module有type和uuid
        JsonObject firstModule = modules.get(0).getAsJsonObject();
        return firstModule.has("type") && 
               firstModule.has("uuid") && 
               firstModule.has("version") &&
               firstModule.getAsJsonArray("version").size() == 3;
    }

    public String getPackType() {
        return packType;
    }

    @Override
    public int getPackSize() {
        return (int) this.file.length();
    }

    @Override
    public byte[] getSha256() {
        if (this.sha256 == null) {
            try {
                if (file.isDirectory()) {
                    // 对于文件夹，计算所有文件的hash
                    this.sha256 = calculateDirectoryHash(file);
                } else {
                    this.sha256 = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(this.file.toPath()));
                }
            } catch (Exception e) {
                Server.getInstance().getLogger().logException(e);
            }
        }
        return this.sha256;
    }

    private byte[] calculateDirectoryHash(File directory) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        updateDigestWithDirectory(digest, directory);
        return digest.digest();
    }

    private void updateDigestWithDirectory(MessageDigest digest, File directory) throws Exception {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    updateDigestWithDirectory(digest, file);
                } else {
                    digest.update(Files.readAllBytes(file.toPath()));
                }
            }
        }
    }

    @Override
    public byte[] getPackChunk(int off, int len) {
        if (file.isDirectory()) {
            // 文件夹格式暂不支持分块读取
            return new byte[0];
        }
        
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

    public List<String> getPackTypes() {
        return new ArrayList<>(packTypes);
    }
    
    public String getPrimaryPackType() {
        return packTypes.isEmpty() ? "unknown" : packTypes.get(0);
    }

    public boolean isResourcePack() {
        return packTypes.contains("resources");
    }

    public boolean isBehaviorPack() {
        return packTypes.contains("data");
    }
    
    public boolean isCompositeResourcePack() {
        return packTypes.size() > 1;
    }
    
    public List<JsonObject> getAllManifests() {
        return new ArrayList<>(manifests);
    }
    
    public JsonObject getManifestByType(String type) {
        for (int i = 0; i < manifests.size(); i++) {
            String manifestType = determinePackTypeFromManifest(manifests.get(i));
            if (type.equals(manifestType)) {
                return manifests.get(i);
            }
        }
        return null;
    }
}