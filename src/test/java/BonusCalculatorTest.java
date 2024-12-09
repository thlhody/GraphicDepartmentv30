

import com.ctgraphdep.model.BonusCalculationResult;
import com.ctgraphdep.model.BonusConfiguration;
import com.ctgraphdep.utils.BonusCalculatorUtil;
import org.junit.jupiter.api.Test;

class BonusCalculatorTest {

    @Test
    void testBonusCalculation() {
        // Given
        BonusCalculatorUtil calculator = new BonusCalculatorUtil();
        BonusConfiguration config = BonusConfiguration.getDefaultConfig();

        // Input values
        int numberOfEntries = 176;
        int workedDays = 23;
        double sumArticleNumbers = 356;
        double sumComplexity =395.5;

        // When
        BonusCalculationResult result = calculator.calculateBonus(
                numberOfEntries,
                workedDays,
                sumArticleNumbers,
                sumComplexity,
                config
        );

        // Then
        System.out.println("Bonus Calculation Results:");
        System.out.println("-------------------------");
        System.out.println("Entries: " + result.getEntries());
        System.out.println("Article Numbers (avg): " + result.getArticleNumbers());
        System.out.println("Graphic Complexity (avg): " + result.getGraphicComplexity());
        System.out.println("Misc: " + result.getMisc());
        System.out.println("Worked Days: " + result.getWorkedDays());
        System.out.println("Worked Percentage: " + result.getWorkedPercentage());
        System.out.println("Bonus Percentage: " + result.getBonusPercentage());
        System.out.println("Bonus Amount: " + result.getBonusAmount());
    }
}