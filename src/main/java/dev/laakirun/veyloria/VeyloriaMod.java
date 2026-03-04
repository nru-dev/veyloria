package dev.laakirun.veyloria;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(VeyloriaMod.MOD_ID)
public final class VeyloriaMod {
    public static final String MOD_ID = "veyloria";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VeyloriaMod(IEventBus modEventBus) {
        LOGGER.info("Initializing mod {}", MOD_ID);
    }
}
