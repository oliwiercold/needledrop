package com.musicdiscs.scan;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Replaces the old "shrink the whole cover to 16x16" approach. Instead:
 * every disc uses the SAME procedurally-drawn vinyl icon (dark body, groove
 * rings, spindle hole), and only the label circle in the middle is tinted --
 * to the album cover's most visually dominant colour, if we found one, or a
 * neutral grey otherwise. Reads cleaner at 16x16 than a full mosaic does,
 * and gives your disc shelf a consistent "look" while still being colour-
 * coded per song.
 */
public class DiscIconRenderer {

	private static final int ICON_SIZE = 16;

	/**
	 * Buckets the image's pixels into a coarse colour grid and returns the
	 * centre of whichever bucket scored highest, weighted toward saturated,
	 * mid-brightness colours -- a flat pixel average tends to collapse
	 * everything to muddy grey/brown, which this avoids.
	 */
	public static Color extractDominantColor(Path sourceImage) {
		try {
			BufferedImage img = ImageIO.read(sourceImage.toFile());
			if (img == null) return null;

			// Downscale first purely for speed -- doesn't need to be exact.
			BufferedImage sample = step(img, 64, 64);

			final int BUCKETS_PER_CHANNEL = 8; // 8x8x8 = 512 buckets
			double[] bucketWeight = new double[BUCKETS_PER_CHANNEL * BUCKETS_PER_CHANNEL * BUCKETS_PER_CHANNEL];
			long[] bucketR = new long[bucketWeight.length];
			long[] bucketG = new long[bucketWeight.length];
			long[] bucketB = new long[bucketWeight.length];
			long[] bucketCount = new long[bucketWeight.length];

			for (int y = 0; y < sample.getHeight(); y++) {
				for (int x = 0; x < sample.getWidth(); x++) {
					int rgb = sample.getRGB(x, y);
					int a = (rgb >> 24) & 0xFF;
					if (a < 40) continue; // skip near-transparent pixels (padding/letterboxing)

					int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
					float[] hsb = Color.RGBtoHSB(r, g, b, null);
					float sat = hsb[1], bri = hsb[2];

					// Prefer saturated, not-too-dark, not-too-bright pixels -- de-prioritises
					// black bars, white backgrounds and pure greys without excluding them outright.
					double weight = 0.15 + sat * (1.0 - Math.abs(bri - 0.6));

					int bucket = bucketIndex(r, g, b, BUCKETS_PER_CHANNEL);
					bucketWeight[bucket] += weight;
					bucketR[bucket] += r;
					bucketG[bucket] += g;
					bucketB[bucket] += b;
					bucketCount[bucket]++;
				}
			}

			int best = -1;
			double bestWeight = -1;
			for (int i = 0; i < bucketWeight.length; i++) {
				if (bucketWeight[i] > bestWeight) {
					bestWeight = bucketWeight[i];
					best = i;
				}
			}

			if (best < 0 || bucketCount[best] == 0) return null;
			return new Color(
					(int) (bucketR[best] / bucketCount[best]),
					(int) (bucketG[best] / bucketCount[best]),
					(int) (bucketB[best] / bucketCount[best])
			);
		} catch (IOException e) {
			System.err.println("[musicdiscs] Could not read cover art " + sourceImage + ": " + e.getMessage());
			return null;
		}
	}

	private static int bucketIndex(int r, int g, int b, int perChannel) {
		int rb = Math.min(perChannel - 1, r * perChannel / 256);
		int gb = Math.min(perChannel - 1, g * perChannel / 256);
		int bb = Math.min(perChannel - 1, b * perChannel / 256);
		return (rb * perChannel + gb) * perChannel + bb;
	}

	/**
	 * A real disc icon (16x16, exact proportions), either with the label
	 * area painted flat magenta (#FF00FF) as a placeholder, or just a raw
	 * vanilla-style texture with a plain near-white label (e.g. straight
	 * off the wiki, unedited) -- see template_assets/README in the game
	 * dir. Exact magenta pixels are replaced with the extracted dominant
	 * colour if any are found; otherwise any near-white pixel (a raw
	 * texture's label, not touched up) is treated as the label instead.
	 * Everything else is copied through unchanged. Falls back to the
	 * hand-authored TEMPLATE below if no template image has been supplied.
	 */
	private static final int PLACEHOLDER_RGB = 0xFFFF00FF;
	private static final int NEAR_WHITE_THRESHOLD = 235;
	private static Path templateImagePath;
	private static boolean templateLoadAttempted = false;
	private static BufferedImage cachedTemplateImage;

	public static void setTemplateImagePath(Path path) {
		templateImagePath = path;
		templateLoadAttempted = false;
		cachedTemplateImage = null;
	}

	/**
	 * Draws the shared vinyl-disc icon with the given label colour and
	 * writes it to targetPng: uses the user-supplied template image if one
	 * has been provided (see setTemplateImagePath), otherwise falls back to
	 * the hand-authored TEMPLATE grid.
	 */
	public static void render(Color labelColor, Path targetPng) {
		try {
			BufferedImage img = loadTemplateImage();
			int labelRgb = labelColor.getRGB() | 0xFF000000;

			if (img != null) {
				img = copyImage(img);
				boolean hasMagenta = false;
				for (int y = 0; y < img.getHeight() && !hasMagenta; y++) {
					for (int x = 0; x < img.getWidth(); x++) {
						if (img.getRGB(x, y) == PLACEHOLDER_RGB) {
							hasMagenta = true;
							break;
						}
					}
				}
				for (int y = 0; y < img.getHeight(); y++) {
					for (int x = 0; x < img.getWidth(); x++) {
						int rgb = img.getRGB(x, y);
						boolean isLabelPixel = hasMagenta ? rgb == PLACEHOLDER_RGB : isNearWhite(rgb);
						if (isLabelPixel) {
							img.setRGB(x, y, labelRgb);
						} else if (((rgb >>> 24) & 0xFF) >= 128) {
							// Darken the disc body -- the reference texture we've been
							// testing with is a lighter grey than vanilla's own discs,
							// which read as much closer to black outside the label.
							img.setRGB(x, y, darken(rgb, 0.55));
						}
					}
				}
			} else {
				img = renderBuiltinDisc(labelColor);
			}

			Files.createDirectories(targetPng.getParent());
			ImageIO.write(img, "png", targetPng.toFile());
		} catch (IOException e) {
			System.err.println("[musicdiscs] Could not write icon " + targetPng + ": " + e.getMessage());
		}
	}

	/**
	 * Built-in disc icon, used whenever no user-supplied template image is
	 * present: a dark vinyl body with a few subtle groove rings and a
	 * label circle tinted to the extracted dominant colour. Computed by
	 * distance-from-centre rather than a hand-typed pixel grid, so it's
	 * exactly radially symmetric by construction.
	 */
	private static BufferedImage renderBuiltinDisc(Color labelColor) {
		BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
		int labelRgb = labelColor.getRGB() | 0xFF000000;
		int labelEdgeRgb = labelColor.darker().getRGB() | 0xFF000000;
		double center = (ICON_SIZE - 1) / 2.0;
		double maxDist = center + 0.5;

		for (int y = 0; y < ICON_SIZE; y++) {
			for (int x = 0; x < ICON_SIZE; x++) {
				double dist = Math.hypot(x - center, y - center);
				if (dist > maxDist) {
					img.setRGB(x, y, 0);
					continue;
				}

				double t = dist / maxDist; // 0 = centre, 1 = rim
				int rgb;
				if (t < 0.42) {
					rgb = labelRgb;
				} else if (t < 0.47) {
					rgb = labelEdgeRgb;
				} else if (t < 0.60) {
					rgb = 0xFF262626;
				} else if (t < 0.64) {
					rgb = 0xFF3A3A3A;
				} else if (t < 0.80) {
					rgb = 0xFF262626;
				} else if (t < 0.85) {
					rgb = 0xFF3A3A3A;
				} else {
					rgb = 0xFF161616;
				}
				img.setRGB(x, y, rgb);
			}
		}

		int mid = ICON_SIZE / 2;
		int spindleRgb = 0xFF0D0D0D;
		img.setRGB(mid - 1, mid - 1, spindleRgb);
		img.setRGB(mid, mid - 1, spindleRgb);
		img.setRGB(mid - 1, mid, spindleRgb);
		img.setRGB(mid, mid, spindleRgb);
		return img;
	}

	private static int darken(int argb, double factor) {
		int a = (argb >>> 24) & 0xFF;
		int r = (int) (((argb >> 16) & 0xFF) * factor);
		int g = (int) (((argb >> 8) & 0xFF) * factor);
		int b = (int) ((argb & 0xFF) * factor);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static boolean isNearWhite(int argb) {
		if (((argb >>> 24) & 0xFF) < 128) return false; // ignore transparent/edge pixels
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		return r >= NEAR_WHITE_THRESHOLD && g >= NEAR_WHITE_THRESHOLD && b >= NEAR_WHITE_THRESHOLD;
	}

	private static BufferedImage loadTemplateImage() {
		if (templateLoadAttempted) return cachedTemplateImage;
		templateLoadAttempted = true;
		if (templateImagePath == null || !Files.isRegularFile(templateImagePath)) return null;
		try {
			cachedTemplateImage = ImageIO.read(templateImagePath.toFile());
			if (cachedTemplateImage != null) {
				System.out.println("[musicdiscs] Using disc icon template from " + templateImagePath);
			}
		} catch (IOException e) {
			System.err.println("[musicdiscs] Could not read disc icon template " + templateImagePath + ": " + e.getMessage());
		}
		return cachedTemplateImage;
	}

	private static BufferedImage copyImage(BufferedImage src) {
		BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return copy;
	}

	private static BufferedImage step(BufferedImage src, int w, int h) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	/** Deterministic fallback colour for tracks with no usable embedded art, so re-scans stay stable instead of re-randomising. */
	public static Color placeholderColor(String seed) {
		int hash = seed.hashCode();
		return Color.getHSBColor((hash & 0xFF) / 255f, 0.45f, 0.55f);
	}
}
