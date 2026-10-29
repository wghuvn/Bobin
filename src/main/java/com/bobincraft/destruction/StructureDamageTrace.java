package com.bobincraft.destruction;

import java.util.*;

/** Opt-in bounded event/commit trace. No disk spam or unbounded per-hit allocations when disabled. */
public final class StructureDamageTrace {
    private static final ArrayDeque<String> RECENT=new ArrayDeque<>();
    private static final int LIMIT=256;
    private static boolean enabled=Boolean.getBoolean("bobin.destruction.trace");
    private StructureDamageTrace(){}
    public static synchronized boolean enabled(){return enabled;}
    public static synchronized void enabled(boolean value){enabled=value;if(!value)RECENT.clear();}
    public static synchronized void add(String value){if(!enabled)return;while(RECENT.size()>=LIMIT)RECENT.remove();RECENT.add(value);}
    public static synchronized List<String> recent(int maximum){return RECENT.stream().skip(Math.max(0,RECENT.size()-Math.max(0,Math.min(32,maximum)))).toList();}
}
