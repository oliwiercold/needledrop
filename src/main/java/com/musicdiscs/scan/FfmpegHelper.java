package com.musicdiscs.scan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * All the ffmpeg/ffprobe plumbing lives here. Every method fails soft:
 * if ffmpeg/ffprobe errors out or isn't found, we log and return null/false
 * rather than throwing, since one broken/DRM'd file shouldn't stop the whole
 * scan.
 */
public class FfmpegHelper {

	private final String ffmpegPath;
	private final String ffprobePath;

	public FfmpegHelper(String ffmpegPath) {
		this.ffmpegPath = ffmpegPath;
		// Assume ffprobe sits next to ffmpeg with the matching name -- true for
		// every normal ffmpeg distribution (the official builds, choco, apt, brew...).
		if (ffmpegPath.toLowerCase(Locale.ROOT).contains("ffmpeg")) {
			this.ffprobePath = ffmpegPath.toLowerCase(Locale.ROOT).replace("ffmpeg", "ffprobe");
		} else {
			this.ffprobePath = "ffprobe";
		}
	}

	/** Checks ffmpeg is actually runnable before we bother scanning anything. */
	public boolean isAvailable() {
		try {
			Process p = new ProcessBuilder(ffmpegPath, "-version").redirectErrorStream(true).start();
			boolean finished = p.waitFor(10, TimeUnit.SECONDS);
			return finished && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/** Reads title/artist tags and duration via ffprobe. Fills in DiscEntry fields it finds; leaves defaults otherwise. */
	public void readMetadata(DiscEntry entry) {
		try {
			Process p = new ProcessBuilder(
					ffprobePath, "-v", "quiet",
					"-print_format", "json",
					"-show_format",
					entry.sourceFile.toAbsolutePath().toString()
			).start();

			String output = readAll(p.getInputStream());
			p.waitFor(15, TimeUnit.SECONDS);

			JsonElement root = JsonParser.parseString(output);
			JsonObject format = root.getAsJsonObject().getAsJsonObject("format");
			if (format == null) return;

			if (format.has("duration")) {
				entry.lengthSeconds = format.get("duration").getAsDouble();
			}

			JsonObject tags = format.getAsJsonObject("tags");
			if (tags != null) {
				entry.title = firstTag(tags, "title", "TITLE", "Title").orElse(entry.title);
				entry.artist = firstTag(tags, "artist", "ARTIST", "Artist").orElse(entry.artist);
			}
		} catch (Exception e) {
			System.err.println("[musicdiscs] ffprobe failed for " + entry.sourceFile.getFileName() + ": " + e.getMessage());
		}
	}

	private java.util.Optional<String> firstTag(JsonObject tags, String... keys) {
		for (String k : keys) {
			if (tags.has(k)) {
				String v = tags.get(k).getAsString();
				if (v != null && !v.isBlank()) return java.util.Optional.of(v);
			}
		}
		return java.util.Optional.empty();
	}

	/**
	 * Extracts the embedded cover art (if any) as a raw image file.
	 * Returns the path to the extracted image, or null if the file has no
	 * embedded art (this is common and not an error).
	 */
	public Path extractCoverArt(DiscEntry entry, Path outputDir) {
		try {
			Files.createDirectories(outputDir);
			Path out = outputDir.resolve(entry.id + "_cover.png");
			Process p = new ProcessBuilder(
					ffmpegPath, "-y", "-i", entry.sourceFile.toAbsolutePath().toString(),
					"-an", "-vcodec", "png",
					out.toAbsolutePath().toString()
			).redirectErrorStream(true).start();
			readAll(p.getInputStream());
			boolean finished = p.waitFor(30, TimeUnit.SECONDS);
			if (finished && p.exitValue() == 0 && Files.exists(out) && Files.size(out) > 0) {
				return out;
			}
		} catch (Exception e) {
			// No embedded art, or ffmpeg didn't like this file -- that's fine, we fall back to a neutral colour.
		}
		return null;
	}

	/** Converts to mono OGG Vorbis, which is what Minecraft's sound engine expects. */
	public boolean convertToOgg(DiscEntry entry, Path outputFile) {
		try {
			Files.createDirectories(outputFile.getParent());
			Process p = new ProcessBuilder(
					ffmpegPath, "-y", "-i", entry.sourceFile.toAbsolutePath().toString(),
					"-ac", "1",
					"-c:a", "libvorbis", "-q:a", "4",
					outputFile.toAbsolutePath().toString()
			).redirectErrorStream(true).start();
			String log = readAll(p.getInputStream());
			boolean finished = p.waitFor(120, TimeUnit.SECONDS);
			if (!finished || p.exitValue() != 0) {
				System.err.println("[musicdiscs] ffmpeg conversion failed for " + entry.sourceFile.getFileName() + ":\n" + log);
				return false;
			}
			return true;
		} catch (Exception e) {
			System.err.println("[musicdiscs] ffmpeg conversion crashed for " + entry.sourceFile.getFileName() + ": " + e.getMessage());
			return false;
		}
	}

	private static String readAll(InputStream in) throws IOException {
		return new String(in.readAllBytes());
	}
}
