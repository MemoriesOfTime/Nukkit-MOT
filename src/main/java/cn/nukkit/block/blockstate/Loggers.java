package cn.nukkit.block.blockstate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author joserobjr
 */
final class Loggers {
    private Loggers(){ throw new UnsupportedOperationException(); }
    
    static final Logger logIBlockState = LogManager.getLogger(IBlockState.class);
    static final Logger logIMutableBlockState = LogManager.getLogger(IMutableBlockState.class);
}
