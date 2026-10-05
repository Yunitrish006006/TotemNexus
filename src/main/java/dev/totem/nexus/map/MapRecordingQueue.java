package dev.totem.nexus.map;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded coordinate-only 8x8 jobs. Motion never changes a queued sample's world position. */
public final class MapRecordingQueue {
    public static final int MAX_TILES = 1024;
    private final LinkedHashMap<Long, Tile> near = new LinkedHashMap<>(), older = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Completion> completed = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Deferred> deferred = new LinkedHashMap<>();
    private int turn;

    public void offer(int tileX, int tileZ, boolean urgent, long tick) {
        long key = ((long)tileX << 32) | (tileZ & 0xffffffffL);
        Completion last = completed.get(key);
        if (last != null && tick - last.attempt < 20) return;
        if (near.containsKey(key)) return;
        Tile present = older.get(key);
        if (present != null) {
            if (urgent) { older.remove(key); near.put(key,present); }
            return;
        }
        if (near.size()+older.size() >= MAX_TILES) return;
        boolean refresh=last != null && tick-last.refresh>=200;
        (urgent && !refresh ? near : older).put(key,new Tile(tileX,tileZ,refresh));
    }

    public Sample next(long tick) {
        turn++;
        if(!deferred.isEmpty() && (turn%8==0 || (near.isEmpty() && older.isEmpty()))) {
            var retries=deferred.entrySet().iterator();var first=retries.next();
            if(first.getValue().ready<=tick) {retries.remove();return first.getValue().sample;}
        }
        Map<Long,Tile> queue = !older.isEmpty() && (turn%4==0 || near.isEmpty()) ? older : near;
        if (queue.isEmpty()) return null;
        var iterator=queue.entrySet().iterator(); var entry=iterator.next(); Tile tile=entry.getValue();
        int at=tile.cursor++;
        var sample=new Sample(tile.x*8+(at&7),tile.z*8+(at>>>3),tile.refresh);
        if(tile.cursor==64) {
            iterator.remove();
            Completion previous=completed.get(entry.getKey());
            completed.put(entry.getKey(),new Completion(tick,tile.refresh || previous==null?tick:previous.refresh));
            while(completed.size()>MAX_TILES) { var first=completed.keySet().iterator(); first.next();first.remove(); }
        }
        return sample;
    }
    /** Retry budget-interrupted or temporarily unavailable cells without losing their world position. */
    public void defer(Sample sample,long tick) {
        defer(sample,tick,false);
    }
    public void defer(Sample sample,long tick,boolean budgetInterrupted) {
        long key=((long)sample.x<<32)|(sample.z&0xffffffffL);
        if(budgetInterrupted && deferred.size()>=MAX_TILES && !deferred.containsKey(key)) {
            var oldest=deferred.keySet().iterator();oldest.next();oldest.remove();
        }
        if(deferred.size()<MAX_TILES || deferred.containsKey(key)) deferred.put(key,new Deferred(sample,tick+1));
    }
    public int size() { return near.size()+older.size(); }
    public int deferredSize() { return deferred.size(); }
    public record Sample(int x,int z,boolean refresh) { }
    private record Completion(long attempt,long refresh) { }
    private record Deferred(Sample sample,long ready) { }
    private static final class Tile {
        final int x,z; final boolean refresh; int cursor;
        Tile(int x,int z,boolean refresh) { this.x=x;this.z=z;this.refresh=refresh; }
    }
}
