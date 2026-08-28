package com.musicdiscs.scan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MusicScanner {

	/**
	 * Walks musicFolder looking for files with one of the given extensions.
	 * Returns at most maxDiscs entries (sorted by path, so the result is stable
	 * between runs; maxDiscs is a safety valve, not a real limit, see
	 * ModConfig). If the folder doesn't exist, returns an empty list instead
	 * of throwing, since that's a config problem, not a crash.
	 */
	public static List<DiscEntry> scan(Path musicFolder, Set<String> extensions, int maxDiscs) {
		List<DiscEntry> results = new ArrayList<>();

		if (!Files.isDirectory(musicFolder)) {
			System.err.println("[musicdiscs] Music folder does not exist, skipping scan: " + musicFolder);
			return results;
		}

		List<Path> files;
		try (Stream<Path> walk = Files.walk(musicFolder)) {
			files = walk
					.filter(Files::isRegularFile)
					.filter(p -> hasWantedExtension(p, extensions))
					.sorted(Comparator.comparing(Path::toString))
					.limit(maxDiscs)
					.collect(Collectors.toList());
		} catch (IOException e) {
			System.err.println("[musicdiscs] Failed walking music folder: " + e.getMessage());
			return results;
		}

		for (Path file : files) {
			String id = shortHash(file.toAbsolutePath().toString());
			results.add(new DiscEntry(id, file));
		}

		System.out.println("[musicdiscs] Found " + results.size() + " tracks in " + musicFolder);
		return results;
	}

	private static boolean hasWantedExtension(Path p, Set<String> extensions) {
		String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		if (dot < 0) return false;
		return extensions.contains(name.substring(dot + 1));
	}

	/** First 8 hex chars of a SHA-256 hash, plenty unique for a personal music library, and stable across restarts. */
	private static String shortHash(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 4; i++) {
				sb.append(String.format("%02x", hash[i]));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is always available on the JVM; this is unreachable in practice.
			return Integer.toHexString(input.hashCode());
		}
	}
}
