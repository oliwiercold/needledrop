package com.musicdiscs.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * jukebox_song is a datapack-loaded registry, not something registerable
 * directly in Java. This adds our generated jukebox_song data as an extra
 * source merged into Minecraft's "vanilla" data -- the same set used for
 * the early world-creation preview (built before any world folder exists,
 * from vanilla + built-in mod data only) and every actual world afterward.
 *
 * The data lives in an external folder under the game directory (not the
 * mod's own jar/resources), so this works the same way whether the mod
 * runs from Gradle or as an installed jar -- a real jar can't be written
 * into at runtime.
 */
@Mixin(ServerPacksSource.class)
public class ServerPacksSourceMixin {

	@Redirect(
			method = "createVanillaPackSource",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/packs/VanillaPackResourcesBuilder;build(Lnet/minecraft/server/packs/PackLocationInfo;)Lnet/minecraft/server/packs/VanillaPackResources;"
			)
	)
	private static VanillaPackResources musicdiscs$addGeneratedData(VanillaPackResourcesBuilder builder, PackLocationInfo info) {
		// pushAssetPath wants the path to the "data" folder itself, not the
		// pack root containing pack.mcmeta -- confirmed by reading
		// VanillaPackResourcesBuilder's own pushJarResources(), which passes
		// the resolved data/assets directory directly, never its parent.
		Path dataDir = FabricLoader.getInstance().getGameDir()
				.resolve("musicdiscs_cache").resolve("datapack_template").resolve("data");
		if (Files.isDirectory(dataDir)) {
			builder.pushAssetPath(PackType.SERVER_DATA, dataDir);
		}
		return builder.build(info);
	}
}
