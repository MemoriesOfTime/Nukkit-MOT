package cn.nukkit.ddui;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ObservableOptions {

    @Builder.Default
    private final boolean clientWritable = false;
}
