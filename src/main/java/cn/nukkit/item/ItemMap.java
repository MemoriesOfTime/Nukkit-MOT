package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ClientboundMapItemDataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.utils.MainLogger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Created by CreeperFace on 18.3.2017.
 */
public class ItemMap extends Item {

    public static int mapCount = 0;

    private BufferedImage image;

    public ItemMap() {
        this(0, 1);
    }

    public ItemMap(Integer meta) {
        this(meta, 1);
    }

    public ItemMap(Integer meta, int count) {
        super(MAP, meta, count, "Map");

        CompoundTag tag = this.hasCompoundTag() ? this.getNamedTag() : new CompoundTag();
        if (!tag.contains("map_uuid")) {
            tag.putLong("map_uuid", mapCount++);
            this.setNamedTag(tag);
        }
    }

    public void setImage(File file) throws IOException {
        setImage(ImageIO.read(file));
    }

    public void setImage(BufferedImage image) {
        try {
            if (image.getHeight() != 128 || image.getWidth() != 128) {
                this.image = new BufferedImage(128, 128, image.getType());
                Graphics2D g = this.image.createGraphics();
                g.drawImage(image, 0, 0, 128, 128, null);
                g.dispose();
            } else {
                this.image = image;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(this.image, "png", baos);

            this.setNamedTag(this.getNamedTag().putByteArray("Colors", baos.toByteArray()));
            baos.close();
        } catch (IOException e) {
            MainLogger.getLogger().logException(e);
        }
    }

    protected BufferedImage loadImageFromNBT() {
        try {
            byte[] data = getNamedTag().getByteArray("Colors");
            image = ImageIO.read(new ByteArrayInputStream(data));
            return image;
        } catch (IOException e) {
            MainLogger.getLogger().logException(e);
        }

        return null;
    }

    public long getMapId() {
        return getNamedTag().getLong("map_uuid");
    }

    public void sendImage(Player p) {
        // Don't load the image from NBT if it has been done before
        BufferedImage image = this.image != null ? this.image : loadImageFromNBT();

        ClientboundMapItemDataPacket pk = new ClientboundMapItemDataPacket();
        pk.mapId = getMapId();
        pk.scale = 0;
        pk.width = 128;
        pk.height = 128;
        pk.offsetX = 0;
        pk.offsetZ = 0;
        pk.image = image;
        if (p.protocol >= ProtocolInfo.v1_19_50_20) {
            pk.eids = new long[]{pk.mapId};
        }

        p.dataPacket(pk);

        if (p.protocol >= ProtocolInfo.v1_19_20 && p.protocol < ProtocolInfo.v1_19_50) {
            Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> p.dataPacket(pk), 20);
        }
    }

    public void renderMap(Level level, int centerX, int centerZ) {
        renderMap(level, centerX, centerZ, 1);
    }

    public void renderMap(Level level, int centerX, int centerZ, int zoom) {
        if (zoom < 1)
            throw new IllegalArgumentException("Zoom must be greater than 0");
        int[] pixels = new int[128 * 128];
        try {
            for (int x = 0; x < 128 * zoom; x += zoom) {
                for (int z = 0; z < 128 * zoom; z += zoom) {
                    pixels[(x * 128 + z) / zoom] = level.getMapColorAt(centerX + x, centerZ + z).getARGB();
                }
            }
            BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 128, 128, pixels, 0, 128);

            this.setImage(image);
        } catch (Exception ex) {
            MainLogger.getLogger().warning("There was an error while generating map image", ex);
        }
    }

    public boolean trySendImage(Player p) {
        BufferedImage image = this.image != null ? this.image : loadImageFromNBT();
        if (image == null) return false;
        this.sendImage(p);
        return true;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
