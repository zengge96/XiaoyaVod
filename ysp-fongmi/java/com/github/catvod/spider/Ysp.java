package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.ysp.svc.NetBridge;
import com.ysp.tv.ckey.CKey;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.io.File;

public class Ysp extends Spider {
    private Context ctx;

    public static native void installCmdHook(String pkg);

    private static void jlog(String s) {
        try {
            java.io.File d = new java.io.File("/sdcard/TV");
            if (!d.exists()) d.mkdirs();
            java.io.File f2 = new java.io.File(d, "probe.txt");
            java.io.FileOutputStream p = new java.io.FileOutputStream(f2);
            p.write(49); p.close();
            java.io.FileOutputStream f = new java.io.FileOutputStream(new java.io.File(d, "ysp_java.log"), true);
            f.write((System.currentTimeMillis() + " " + s + "\n").getBytes("UTF-8"));
            f.close();
        } catch (Throwable t) {
            try {
                java.io.FileOutputStream f = new java.io.FileOutputStream("/data/local/tmp/ysp_java_err.log", true);
                f.write((String.valueOf(t) + "\n").getBytes("UTF-8"));
                f.close();
            } catch (Throwable ignore) {}
        }
    }

    private static void installCrashHandler() {
        jlog("CRASH_HANDLER_SKIP");
    }

    private static final String LIVEINFO =
        "https://liveinfo.ysp.cctv.cn/?spacode=7&app_version=305060&spwm=2&hevclv=26&platform=4330303&sdtfrom=v5028"
        + "&stream=3&sphttps=0&cnlid=2024078201&spdynamicrange=1&refer_pageId=none&spdemuxer=14"
        + "&spvcode=MSgzMDoyMTYwLDYwOjE0NDAsOTA6MTA4MCwxMjA6MTA4MHwzMDoyMTYwLDYwOjE0NDAsOTA6MTA4MCwxMjA6MTA4MCk7MigzMDoyMTYwLDYwOjE0NDAsOTA6MTA4MHwzMDoxMDgwLDYwOjEwODAsOTA6MTA4MCwxMjA6MTA4MCk="
        + "&sphdrfps=60&defnsrc=3&livepid=600001859&program_id=22628854"
        + "&appVer=V8.26.1034.0171&defn=fhd&encryptVer=4.2&spdrm=2&pageId=page_channel_510104"
        + "&newnettype=1&spflvaudio=1&adjust=1&logintype=1&vip_status=0&spflv=1&uhd_flag=0&cmd=2&otype=json&caplv=1";

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
            os.close();
            is.close();
        }
        System.load(out.getAbsolutePath());
    }

    @Override
    public void init(Context context) throws Exception {
        ctx = context;
        jlog("INIT pkg=" + context.getPackageName() + " arch=" + System.getProperty("os.arch"));
        installCrashHandler();
        try { loadSo(context, "cmdhook"); jlog("HOOK_SO_LOADED"); } catch (Throwable t) { jlog("HOOK_SO_FAIL " + t); }
        jlog("BEFORE_CK_SO");
        try { loadSo(context, "stdc++"); jlog("STDC_SO_LOADED"); } catch (Throwable t) { jlog("STDC_SO_SKIP " + t); }
        try { loadSo(context, "ckeygenerator"); jlog("CK_SO_LOADED"); } catch (Throwable t) { jlog("CK_SO_FAIL " + t); }
        jlog("BEFORE_GUARD_SO");
        try { loadSo(context, "ckguard"); jlog("GUARD_SO_LOADED"); } catch (Throwable t) { jlog("GUARD_SO_FAIL " + t); }
        jlog("SKIP_EST_SO");
        jlog("BEFORE_INSTALL_HOOK");
        try { installCmdHook(context.getPackageName()); jlog("HOOK_INSTALLED"); } catch (Throwable t) { jlog("HOOK_FAIL " + t); }
        jlog("BEFORE_CK_INIT");
        CKey.INSTANCE.init(context);
        jlog("CK_INIT_DONE");
    }

    @Override
    public String liveContent(String url) throws Exception {
        try {
            long tick = System.currentTimeMillis() / 1000;
            String ck = CKey.INSTANCE.generate(tick, "2024078201");
            String guid = CKey.INSTANCE.getGUID();
            String est = CKey.INSTANCE.estId();
            String flowid = java.util.UUID.randomUUID().toString() + "_" + tick + "_4330303";
            String rand = String.valueOf(Math.random());
            String liveUrl = LIVEINFO
                + "&cKey=" + ck
                + "&fntick=" + tick
                + "&guid=" + guid
                + "&estid=" + URLEncoder.encode(est, "UTF-8")
                + "&flowid=" + flowid
                + "&randnum=" + rand;
            String resp = NetBridge.get(liveUrl);
            int idx = resp.indexOf('|');
            String body = idx >= 0 ? resp.substring(idx + 1) : "";
            JSONObject jo = new JSONObject(body);
            String pu = jo.optString("playurl", "");
            if (pu.isEmpty()) return "#EXTM3U\n";
            return "#EXTM3U\n#EXTINF:-1,CCTV1\n" + pu + "\n";
        } catch (Throwable t) {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            jlog("LIVE_FAIL " + sw.toString());
            return "#EXTM3U\n";
        }
    }
}
