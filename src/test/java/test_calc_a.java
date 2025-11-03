/*
public class test_calc {
    public static void main(String[] args) {
        int inputMinutes = 487;
        int schedule = 8;
        int HOUR_DURATION = 60;
        int HALF_HOUR_DURATION = 30;
        int INTERVAL_HOURS_A = 4;
        int INTERVAL_HOURS_B = 11;
        int INTERVAL_HOURS_C = 8;

        int hours = inputMinutes / HOUR_DURATION;
        System.out.println("hours = " + hours);

        boolean shouldDeduct = (schedule == INTERVAL_HOURS_C) && (hours > INTERVAL_HOURS_A && hours <= INTERVAL_HOURS_B);
        System.out.println("shouldDeduct = " + shouldDeduct);
        System.out.println("  schedule == INTERVAL_HOURS_C: " + (schedule == INTERVAL_HOURS_C));
        System.out.println("  hours > INTERVAL_HOURS_A: " + (hours > INTERVAL_HOURS_A));
        System.out.println("  hours <= INTERVAL_HOURS_B: " + (hours <= INTERVAL_HOURS_B));

        int adjusted = shouldDeduct ? (inputMinutes - HALF_HOUR_DURATION) : inputMinutes;
        System.out.println("adjusted = " + adjusted);

        int scheduleMinutes = schedule * 60;
        int missingMinutes = scheduleMinutes - adjusted;
        System.out.println("missingMinutes = " + missingMinutes);

        int missingHours = (int) Math.ceil(missingMinutes / 60.0);
        System.out.println("missingHours = " + missingHours);
    }
}
*/

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

/*public class test_calc {
    public static void main(String[] var0) {
        short var1 = 487;
        byte var2 = 8;
        byte var3 = 60;
        byte var4 = 30;
        byte var5 = 4;
        byte var6 = 11;
        byte var7 = 8;
        int var8 = var1 / var3;
        System.out.println("hours = " + var8);
        boolean var9 = var2 == var7 && var8 > var5 && var8 <= var6;
        System.out.println("shouldDeduct = " + var9);
        System.out.println("  schedule == INTERVAL_HOURS_C: " + (var2 == var7));
        System.out.println("  hours > INTERVAL_HOURS_A: " + (var8 > var5));
        System.out.println("  hours <= INTERVAL_HOURS_B: " + (var8 <= var6));
        int var10 = var9 ? var1 - var4 : var1;
        System.out.println("adjusted = " + var10);
        int var11 = var2 * 60;
        int var12 = var11 - var10;
        System.out.println("missingMinutes = " + var12);
        int var13 = (int)Math.ceil((double)var12 / (double)60.0F);
        System.out.println("missingHours = " + var13);
    }
}*/
