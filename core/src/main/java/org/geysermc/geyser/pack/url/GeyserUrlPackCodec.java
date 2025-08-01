/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.pack.url;

import lombok.Getter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.pack.PathPackCodec;
import org.geysermc.geyser.api.pack.UrlPackCodec;
import org.geysermc.geyser.pack.GeyserResourcePack;
import org.geysermc.geyser.pack.ResourcePackHolder;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.loader.ResourcePackLoader;
import org.geysermc.geyser.text.GeyserLocale;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;

public class GeyserUrlPackCodec extends UrlPackCodec {
    private final @NonNull String url;
    @Getter
    private PathPackCodec fallback;

    public GeyserUrlPackCodec(@NonNull String url) throws IllegalArgumentException {
        Objects.requireNonNull(url);
        this.url = url;
    }

    private GeyserUrlPackCodec(@NonNull String url, PathPackCodec fallback) {
        Objects.requireNonNull(url);
        this.fallback = fallback;
        this.url = url;
    }

    @Override
    public byte @NonNull [] sha256() {
        Objects.requireNonNull(fallback, "must call #create() before attempting to get the sha256!");
        return fallback.sha256();
    }

    @Override
    public long size() {
        Objects.requireNonNull(fallback, "must call #create() before attempting to get the size!");
        return fallback.size();
    }

    @Override
    public @NonNull SeekableByteChannel serialize() throws IOException {
        Objects.requireNonNull(fallback, "must call #create() before attempting to serialize!!");
        return fallback.serialize();
    }

    @Override
    @NonNull
    public GeyserResourcePack create() {
        return createBuilder().build();
    }

    @Override
    protected GeyserResourcePack.@NonNull Builder createBuilder() {
        if (this.fallback == null) {
            ResourcePackLoader.downloadPack(url, false)
                .thenAccept(pack -> this.fallback = pack)
                .exceptionally(throwable -> {
                    throw new IllegalStateException(throwable.getCause());
                }).join(); // Needed to ensure that we don't attempt to read a pack before downloading/checking it
        }

        return ResourcePackLoader.readPack(this);
    }

    @Override
    public @NonNull String url() {
        return this.url;
    }

    /**
     * Tests whether Geyser's "mirror" of the remote pack needs to be updated.
     * This is triggered if a Bedrock client is unable to download a pack
     * @param holder the current resource pack holder with the "originally" known manifest
     */
    public void testForChanges(ResourcePackHolder holder) {
        ResourcePackLoader.downloadPack(url, true)
            .thenAccept(backingPathCodec -> {
                GeyserResourcePack updatedPack = ResourcePackLoader.readPack(backingPathCodec.path())
                    .contentKey(holder.pack().contentKey())
                    .build();
                if (updatedPack.uuid().equals(holder.uuid())) {
                    var currentVersion = holder.version().toString();
                    var updatedVersion = updatedPack.manifest().header().version().toString();
                    if (currentVersion.equals(updatedVersion)) {
                        GeyserImpl.getInstance().getLogger().info("No version or pack change detected: Was the resource pack server down?");
                        return;
                    } else {
                        GeyserImpl.getInstance().getLogger().info("Detected a new resource pack version (%s, old version %s) for pack at %s!"
                            .formatted(currentVersion, updatedVersion, url));
                    }
                } else {
                    GeyserImpl.getInstance().getLogger().info("Detected a new resource pack at the url %s!".formatted(url));
                    Registries.RESOURCE_PACKS.get().remove(holder.uuid());
                }

                // Update to new url pack codec (same url, updated fallback), and keep content key
                GeyserResourcePack pack = updatedPack.withCodec(new GeyserUrlPackCodec(url, backingPathCodec));
                // Keep the pack options that were previously set
                Registries.RESOURCE_PACKS.get().put(holder.uuid(), holder.withPack(pack));

            })
            .exceptionally(throwable -> {
                GeyserImpl.getInstance().getLogger().error(GeyserLocale.getLocaleStringLog("geyser.resource_pack.broken", url), throwable);
                Registries.RESOURCE_PACKS.get().remove(holder.uuid());
                return null;
            });
    }
}
