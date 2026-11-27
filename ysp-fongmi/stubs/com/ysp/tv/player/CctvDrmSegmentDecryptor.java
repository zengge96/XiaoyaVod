package com.ysp.tv.player;

public final class CctvDrmSegmentDecryptor {
    public static final CctvDrmSegmentDecryptor INSTANCE = new CctvDrmSegmentDecryptor();
    private CctvDrmSegmentDecryptor() {}
    public final byte[] decrypt(byte[] segment, String uri) { return segment; }
    public final boolean supportsSegmentName(String name) { return false; }
}
