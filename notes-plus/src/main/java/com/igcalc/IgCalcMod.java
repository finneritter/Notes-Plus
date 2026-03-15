package com.igcalc;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IgCalcMod implements ModInitializer {

    public static final String MOD_ID = "igcalc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("igCalc initialized.");
    }
}
