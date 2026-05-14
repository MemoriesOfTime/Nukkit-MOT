package cn.nukkit.network.protocol.types.store;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * @since v975
 */
@ToString
@AllArgsConstructor
@Getter
public class ClientStoreEntrypointConfiguration {
    private String storeId;
    private String storeName;
}
