package com.github.catvod.crawler;
import android.content.Context;
import java.util.List;
import java.util.Map;
public abstract class Spider {
    public void init(Context c) throws Exception {}
    public void init(Context c, String e) throws Exception { init(c); }
    public String homeContent(boolean f) throws Exception { return ""; }
    public String homeVideoContent() throws Exception { return ""; }
    public String categoryContent(String t, String p, boolean f, Map<String, String> e) throws Exception { return ""; }
    public String detailContent(List<String> ids) throws Exception { return ""; }
    public String searchContent(String k, boolean q) throws Exception { return ""; }
    public String searchContent(String k, boolean q, String p) throws Exception { return ""; }
    public String playerContent(String f, String i, List<String> v) throws Exception { return ""; }
    public String liveContent(String u) throws Exception { return ""; }
    public Object[] proxy(Map<String, String> p) throws Exception { return null; }
    public void destroy() {}
}
