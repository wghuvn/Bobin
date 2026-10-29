package com.bobincraft.armament.client.model.glb;

import java.util.*;

/** Owned resources: failed factories keep the last good entry, replacements close exactly once. */
public final class ReloadableResourceCache<K,V extends AutoCloseable> implements AutoCloseable {
    @FunctionalInterface public interface Factory<V>{ V create()throws Exception; }
    private final Map<K,V> entries=new HashMap<>();
    private long generation;
    public synchronized Optional<V> get(K key){return Optional.ofNullable(entries.get(key));}
    public synchronized V getOrLoad(K key,Factory<V> factory)throws Exception{
        V value=entries.get(key);return value==null?reload(key,factory):value;
    }
    /** The factory owns partial allocations until it returns a fully initialized value. */
    public synchronized V reload(K key,Factory<V> factory)throws Exception{
        Objects.requireNonNull(key);V candidate=Objects.requireNonNull(factory.create());
        V old=entries.put(key,candidate);generation++;
        if(old!=null && old!=candidate)old.close();return candidate;
    }
    public synchronized int size(){return entries.size();}
    public synchronized long generation(){return generation;}
    @Override public synchronized void close()throws Exception{
        List<V> old=new ArrayList<>(entries.values());entries.clear();
        Exception failure=null;
        for(V value:old)try{value.close();}catch(Exception e){if(failure==null)failure=e;else failure.addSuppressed(e);}
        if(failure!=null)throw failure;
    }
}
