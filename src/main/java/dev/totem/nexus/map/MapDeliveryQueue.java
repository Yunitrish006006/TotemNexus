package dev.totem.nexus.map;

import java.util.*;
import java.util.function.ToIntFunction;

/** Keep unfinished page order when a repeated request supplies newer revisions. */
public final class MapDeliveryQueue {
    private MapDeliveryQueue() { }
    public static <T> List<T> merge(Collection<T> pending,Collection<T> requested,ToIntFunction<T> id) {
        LinkedHashMap<Integer,T> remaining=new LinkedHashMap<>();
        for(T value:requested) remaining.put(id.applyAsInt(value),value);
        List<T> result=new ArrayList<>();
        for(T value:pending) { T current=remaining.remove(id.applyAsInt(value));if(current!=null) result.add(current); }
        result.addAll(remaining.values());return result;
    }
}
