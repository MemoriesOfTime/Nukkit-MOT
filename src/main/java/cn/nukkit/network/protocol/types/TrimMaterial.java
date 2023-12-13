package cn.nukkit.network.protocol.types;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class TrimMaterial {

    private String materialId;
    private String color;
    private String itemName;

}
