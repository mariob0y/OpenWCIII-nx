/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.etheller.warsmash.viewer5.handlers.tga;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.etheller.warsmash.util.image.ImageDecoders;
import com.etheller.warsmash.util.image.PixelImage;
import com.etheller.warsmash.util.image.TgaDecoder;

/**
 *
 * @author Riven, modified by Oger-Lord
 *
 *         Decoding now lives in {@link TgaDecoder} (no AWT). The AWT-only
 *         writeTGA, which nothing called, was removed with the AWT
 *         dependency.
 */
public class TgaFile {

	/**
	 * Read a TGA image from a file
	 */
	public static PixelImage readTGA(final File file) throws FileNotFoundException, IOException {
		try (InputStream stream = new FileInputStream(file)) {
			return readTGA(file.getName(), stream);
		}
	}

	/**
	 * Read a TGA image from an input stream.
	 */
	public static PixelImage readTGA(final String name, final InputStream stream) throws IOException {
		return TgaDecoder.decode(name, ImageDecoders.readAll(stream));
	}
}
