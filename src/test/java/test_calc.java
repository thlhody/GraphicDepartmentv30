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
