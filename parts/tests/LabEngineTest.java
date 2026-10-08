package org.lineageos.settings.motionsense.lab;
public final class LabEngineTest {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value) throw new AssertionError("check "+checks); }
    public static void main(String[] args) {
        LabEngine e=new LabEngine(123);
        e.start(LabEngine.DODGE); e.swipe(-1);e.swipe(-1);check(e.lane==0);
        e.swipe(1);e.swipe(1);e.swipe(1);check(e.lane==2);
        e.swipe(0);check(e.lane==2);
        e.obstacleLane=e.lane;e.obstacleY=.91f;e.tick(.01f);check(!e.running && !e.complete);
        e.tap();check(e.running && e.score==0 && e.lane==1);
        e.obstacleLane=e.lane;e.obstacleY=.91f;e.tap();e.tick(.01f);check(e.running && e.shield>0);
        e.obstacleY=1.1f;e.tick(.01f);check(e.score==1 && e.obstacleY<0);
        float time=e.timeLeft;e.tick(-1);check(e.timeLeft==time);
        e.tick(100);check(Math.abs(time-e.timeLeft-.1f)<.001f);
        e.start(LabEngine.TARGETS);int target=e.targetSide;e.swipe(-target);check(e.score==0);
        e.swipe(target);check(e.score==1);
        e.timeLeft=.05f;e.tick(.1f);check(e.complete && !e.running);
        e.start(LabEngine.TRAINING);e.swipe(-1);e.tap();check(e.lesson==0);
        e.reach();check(e.lesson==1);e.swipe(1);check(e.lesson==1);
        e.swipe(-1);check(e.lesson==2);e.reach();check(e.lesson==2);
        e.swipe(1);check(e.lesson==3);e.tap();check(e.complete && e.lesson==4 && !e.running);
        check(LabEngine.cycle(0,-1,6)==5 && LabEngine.cycle(5,1,6)==0);
        check(LabEngine.cycle(0,1,0)==0);
        check(LabEngine.physicalSide(1)==1 && LabEngine.physicalSide(2)==1 && LabEngine.physicalSide(8)==1);
        check(LabEngine.physicalSide(4)==-1 && LabEngine.physicalSide(5)==-1 && LabEngine.physicalSide(6)==-1);
        check(LabEngine.physicalSide(0)==0 && LabEngine.physicalSide(3)==0 && LabEngine.physicalSide(7)==0);
        System.out.println("Motion Lab engine: "+checks+" checks passed");
    }
}
