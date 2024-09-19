package cn.nukkit.network.protocol.types.inventory;

import lombok.Value;

@Value
public class FullContainerName {
   ContainerSlotType container;
   /**
    * May be null if not present since v729
    */
   Integer dynamicId;
}
