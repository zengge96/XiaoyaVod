package com.ysp.svc;

import java.io.*;
import java.net.*;

public class NetBridge {
    public static String get(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", "qqlive");
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            int code = c.getResponseCode();
            InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) b.write(buf, 0, n);
            is.close();
            return code + "|" + new String(b.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "ERR|" + t;
        }
    }
}
