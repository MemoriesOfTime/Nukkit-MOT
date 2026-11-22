package cn.nukkit.network.protocol;

import cn.nukkit.entity.data.Skin;
import lombok.ToString;

import java.util.UUID;

@ToString
public class PlayerSkinPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.PLAYER_SKIN_PACKET;

    public UUID uuid;
    public Skin skin;
    public String newSkinName;
    public String oldSkinName;
    public boolean premium;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        uuid = getUUID();
        if (protocol < 388) {
            skin = new Skin();
            skin.setSkinId(getString());
            newSkinName = getString();
            oldSkinName = getString();
            skin.setSkinData(getByteArray());
            skin.setCapeData(getByteArray());
            skin.setGeometryName(getString());
            skin.setGeometryData(getString());
            if (protocol > 274) {
                premium = getBoolean();
            }
        } else {
            skin = getSkin(protocol);
            newSkinName = getString();
            oldSkinName = getString();
            if (protocol < ProtocolInfo.v1_26_40 && !feof()) {
                skin.setTrusted(getBoolean());
            }
        }
    }

    @Override
    public void encode() {
        reset();
        if (skin != null) {
            long estimated = 32L + estimatedStringBytes(newSkinName) + estimatedStringBytes(oldSkinName);
            if (protocol < 388) {
                byte[] skinBytes = skin.getSkinData() != null ? skin.getSkinData().data : null;
                byte[] capeBytes = skin.getCapeData() != null ? skin.getCapeData().data : null;
                estimated += 72L
                        + (skinBytes != null ? skinBytes.length : 0)
                        + (capeBytes != null ? capeBytes.length : 0)
                        + estimatedStringBytes(skin.getGeometryData());
            } else {
                estimated += estimatedSkinSize(this.gameVersion, skin);
            }
            if (estimated > 0 && estimated <= Integer.MAX_VALUE - 64L) {
                reserve((int) estimated);
            }
        }
        putUUID(uuid);
        if (protocol < 388) {
            putString(skin.isLegacySlim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom");
            putString(newSkinName);
            putString(oldSkinName);
            putByteArray(skin.getSkinData().data);
            putByteArray(skin.getCapeData().data);
            putString(skin.isLegacySlim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom");
            putString(skin.getGeometryData());
            if (protocol > 274) {
                putBoolean(premium);
            }
        } else {
            putSkin(this.gameVersion, skin);
            putString(newSkinName);
            putString(oldSkinName);
            if (protocol >= ProtocolInfo.v1_14_60 && protocol < ProtocolInfo.v1_26_40) {
                putBoolean(skin.isTrusted());
            }
        }
    }
}
