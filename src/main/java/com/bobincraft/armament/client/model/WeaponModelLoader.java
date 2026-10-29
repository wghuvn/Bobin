package com.bobincraft.armament.client.model;

import com.bobincraft.armament.api.WeaponId;
import java.io.IOException;
import java.io.InputStream;

/** Future client loader boundary. Resource adapters must reject unsafe/external URIs. */
public interface WeaponModelLoader {
    WeaponModelAsset load(WeaponId resource, ResourceSource resources) throws IOException;

    @FunctionalInterface
    interface ResourceSource {
        /** Opens a resource-pack entry; the caller owns and closes the returned stream. */
        InputStream open(WeaponId resource) throws IOException;
    }
}
