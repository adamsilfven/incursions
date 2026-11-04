package dev.incursions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class RulesTest {
    static class Fixed extends Random {
        double value;Fixed(double n){value=n;} @Override public double nextDouble(){return value;}
    }
    static class World implements Rules.World {
        int alive, completed, failed; double scale=1; boolean allow=true;
        List<Rules.Kind> history=new ArrayList<>();
        public boolean spawn(Rules.Kind kind) {if(!allow)return false;history.add(kind);alive++;return true;}
        public int requiredAlive(){return alive;}public double scaling(){return scale;}
        public void completed(){completed++;}public void failed(String reason){failed++;}
    }
    Rules.Schedule schedule(int waves,int seconds,boolean normal){return new Rules.Schedule(waves,seconds,200,300,.2,.55,normal,false,false);}
    @Test void probabilityBoundaries(){assertEquals(0,Rules.ordinary(.199999,.2,.55));assertEquals(1,Rules.ordinary(.2,.2,.55));assertEquals(1,Rules.ordinary(.74999,.2,.55));assertEquals(2,Rules.ordinary(.75,.2,.55));}
    @Test void millionRollsMatchDistribution(){Random r=new Random(991);int[] n=new int[3];for(int i=0;i<1000000;i++)n[Rules.ordinary(r.nextDouble(),.2,.55)]++;assertEquals(.2,n[0]/1e6,.002);assertEquals(.55,n[1]/1e6,.002);assertEquals(.25,n[2]/1e6,.002);assertEquals(1.05,(n[1]+n[2]*2)/1e6,.003);}
    @Test void emptyProbabilitiesAreExact(){assertEquals(1d/625,Math.pow(.2,4),1e-15);assertEquals(1d/78125,Math.pow(.2,7),1e-15);assertEquals(1d/244140625,Math.pow(.2,12),1e-20);}
    @Test void emptyWindowsMonteCarlo(){Random r=new Random(7);int zero=0;for(int i=0;i<1000000;i++){int count=0;for(int t=0;t<4;t++)count+=Rules.ordinary(r.nextDouble(),.2,.55);if(count==0)zero++;}assertEquals(1600,zero,180);}
    @Test void playerMappingAndCap(){for(int n=1;n<=5;n++)assertEquals(new double[]{1,1.35,1.7,2.05,2.4}[n-1],Rules.multiplier(n),1e-10);assertEquals(2.4,Rules.multiplier(99),1e-10);}
    @Test void stochasticRoundingPreservesExpectation(){Random r=new Random(1);long sum=0;for(int i=0;i<1000000;i++)sum+=Rules.scaled(1,1.35,r);assertEquals(1.35,sum/1e6,.002);}
    @Test void zeroRollNeverScalesIntoSpawn(){for(int i=0;i<100;i++)assertEquals(0,Rules.scaled(0,2.4,new Random(i)));}
    @Test void independentWeightedSampling(){record Entry(int id,double w){}var pool=List.of(new Entry(1,45),new Entry(2,35),new Entry(3,20));int[] n=new int[4];Random rng=new Random(2);for(int i=0;i<500000;i++)n[Rules.weighted(pool,Entry::w,rng).id()]++;assertEquals(.45,n[1]/500000d,.003);assertEquals(.35,n[2]/500000d,.003);assertEquals(.2,n[3]/500000d,.003);}
    @Test void noArtificialCreatureCaps(){var pool=List.of("Zephyr");for(int i=0;i<14;i++)assertEquals("Zephyr",Rules.weighted(pool,x->1,new Random(i)));}
    @Test void netherFirstShortCircuits(){var options=List.of(new Rules.Lottery<>("nether",1,true),new Rules.Lottery<>("other",1,false));assertEquals("nether",Rules.choose(options,new Random(0)));}
    @Test void netherFailureDoesNotPreventOtherRolls(){assertEquals("aether",Rules.choose(List.of(new Rules.Lottery<>("nether",0,true),new Rules.Lottery<>("aether",1,false)),new Random(0)));}
    @Test void missingProfilesHaveNoPenalty(){assertEquals("twilight",Rules.choose(List.of(new Rules.Lottery<>("twilight",1,false)),new Random(0)));assertNull(Rules.choose(List.of(),new Random()));}
    @Test void multipleSuccessesChosenUniformly(){Random r=new Random(5);int count=0;for(int i=0;i<100000;i++)if(Rules.choose(List.of(new Rules.Lottery<>("a",1,false),new Rules.Lottery<>("b",1,false)),r).equals("a"))count++;assertEquals(50000,count,800);}
    @Test void emptyWindowWaitsUntilFifteenSeconds(){World w=new World();var f=new Rules.Fight(schedule(1,4,false),new Fixed(0),0,w);f.tick(80,w);assertEquals(0,w.completed);assertEquals(2,f.pendingCount());f.tick(299,w);assertTrue(w.history.isEmpty());f.tick(300,w);assertEquals(2,w.alive);w.alive=0;f.tick(301,w);assertEquals(1,w.completed);}
    @Test void normalEliteBlocksVictory(){World w=new World();var f=new Rules.Fight(schedule(1,7,true),new Fixed(.3),0,w);f.tick(140,w);w.alive=0;f.tick(299,w);assertEquals(0,w.completed);f.tick(300,w);assertEquals(1,w.alive);assertEquals(Rules.Kind.ENHANCED,w.history.getLast());}
    @Test void allThreeEmptyWavesProduceSixEnhanced(){World w=new World();var f=new Rules.Fight(schedule(3,4,true),new Fixed(0),0,w);for(long t=0;t<2000;t++){w.alive=0;f.tick(t,w);}assertEquals(6,w.history.size());assertTrue(w.history.stream().allMatch(k->k==Rules.Kind.ENHANCED));assertEquals(1,w.completed);}
    @Test void earlierPairDoesNotSuppressLaterNormalElite(){World w=new World();Fixed rng=new Fixed(0);var f=new Rules.Fight(schedule(2,4,true),rng,0,w);f.tick(300,w);w.alive=0;f.tick(301,w);rng.value=.3;f.tick(501,w);f.tick(801,w);assertEquals(3,w.history.stream().filter(k->k==Rules.Kind.ENHANCED).count());}
    @Test void finalEmptyWaveReplacesNormalElite(){World w=new World();var f=new Rules.Fight(schedule(1,7,true),new Fixed(0),0,w);assertEquals(2,f.pendingCount());f.tick(300,w);assertEquals(2,w.history.size());}
    @Test void interwaveGapStartsAfterKill(){World w=new World();var f=new Rules.Fight(schedule(2,4,false),new Fixed(.3),0,w);f.tick(80,w);f.tick(500,w);assertEquals(1,f.wave);w.alive=0;f.tick(501,w);assertEquals(701,f.nextWave);f.tick(700,w);assertEquals(1,f.wave);f.tick(701,w);assertEquals(2,f.wave);}
    @Test void requiredArrivalFailsAfterThirtySeconds(){World w=new World();w.allow=false;var f=new Rules.Fight(schedule(1,4,false),new Fixed(0),0,w);f.tick(899,w);assertEquals(0,w.failed);f.tick(900,w);assertEquals(1,w.failed);assertTrue(f.ended);assertEquals(0,w.completed);}
    @Test void failedOrdinaryPlacementDoesNotManufactureElites(){World w=new World();w.allow=false;var f=new Rules.Fight(schedule(1,4,false),new Fixed(.3),0,w);f.tick(80,w);assertEquals(0,f.pendingCount());assertEquals(1,w.completed);assertEquals(0,w.failed);}
    @Test void noVictoryBeforeWindowEnds(){World w=new World();var f=new Rules.Fight(schedule(1,4,false),new Fixed(.3),0,w);f.tick(60,w);w.alive=0;f.tick(79,w);assertEquals(0,w.completed);f.tick(80,w);assertEquals(1,w.completed);}
    @Test void failureCancelsPendingAndCannotPay(){World w=new World();var f=new Rules.Fight(schedule(1,4,true),new Fixed(0),0,w);f.fail(w,"shutdown");f.tick(10000,w);f.fail(w,"twice");assertEquals(1,w.failed);assertEquals(0,w.completed);assertTrue(w.history.isEmpty());assertEquals(0,f.pendingCount());}
    @Test void victoryPayoutExactlyOnce(){World w=new World();var f=new Rules.Fight(schedule(1,4,false),new Fixed(.3),0,w);f.tick(80,w);w.alive=0;for(int t=81;t<500;t++)f.tick(t,w);assertEquals(1,w.completed);}
    @Test void rareEnhancedReplacesNormalButNotExceptionalPair(){World w=new World();var s=new Rules.Schedule(1,4,200,300,.2,.55,true,true,true);var f=new Rules.Fight(s,new Fixed(.3),0,w);assertEquals(1,f.arrivals().stream().filter(a->a.kind()==Rules.Kind.RARE_ENHANCED).count());assertEquals(0,f.arrivals().stream().filter(a->a.kind()==Rules.Kind.ENHANCED).count());var zero=new Rules.Fight(s,new Fixed(0),0,new World());assertEquals(2,zero.arrivals().stream().filter(a->a.kind()==Rules.Kind.ENHANCED).count());assertEquals(1,zero.arrivals().stream().filter(a->a.kind()==Rules.Kind.RARE_ENHANCED).count());}
    @Test void normalRareOccursOnlyOnce(){World w=new World();var s=new Rules.Schedule(3,4,200,300,.2,.55,false,true,false);var f=new Rules.Fight(s,new Fixed(0),0,w);for(int t=0;t<2000;t++){w.alive=0;f.tick(t,w);}assertEquals(1,w.history.stream().filter(k->k==Rules.Kind.RARE).count());}
    @Test void windowScalingIsSnapshotted(){World w=new World();w.scale=1;var f=new Rules.Fight(schedule(1,4,false),new Fixed(.3),0,w);w.scale=2.4;f.tick(80,w);assertEquals(4,w.history.size());}

    @Test void allFourProfileLotteryMatchesApprovedDistribution() {
        var choices=List.of(new Rules.Lottery<>("nether",.02,true),new Rules.Lottery<>("aether",.005,false),new Rules.Lottery<>("undergarden",.01,false),new Rules.Lottery<>("twilight",.005,false));
        Map<String,Integer> counts=new HashMap<>();var rng=new Random(727);
        for(int i=0;i<2000000;i++){String selected=Rules.choose(choices,rng);if(selected!=null)counts.merge(selected,1,Integer::sum);}
        int total=counts.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(.020000,counts.get("nether")/2000000d,.0004);
        assertEquals(.00486333,counts.get("aether")/2000000d,.0002);
        assertEquals(.00975108,counts.get("undergarden")/2000000d,.0003);
        assertEquals(.00486333,counts.get("twilight")/2000000d,.0002);
        System.out.println("Approved lottery over 2M scans: "+counts+"; encounters="+total);
    }
    @Test void twoAndThreeWaveEncountersCompleteOnlyTheirChosenTotal() {
        for(int waves:List.of(2,3)) {
            World w=new World();var f=new Rules.Fight(schedule(waves,4,true),new Fixed(.3),0,w);
            for(int tick=0;tick<2000;tick++) {w.alive=0;f.tick(tick,w);}
            assertEquals(waves,f.wave);assertEquals(1,w.completed);assertEquals(1,w.history.stream().filter(k->k==Rules.Kind.ENHANCED).count());
        }
    }

    @Test void twoSecondSpacingPreservesFourRollsAndEightSecondWindow() {
        World w=new World();var f=new Rules.Fight(new Rules.Schedule(1,8,200,600,.2,.55,false,false,false,40),new Fixed(.3),0,w);
        assertEquals(List.of(0L,40L,80L,120L),f.arrivals().stream().map(Rules.Arrival::due).toList());
        f.tick(0,w);assertEquals(1,w.history.size());f.tick(39,w);assertEquals(1,w.history.size());f.tick(40,w);assertEquals(2,w.history.size());
        f.tick(120,w);w.alive=0;f.tick(159,w);assertEquals(0,w.completed);f.tick(160,w);assertEquals(1,w.completed);
    }
    @Test void fourteenSecondWindowHasSevenRollsAndWaitsForThirtySecondElite() {
        World w=new World();var f=new Rules.Fight(new Rules.Schedule(1,14,200,600,.2,.55,true,false,false,40),new Fixed(.3),0,w);
        assertEquals(List.of(0L,40L,80L,120L,160L,200L,240L),f.arrivals().stream().filter(a->a.kind()==Rules.Kind.ORDINARY).map(Rules.Arrival::due).toList());
        f.tick(280,w);w.alive=0;f.tick(599,w);assertEquals(0,w.completed);f.tick(600,w);assertEquals(Rules.Kind.ENHANCED,w.history.getLast());assertEquals(0,w.completed);w.alive=0;f.tick(601,w);assertEquals(1,w.completed);
    }
    @Test void slowerThreeEmptyWavesStillProduceSixEnhanced() {
        World w=new World();var f=new Rules.Fight(new Rules.Schedule(3,8,200,600,.2,.55,true,false,false,40),new Fixed(0),0,w);
        for(int t=0;t<3000;t++){w.alive=0;f.tick(t,w);}
        assertEquals(6,w.history.size());assertTrue(w.history.stream().allMatch(k->k==Rules.Kind.ENHANCED));assertEquals(1,w.completed);
    }
    @Test void nonEnhancedRareUsesLastRollOfSlowerWindow() {
        World w=new World();var f=new Rules.Fight(new Rules.Schedule(1,8,200,600,.2,.55,false,true,false,40),new Fixed(.3),0,w);
        assertEquals(120,f.arrivals().stream().filter(a->a.kind()==Rules.Kind.RARE).findFirst().orElseThrow().due());
    }

    @Test void exceptionalPairsCarrySeparateSoundProvenance() {
        World w=new World();var empty=new Rules.Fight(new Rules.Schedule(1,8,200,600,.2,.55,true,true,true,40),new Fixed(0),0,w);
        assertEquals(2,empty.arrivals().stream().filter(Rules.Arrival::zeroPair).count());
        assertTrue(empty.arrivals().stream().filter(a->a.kind()==Rules.Kind.RARE_ENHANCED).noneMatch(Rules.Arrival::zeroPair));
        var normal=new Rules.Fight(new Rules.Schedule(1,8,200,600,.2,.55,true,false,false,40),new Fixed(.3),0,w);
        assertTrue(normal.arrivals().stream().noneMatch(Rules.Arrival::zeroPair));
    }
}
