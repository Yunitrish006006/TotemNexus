package dev.totem.nexus.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapRecordingQueueTest {
    @Test void movementKeepsIncompleteWorldCoordinates() {
        var q=new MapRecordingQueue();q.offer(-1,3,true,0);
        assertEquals(new MapRecordingQueue.Sample(-8,24,false),q.next(0));
        q.offer(100,100,true,1);
        for(int n=1;n<64;n++) assertEquals(new MapRecordingQueue.Sample(-8+(n&7),24+(n>>>3),false),q.next(1));
        assertEquals(800,q.next(1).x());
    }
    @Test void distantWorkReceivesOneInFourCandidatesAndQueuesAreBounded() {
        var q=new MapRecordingQueue();q.offer(0,0,true,0);q.offer(99,99,false,0);
        for(int n=0;n<3;n++) assertTrue(q.next(0).x()<8);
        assertEquals(792,q.next(0).x());
        for(int n=0;n<3000;n++) q.offer(n,n,true,0);
        assertEquals(MapRecordingQueue.MAX_TILES,q.size());
    }
    @Test void repeatOffersDoNotResetProgressOrPostponeRefreshForever() {
        var q=new MapRecordingQueue();q.offer(0,0,true,0);
        for(int n=0;n<64;n++) {q.offer(0,0,true,0);assertFalse(q.next(0).refresh());}
        q.offer(0,0,true,19);assertNull(q.next(19));
        q.offer(0,0,true,200);assertTrue(q.next(200).refresh());
    }
    @Test void interruptedLastCellSurvivesMovementAndQueueCompletion() {
        var q=new MapRecordingQueue();q.offer(-1,-1,true,0);
        MapRecordingQueue.Sample last=null;for(int n=0;n<64;n++) last=q.next(0);
        q.defer(last,0);assertNull(q.next(0));assertEquals(last,q.next(1));
        for(int n=0;n<3000;n++) q.defer(new MapRecordingQueue.Sample(n,n,false),1);
        assertEquals(MapRecordingQueue.MAX_TILES,q.deferredSize());
    }
    @Test void knownRevisitGetsItsOwnCooldownWithoutResettingRefreshAge() {
        var q=new MapRecordingQueue();q.offer(0,0,true,0);for(int n=0;n<64;n++) q.next(0);
        q.offer(0,0,true,20);for(int n=0;n<64;n++) q.next(20);
        q.offer(0,0,true,21);assertNull(q.next(21));
        q.offer(0,0,true,200);assertTrue(q.next(200).refresh());
    }
}
