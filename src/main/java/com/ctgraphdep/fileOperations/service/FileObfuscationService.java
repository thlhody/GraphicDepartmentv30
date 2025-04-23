package com.ctgraphdep.fileOperations.service;

import org.springframework.stereotype.Service;

@Service
public class FileObfuscationService {
    // A fixed pattern for XOR operation - can be any byte sequence
    private static final byte[] PATTERN = {(byte)0xAA, (byte)0x55, (byte)0xF0, (byte)0x0F, (byte)0xCC, (byte)0x33, (byte)0xA5, (byte)0x5A};

    public byte[] obfuscate(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte)(data[i] ^ PATTERN[i % PATTERN.length]);
        }
        return result;
    }

    public byte[] deobfuscate(byte[] data) {
        // XOR is its own inverse, so we can use the same operation
        return obfuscate(data);
    }
}