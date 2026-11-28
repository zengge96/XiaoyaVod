package com.ysp.tv.ckey;
import android.content.Context;
public class CKey {
    public static final CKey INSTANCE = new CKey();
    public void init(Context c) {}
    public String generate(long tick, String vid) { return ""; }
    public String getGUID() { return ""; }
    public String estId() { return ""; }
}
