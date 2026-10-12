package org.lineageos.settings.motionsense.lab;
public final class LabEngineTest {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value) throw new AssertionError("check "+checks); }
    public static void main(String[] args) {
        LabEngine e=new LabEngine();
        e.start();e.swipe(-1);e.tap();check(e.lesson==0);
        e.reach();check(e.lesson==1);e.swipe(1);check(e.lesson==1);
        e.swipe(-1);check(e.lesson==2);e.reach();check(e.lesson==2);
        e.swipe(1);check(e.lesson==3);e.tap();check(e.complete && e.lesson==4 && !e.running);
        check(LabEngine.physicalSide(1)==1 && LabEngine.physicalSide(2)==1 && LabEngine.physicalSide(8)==1);
        check(LabEngine.physicalSide(4)==-1 && LabEngine.physicalSide(5)==-1 && LabEngine.physicalSide(6)==-1);
        check(LabEngine.physicalSide(0)==0 && LabEngine.physicalSide(3)==0 && LabEngine.physicalSide(7)==0);
        System.out.println("Motion Lab engine: "+checks+" checks passed");
    }
}

