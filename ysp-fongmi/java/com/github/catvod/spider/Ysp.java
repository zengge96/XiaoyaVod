package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.ysp.svc.NetBridge;
import com.ysp.tv.ckey.CKey;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import com.ysp.tv.player.CctvDrmSegmentDecryptor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Ysp extends Spider {
    private Context ctx;
    private static final int PROXY_PORT = 18089;
    private static final Map<String, String> PROXY_MAP = new ConcurrentHashMap<String, String>();
    private static volatile boolean proxyStarted = false;
    private static final String CACHE_FILE = "/sdcard/Android/data/com.fongmi.android.tv/files/ysp_live_cache.txt";
    private static final long CACHE_TTL = 30000;
    private static final String[] ENC_CNLID = {"2027248701","2027248801","2027248901","2027249001","2027249401","2029810301"};

    private static boolean isEncrypted(String cnlid) {
        for (int i = 0; i < ENC_CNLID.length; i++) if (ENC_CNLID[i].equals(cnlid)) return true;
        return false;
    }

    private static String readFile(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            return new String(b.toByteArray(), "UTF-8");
        } catch (Throwable t) { return null; }
    }

    private static String readCache() {
        try {
            File f = new File(CACHE_FILE);
            if (!f.exists()) return null;
            String all = readFile(f);
            if (all == null) return null;
            String mark = "\n---M3U---\n";
            int sep = all.indexOf(mark);
            if (sep < 0) return null;
            String head = all.substring(0, sep);
            String m3u = all.substring(sep + mark.length());
            String[] hl = head.split("\n");
            long ts = Long.parseLong(hl[0].trim());
            if (System.currentTimeMillis() - ts > CACHE_TTL) return null;
            for (int i = 1; i < hl.length; i++) {
                int p = hl[i].indexOf('|');
                if (p > 0) PROXY_MAP.put(hl[i].substring(0, p), hl[i].substring(p + 1));
            }
            ensureProxy();
            jlog("LIVE cache hit age=" + (System.currentTimeMillis() - ts) + "ms");
            return m3u;
        } catch (Throwable t) { return null; }
    }

    private static void writeCache(String m3u) {
        try {
            StringBuilder sb = new StringBuilder(String.valueOf(System.currentTimeMillis()) + "\n");
            for (Map.Entry<String, String> e : PROXY_MAP.entrySet())
                sb.append(e.getKey()).append('|').append(e.getValue()).append('\n');
            sb.append("---M3U---\n").append(m3u);
            FileOutputStream o = new FileOutputStream(CACHE_FILE);
            o.write(sb.toString().getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) {}
    }

    public static native void installCmdHook(String pkg);

    private static synchronized void ensureProxy() {
        if (proxyStarted) return;
        proxyStarted = true;
        new Thread(new ProxyAccept(), "ysp-proxy").start();
        jlog("PROXY_START " + PROXY_PORT);
    }

    private static class ProxyAccept implements Runnable {
        public void run() {
            try {
                ServerSocket srv = new ServerSocket(PROXY_PORT);
                while (true) {
                    try {
                        Socket c = srv.accept();
                        Thread t = new Thread(new ClientHandler(c), "ysp-proxy-h");
                        t.setDaemon(true);
                        t.start();
                    } catch (Throwable ignore) {}
                }
            } catch (Throwable t) { jlog("PROXY_FAIL " + t); }
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket c;
        ClientHandler(Socket sock) { this.c = sock; }
        public void run() { handleProxy(c); }
    }

    private static void handleProxy(Socket c) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String req = br.readLine();
            if (req == null) { c.close(); return; }
            String path = req.split(" ")[1];
            if (path.startsWith("/")) path = path.substring(1);
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            byte[] body;
            String ctype = "application/vnd.apple.mpegurl";
            if (path.startsWith("ts/")) {
                try {
                    String rest = path.substring(3);
                    int slash = rest.indexOf('/');
                    String realTs = (slash >= 0 ? rest.substring(slash + 1) : rest);
                    if (realTs.startsWith("http")) {
                        java.net.HttpURLConnection tc = (java.net.HttpURLConnection) new java.net.URL(realTs).openConnection();
                        tc.setRequestProperty("User-Agent", "Mozilla/5.0");
                        tc.setConnectTimeout(8000); tc.setReadTimeout(8000);
                        java.io.InputStream tis = tc.getInputStream();
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192]; int r;
                        while ((r = tis.read(buf)) > 0) bos.write(buf, 0, r);
                        tis.close();
                        byte[] raw = bos.toByteArray();
                        String tsName = realTs.substring(realTs.lastIndexOf('/') + 1);
                        boolean sup = CctvDrmSegmentDecryptor.INSTANCE.supportsSegmentName(tsName);
                        jlog("TSSUP " + tsName + " supports=" + sup);
                        byte[] dec = CctvDrmSegmentDecryptor.INSTANCE.decrypt(raw, tsName);
                        body = dec != null ? dec : raw;
                        jlog("TSPROXY " + tsName + " raw=" + raw.length + " dec=" + body.length + " changed=" + (dec != null && dec.length != raw.length));
                    } else { body = new byte[0]; }
                } catch (Throwable t) { jlog("TSPROXY ERR=" + t); body = new byte[0]; }
                OutputStream os = c.getOutputStream();
                os.write(("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: " + body.length + "\r\nConnection: close\r\nAccept-Ranges: bytes\r\n\r\n").getBytes("UTF-8"));
                os.write(body); os.flush(); c.close(); return;
            }
            String src = PROXY_MAP.get(path);
            if (src != null) {
                String m3u8 = NetBridge.get(src);
                int bar = m3u8.indexOf('|');
                if (bar >= 0) m3u8 = m3u8.substring(bar + 1);
                String dir = src.substring(0, src.lastIndexOf('/') + 1);
                StringBuilder out = new StringBuilder();
                String[] lines = m3u8.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String ln = lines[i].trim();
                    if (ln.startsWith("#EXT-X-KEY")) continue;
                    if (!ln.startsWith("#") && ln.length() > 0 && !ln.startsWith("http")) {
                        ln = dir + ln;
                    }
                    if (!ln.startsWith("#") && ln.length() > 0 && ln.startsWith("http")) {
                        ln = "http://127.0.0.1:" + PROXY_PORT + "/ts/0/" + ln;
                    }
                    out.append(ln).append("\n");
                }
                body = out.toString().getBytes("UTF-8");
            } else {
                body = "#EXTM3U\n".getBytes("UTF-8");
            }
            OutputStream os = c.getOutputStream();
            os.write(("HTTP/1.1 200 OK\r\nContent-Type: " + ctype + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            os.write(body);
            os.flush();
            c.close();
        } catch (Throwable t) {
            try { c.close(); } catch (Throwable ignore) {}
        }
    }

    private static String proxyUrl(String srcM3u8) {
        ensureProxy();
        String key = "c" + Math.abs(srcM3u8.hashCode()) + ".m3u8";
        PROXY_MAP.put(key, srcM3u8);
        return "http://127.0.0.1:" + PROXY_PORT + "/" + key;
    }


    private static void jlog(String s) {
        String[] paths = {
            "/sdcard/TV/ysp_java.log",
            "/sdcard/Android/data/com.fongmi.android.tv/files/ysp_java.log",
            "/data/local/tmp/ysp_java.log"
        };
        for (int i = 0; i < paths.length; i++) {
            try {
                java.io.File f = new java.io.File(paths[i]);
                java.io.File par = f.getParentFile();
                if (par != null && !par.exists()) par.mkdirs();
                java.io.FileOutputStream o = new java.io.FileOutputStream(f, true);
                o.write((System.currentTimeMillis() + " " + s + "\n").getBytes("UTF-8"));
                o.close();
                return;
            } catch (Throwable ignore) {}
        }
    }

    private static void installCrashHandler() { jlog("CRASH_HANDLER_SKIP"); }

    private static final String LIVEINFO =
        "https://liveinfo.ysp.cctv.cn/?spacode=7&app_version=305060&spwm=2&hevclv=26&platform=4330303&sdtfrom=v5028"
        + "&stream=3&sphttps=0&spdynamicrange=1&refer_pageId=none&spdemuxer=14"
        + "&spvcode=MSgzMDoyMTYwLDYwOjE0NDAsOTA6MTA4MCwxMjA6MTA4MHwzMDoyMTYwLDYwOjE0NDAsOTA6MTA4MCwxMjA6MTA4MCk7MigzMDoyMTYwLDYwOjE0NDAsOTA6MTA4MHwzMDoxMDgwLDYwOjEwODAsOTA6MTA4MCwxMjA6MTA4MCk="
        + "&sphdrfps=60&defnsrc=3&appVer=V8.26.1034.0171&defn=fhd&encryptVer=4.2&spdrm=2&pageId=page_channel_510104"
        + "&newnettype=1&spflvaudio=1&adjust=1&logintype=1&vip_status=0&spflv=1&uhd_flag=0&cmd=2&otype=json&caplv=1";

    private static final String[][] CHANNELS = {
        {"央视","CCTV1","2024078201","600001859","22628854"},
        {"央视","CCTV2","2024075401","600001800","22628483"},
        {"央视","CCTV3","2024068501","600001801","22627085"},
        {"央视","CCTV4","2029797101","600001814","22626969"},
        {"央视","CCTV5","2024078401","600001818","22624627"},
        {"央视","CCTV5+","2024078001","600001817","22624542"},
        {"央视","CCTV6","2030868901","600001802","22608885"},
        {"央视","CCTV7","2024072001","600004092","22626178"},
        {"央视","CCTV8","2029793001","600001803","22627678"},
        {"央视","CCTV9","2024078601","600004078","22615311"},
        {"央视","CCTV10","2024078701","600001805","22627549"},
        {"央视","CCTV11","2027248701","600001806","22626786"},
        {"央视","CCTV12","2027248801","600001807","22626137"},
        {"央视","CCTV13","2029797201","600001811","22635752"},
        {"央视","CCTV14","2027248901","600001809","22615467"},
        {"央视","CCTV15","2027249001","600001815","22641066"},
        {"央视","CCTV16","2027249101","600098637","22624441"},
        {"央视","CCTV17","2027249401","600001810","22627407"},
        {"央视","CCTV4K","2029810301","600002264","22621308"},
        {"央视","CCTV8K","2026774101","600156816","22620621"},
        {"央视","CGTN","2024181701","600014550","22614334"},
        {"央视","CGTN法语频道","2024181801","600084704","22614085"},
        {"央视","CGTN俄语频道","2024181901","600084758","22614132"},
        {"央视","CGTN阿拉伯语频道","2024182001","600084782","22614145"},
        {"央视","CGTN西班牙语频道","2024182101","600084744","22614147"},
        {"央视","CGTN外语纪录频道","2024182301","600084781","22614346"},
        {"卫视","北京卫视","2024052703","600002309",""},
        {"卫视","东方卫视","2024054503","600002483",""},
        {"卫视","江苏卫视","2024171103","600002521",""},
        {"卫视","浙江卫视","2024054703","600002520",""},
        {"卫视","湖南卫视","2024054803","600002475",""},
        {"卫视","湖北卫视","2024171203","600002508",""},
        {"卫视","广东卫视","2024060903","600002485",""},
        {"卫视","广西卫视","2024060703","600002509",""},
        {"卫视","黑龙江卫视","2029797003","600002498",""},
        {"卫视","海南卫视","2024055603","600002506",""},
        {"卫视","重庆卫视","2024061103","600002531",""},
        {"卫视","深圳卫视","2024061303","600002481",""},
        {"卫视","四川卫视","2024061403","600002516",""},
        {"卫视","河南卫视","2029797303","600002525",""},
        {"卫视","福建东南卫视","2024061503","600002484",""},
        {"卫视","贵州卫视","2024061603","600002490",""},
        {"卫视","江西卫视","2024061703","600002503",""},
        {"卫视","辽宁卫视","2024171303","600002505",""},
        {"卫视","安徽卫视","2024171403","600002532",""},
        {"卫视","河北卫视","2024171503","600002493",""},
        {"卫视","山东卫视","2029787903","600002513",""},
        {"卫视","天津卫视","2019927003","600152137",""},
        {"卫视","新疆卫视","2050898603","600152138",""},
        {"卫视","兵团卫视","2025990501","600193252",""},
        {"卫视","国学频道","2029360403","600213139",""}
    };

    private void loadSo(Context context, String name) throws Exception {
        File dir = new File(context.getCacheDir(), "jar");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, "lib" + name + ".so");
        if (!out.exists()) {
            String abi = System.getProperty("os.arch");
            String abiDir = (abi != null && abi.contains("64")) ? "arm64-v8a" : "armeabi-v7a";
            String res = "lib/" + abiDir + "/lib" + name + ".so";
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(res);
            if (is == null) throw new RuntimeException("so not found: " + res);
            java.io.FileOutputStream os = new java.io.FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            os.close(); is.close();
        }
        System.load(out.getAbsolutePath());
    }

    @Override
    public void init(Context context) throws Exception {
        ctx = context;
        jlog("INIT pkg=" + context.getPackageName() + " arch=" + System.getProperty("os.arch"));
        installCrashHandler();
        try { loadSo(context, "cmdhook"); } catch (Throwable t) { jlog("HOOK_SO_FAIL " + t); }
        try { loadSo(context, "stdc++"); } catch (Throwable ignore) {}
        try { loadSo(context, "ckeygenerator"); } catch (Throwable t) { jlog("CK_SO_FAIL " + t); }
        try { loadSo(context, "ckguard"); } catch (Throwable t) { jlog("GUARD_SO_FAIL " + t); }
        try { installCmdHook(context.getPackageName()); } catch (Throwable t) { jlog("HOOK_FAIL " + t); }
        CKey.INSTANCE.init(context);
        jlog("CK_INIT_DONE");
    }

    private static String channelPlayurl(String[] ch, long tick, String guid, String est) {
        try {
            String cnlid=ch[2], pid=ch[3], prog=ch[4];
            String ck = CKey.INSTANCE.generate(tick, cnlid);
            String url = LIVEINFO
                + "&cnlid=" + cnlid + "&livepid=" + pid
                + (prog.isEmpty() ? "" : "&program_id=" + prog)
                + "&cKey=" + ck + "&fntick=" + tick
                + "&guid=" + guid + "&estid=" + URLEncoder.encode(est, "UTF-8")
                + "&flowid=" + cnlid + "_" + tick + "_4330303"
                + "&randnum=" + Math.random();
            String resp = NetBridge.get(url);
            int idx = resp.indexOf('|');
            String body = idx >= 0 ? resp.substring(idx + 1) : "";
            JSONObject jo = new JSONObject(body);
            int iret = jo.optInt("iretcode", -1);
            if (iret != 0) { jlog("IRET " + ch[1] + "=" + iret + " body=" + body.substring(0, Math.min(80,body.length()))); return null; }
            String pu = jo.optString("playurl", "");
            pu = pu.trim();
            if (!pu.startsWith("http://") && !pu.startsWith("https://")) {
                jlog("BAD_PU " + ch[1] + " len=" + pu.length() + " head=" + pu.substring(0, Math.min(30,pu.length())));
                return null;
            }
            jlog("OK_PU " + ch[1] + " " + pu.length());
            if (isEncrypted(cnlid)) {
                String p = proxyUrl(pu);
                jlog("PROXIED " + ch[1] + " " + p);
                return p;
            }
            return pu;
        } catch (Throwable t) { return null; }
    }

    private static class ChannelTask implements Callable<String> {
        private final int idx;
        private final long tick;
        private final String guid;
        private final String est;
        ChannelTask(int idx, long tick, String guid, String est) {
            this.idx = idx; this.tick = tick; this.guid = guid; this.est = est;
        }
        @Override
        public String call() { return channelPlayurl(CHANNELS[idx], tick, guid, est); }
    }

    @Override
    public String liveContent(String url) throws Exception {
        try {
            String cached = readCache();
            if (cached != null) return cached;
        } catch (Throwable ignore) {}
        try {
            final long tick = System.currentTimeMillis() / 1000;
            final String guid = CKey.INSTANCE.getGUID();
            final String est = CKey.INSTANCE.estId();
            final String[] results = new String[CHANNELS.length];
            ExecutorService pool = Executors.newFixedThreadPool(12);
            List<Future> futures = new ArrayList<Future>();
            jlog("LIVE start channels=" + CHANNELS.length + " tick=" + tick);
            for (int i = 0; i < CHANNELS.length; i++) {
                final int idx = i;
                futures.add(pool.submit(new ChannelTask(idx, tick, guid, est)));
            }
            for (int i = 0; i < futures.size(); i++) {
                try { results[i] = (String) futures.get(i).get(); } catch (Throwable t) { results[i] = null; }
            }
            pool.shutdown();
            StringBuilder sb = new StringBuilder("#EXTM3U\n");
            int ok = 0;
            for (int i = 0; i < CHANNELS.length; i++) {
                String pu = results[i];
                if (pu != null) {
                    sb.append("#EXTINF:-1,").append(CHANNELS[i][0]).append("$").append(CHANNELS[i][1]).append("\n").append(pu).append("\n");
                    ok++;
                }
            }
            jlog("LIVE channels ok=" + ok + "/" + CHANNELS.length);
            String m3u = sb.toString();
            writeCache(m3u);
            return m3u;
        } catch (Throwable t) {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            jlog("LIVE_FAIL " + sw.toString());
            return "#EXTM3U\n";
        }
    }
}
