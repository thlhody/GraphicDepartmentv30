package com.ctgraphdep.fileOperations.util;

import java.util.Base64;

/**
 * Base64 utilities for file operations.
 */
public class Base64Util {

    /**
     * Encodes binary data to Base64.
     *
     * @param data The data to encode
     * @return The Base64 encoded string
     */
    public static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Decodes a Base64 string to binary data.
     *
     * @param base64 The Base64 string to decode
     * @return The decoded binary data
     */
    public static byte[] decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }
}
