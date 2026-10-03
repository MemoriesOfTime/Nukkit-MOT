package cn.nukkit.network.protocol.types;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Setter
@Getter
@ToString
public class GatheringsConfigurationJoinInfo {

    public UUID experienceId;
    public String experienceName;
    public UUID worldId;
    public String worldName;
    public String creatorId;
    public UUID targetId;
    public String scenarioId;
    public String serverId;

    public GatheringsConfigurationJoinInfo() {
    }

    public GatheringsConfigurationJoinInfo(UUID experienceId, String experienceName, UUID worldId,
                                           String worldName, String creatorId, UUID targetId,
                                           String scenarioId, String serverId) {
        this.experienceId = experienceId;
        this.experienceName = experienceName;
        this.worldId = worldId;
        this.worldName = worldName;
        this.creatorId = creatorId;
        this.targetId = targetId;
        this.scenarioId = scenarioId;
        this.serverId = serverId;
    }
}
