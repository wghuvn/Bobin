package com.bobincraft.destruction.debris;

import java.util.*;

/** Exact support-cell reverse index. Both notifications and incremental per-fragment rechecks are bounded. */
public final class DebrisSupportIndex {
    private final Map<Long,NavigableSet<UUID>> byPosition=new HashMap<>();
    private final Map<UUID,Set<Long>> byFragment=new HashMap<>();
    private final ArrayDeque<Long> changed=new ArrayDeque<>();
    private final Set<Long> queued=new HashSet<>(),repeat=new HashSet<>();
    private final Map<Long,UUID> cursors=new HashMap<>();
    public void update(UUID id,Set<Long> positions) {
        if(positions.equals(byFragment.getOrDefault(id,Set.of())))return;
        Set<Long> old=byFragment.remove(id);if(old!=null)for(long p:old){var ids=byPosition.get(p);if(ids!=null){ids.remove(id);if(ids.isEmpty())byPosition.remove(p);}}
        if(!positions.isEmpty()){byFragment.put(id,Set.copyOf(positions));for(long p:positions)byPosition.computeIfAbsent(p,k->new TreeSet<>()).add(id);}
    }
    public boolean tracks(long pos){return byPosition.containsKey(pos);}
    public void changed(long pos){if(!byPosition.containsKey(pos))return;if(queued.add(pos))changed.add(pos);else if(cursors.containsKey(pos))repeat.add(pos);}
    public Set<UUID> drain(int maximum) {
        Set<UUID> result=new LinkedHashSet<>();
        while(maximum-->0&&!changed.isEmpty()) {
            long p=changed.peek();var ids=byPosition.get(p);UUID cursor=cursors.get(p),next=ids==null||ids.isEmpty()?null:cursor==null?ids.first():ids.higher(cursor);
            if(next!=null){cursors.put(p,next);result.add(next);}
            else {changed.remove();queued.remove(p);cursors.remove(p);if(repeat.remove(p)&&byPosition.containsKey(p)){queued.add(p);changed.add(p);}}
        }return result;
    }
}
