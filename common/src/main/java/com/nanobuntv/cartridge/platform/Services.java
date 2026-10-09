package com.nanobuntv.cartridge.platform;

import com.nanobuntv.cartridge.Cartridge;
import java.util.ServiceLoader;

/**
 * The platform layer (D32): common code calls loader-specific code only through these services. Each loader
 * project implements every interface and lists the implementation in META-INF/services.
 */
public final class Services {
    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);
    public static final RegistryHelper REGISTRY = load(RegistryHelper.class);
    public static final NetworkHelper NETWORK = load(NetworkHelper.class);

    private Services() {}

    public static <T> T load(Class<T> type) {
        T service = ServiceLoader.load(type, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + type.getName()));
        Cartridge.LOGGER.debug("Loaded {} for {}", service, type.getName());
        return service;
    }
}
