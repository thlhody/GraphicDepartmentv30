**Date:** 2025-11-03
**Status:** ✅ Complete

###### LEGEND #######

    public static final String NATIONAL_HOLIDAY_CODE = "SN"; // National holiday only admin can create these
    public static final String TIME_OFF_CODE = "CO";         // Normal holiday → Field 1: Concediu de odihnă
    public static final String MEDICAL_LEAVE_CODE = "CM";
    public static final String WEEKEND_CODE = "W";
    public static final String RECOVERY_LEAVE_CODE = "CR";   // Concediu Recuperare - paid from overtime
    public static final String UNPAID_LEAVE_CODE = "CN";     // Concediu Neplatit - unpaid leave
    public static final String DELEGATION_CODE = "D";        // Normal work day but away from office
    public static final String SHORT_DAY_CODE = "ZS";        // Zi Scurta - short day filled from overtime
    public static final String SPECIAL_EVENT_CODE = "CE";    // Special day event marriage/death/birth

### Lets discuss about the new CR/CN/D/ZS/CE we need to handle correctly this logic for the new codes 

Auto selects functions/ time off types list and logic:
 * - CO → Field 1: Concediu de odihnă - user can work - overtime only
 * - CM → Field 2: Concediu pentru evenimente speciale(this is not any of the fields) this requiers proof from doctor that is in place of the form soo form is not needed - user can work - overtime only
 * - CE → Field 2: Concediu pentru evenimente speciale - user can work overtime only same logic as CO/CM/W/
 * - CR → Field 3: Concediu fara plata / Invoire + cu recuperare - users can not work on this timeoff types
 * - CN → Field 3: Concediu fara plata / Invoire + fara recuperare - users can not work on this timeoff types
 * - D  → No field requiered this is a normal work day with no time take just a special form written in advanced should be like SN -- users can not work on this timeoff types
 * - W  → No field requiered this is weekend user can work this gets created when user work on any weekend all hours are overtime

# General Scenario
Scenario:
Time Totals
Regular Time: 124:00
Overtime: 32:00
Total Time: 156:00

# CO/CM/SN/W - are perfect as they are
For these CO/CM/SN/W - users can have special day where they can work on Normal Holiday (CO), National Holiday (SN), Medical Leave(CM), Weekend(W) this logic works correctly

User can remove them they can add work time to them all ok i will reference them throughout the newly implemented days.

# We discuss of ZS(short day):
pressed start at: 17:41 pressed end at 20:15 - worked 2hours and 33 minute (raw) it rounds down for actual work 2 hours and discarded minutes = 32minutes
schedule = 8 hours
normal week day (Monday)

What happened:
in worktable for line Oct 27: timeOff type displays: ZS 0h/8h starttime correct/endtime correct rawtime correct - lunch missing(because under 4 hours should display automatically checked (logic in CalculateWorkHoursUtil/CalculationService)) worktime displays - , discarded - , and UI - correct
What should have happened:
in worktable for line Oct 27: timeOff types displays ZS-6(meaning 6 hours missing oppsite of the CO6), starttime correct/endtime correct/rawtime correct, lunch check green same as completed day, worktime 8h (red), discarded 2 minutes,and UI - correct
let me explain individual columns:

timeOff types displays ZS-6. Logic:(schedule/60+30minutes(lunch))(8hours/60+30minutes=480minutes+30minutes=510minutes) - takes raw time(153minutes) = 510-153 =357 minutes needed from Overtime 357/60 =5.95 rounding up = 6hours 
now we have the hours needed the display initial overtime shows 32 hours that should get updated 32hours - 6 hours = 26hours
and done 

# We discuss of CR(recovered leave):
What happened:
user scrolls down to the Request Time Off (Time Off Request Form), select single day, select time off type Recovery Leave (CR), select date Oct 28 presses Add Time Off 
modal appear with all data filled inlcuding 1. Concediu de odihna function selectHolidayType(type) - selects odihna and the users needs to scroll to the number three unpaid leave <span class="checkbox-label">3. Concediu fără plată / Învoire</span>
and selects manual <span class="recovery-label">cu recuperare</span> 
this error appears :time-management-core.js?v=101120251023:97 ⚠️ TimeInputModule not found - time inputs may not work properly
worktime table displays CR correct / start time 7:00 correct / end time 15:30 correct / raw time 8hours 30minutes / lunch field - / actual time - / adminSync UE(User edited) 

What should of happen:
user selects CR / date Oct 28 presses add time off the form opens automatically selected number 3 <span class="checkbox-label">3. Concediu fără plată / Învoire</span> and the <span class="recovery-label">cu recuperare</span>  should be selected and exports jpg/png
worktime table displays CR correct / start time 7:00 correct / end time 15:30 correct / raw time 8hours 30minutes / lunch field green check (like rest) / actual time 8hours / adminSync UI (user input)
and then for display
Overtime = 32 hours if CR take that 32hours -8 hours(CR recovered hours from overtime) = 24hours left in overtime for display

# We discuss of CN(unpaid leave):
almost same scenario as CR but no overtime deduction no hours added its like a SN but the HR will now it is an upaid leave.

# We discuss of CE (event paid leave)
this is like medical leave when user selects this it selects automaticaly (like CR/CN) but it selects number 2. Concediu pentru evenimente speciale *** (field 2 )
and here there are multiple options(user needs extra proofs of the which one of them) so here just select the number 2 and the form has a input field for the reason
and done no deduction no other logic users can work on these days just like CM/CO

### Conclusion:

if we press the Holiday Request button it automatically selects the correct field in holiday request modal we need to make sure the Add Time Off button does the same
we need to make sure the Overtime Calculation works everytime correctly.
consider the file/cache as correct information, we add all overtime from all days = total monthly overtime
we add all the CR days(that are already filled) = total CR schedule*8 = total CR time
we add all ZS-hours (that are already filled) = total raw time - schedule = missing minutes- round up to hours = total ZS hours needed
total overtime-total CR - total ZS = remaining overtime that we display

for ZS this happens when the user press the end button end command (is working)

for CR is easy how to reduce the time needed is always schedule so no fancy things
for ZS we have the number ZS-hours(hours the user needs)
but on the final draft the admin needs to view the ZS6/2(6 is the hours needed 2 is the hours worked)
when admin displays that particular day ZS6/2

for ZS we will need to move hours around and edit time instead of that we will need a logic user/admin both should have similar logic
lets have a fool proof logic my ideea:
user has 32 hours overtime that the display calculates based on all worked days this works for admin/user
now ZS-(hours) has the data in the name so 
we work on 4 totals first we take the totalWorkedMinutes = all the time a user worked in a day this goes through CalculateWorkHoursUtile and it spits out (WorkTimeCalculationResultDTO):schedule(processedMinutes)/overtime(overtimeMinutes)
we take that value for each and start adding them up total of each including ZS now we have total of each in paralel we make a total of ZS missing time from ZS-(hour) and a total from CR(schedule+lunch(510(for this scenario this is tied with schedule and depends on that)))
we have 

schedule(processedMinutes) - total - remains as is
overtime(overtimeMinutes) - total - this sum(ZS-(hour)total+CRtotal)
ZS-(hour)   - total
CR          - total

and then results for total final for overtime = (total overtime-sum(ZS-(hour)total+CRtotal))
and this should be sent to display even for admin and for user
we need to think the ZS better