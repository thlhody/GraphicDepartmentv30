package com.ctgraphdep.utils;

import com.ctgraphdep.model.BonusCalculationResult;
import com.ctgraphdep.model.BonusConfiguration;
import org.springframework.stereotype.Component;

@Component
public class BonusCalculatorUtil {

     //Calculates the entries component of the bonus formula
     public double calculateEntriesComponent(int numberOfEntries, int workedDays, double entriesPercentage) {
         validatePercentage(entriesPercentage);
         // Instead of throwing an error, return 0 for the component if no worked days
         if (workedDays <= 0) {
             LoggerUtil.warn(this.getClass(), "No worked days found, entries component will be 0");
             return 0.0;
         }
         return (((double) numberOfEntries / workedDays) * entriesPercentage);
     }

    //Calculates the articles component of the bonus formula
    public double calculateArticlesComponent(double sumArticleNumbers, int numberOfEntries, double articlesPercentage) {
        validatePercentage(articlesPercentage);
        if (numberOfEntries <= 0) {
            throw new IllegalArgumentException("Number of entries must be greater than 0");
        }
        return ((sumArticleNumbers / numberOfEntries) * articlesPercentage);
    }

    //Calculates the complexity component of the bonus formula
    public double calculateComplexityComponent(double sumComplexity, int numberOfEntries, double complexityPercentage) {
        validatePercentage(complexityPercentage);
        if (numberOfEntries <= 0) {
            throw new IllegalArgumentException("Number of entries must be greater than 0");
        }
        return ((sumComplexity / numberOfEntries) * complexityPercentage);
    }

    // Calculates the misc component of the bonus formula
    public double calculateMiscComponent(double miscValue, double miscPercentage) {
        validatePercentage(miscPercentage);
        return (miscValue * miscPercentage);
    }

    //Calculates the complete bonus result using configuration
    public BonusCalculationResult calculateBonus(int numberOfEntries, int workedDays, double sumArticleNumbers,
                                                 double sumComplexity, BonusConfiguration config) {
        // Validate configuration
        if (config.notValid()) {
            throw new IllegalArgumentException("Invalid bonus configuration: percentages must sum to 1.0");
        }

        // If no worked days, return a result with zeroed values but show the raw totals
        if (workedDays <= 0) {
            return BonusCalculationResult.builder()
                    .entries(numberOfEntries)
                    .articleNumbers(numberOfEntries > 0 ? sumArticleNumbers / numberOfEntries : 0)
                    .graphicComplexity(numberOfEntries > 0 ? sumComplexity / numberOfEntries : 0)
                    .misc(config.getMiscValue())
                    .workedDays(0)
                    .workedPercentage(0.0)
                    .bonusPercentage(0.0)
                    .bonusAmount(0.0)
                    .build();
        }

        // Calculate individual components
        double entriesResult = calculateEntriesComponent(numberOfEntries, workedDays, config.getEntriesPercentage());
        double articlesResult = calculateArticlesComponent(sumArticleNumbers, numberOfEntries, config.getArticlesPercentage());
        double complexityResult = calculateComplexityComponent(sumComplexity, numberOfEntries, config.getComplexityPercentage());
        double miscResult = calculateMiscComponent(config.getMiscValue(), config.getMiscPercentage());

        // Calculate total worked percentage
        double workedPercentage = entriesResult + articlesResult + complexityResult + miscResult;

        // Calculate bonus percentage (difference from norm)
        double bonusPercentage = workedPercentage - config.getNormValue();

        // Calculate final bonus amount
        double bonusAmount = bonusPercentage * config.getSumValue();

        // Build and return result
        return BonusCalculationResult.builder()
                .entries(numberOfEntries)
                .articleNumbers(sumArticleNumbers / numberOfEntries)
                .graphicComplexity(sumComplexity / numberOfEntries)
                .misc(config.getMiscValue())
                .workedDays(workedDays)
                .workedPercentage(workedPercentage)
                .bonusPercentage(bonusPercentage)
                .bonusAmount(bonusAmount)
                .build();
    }

    private void validatePercentage(double percentage) {
        if (percentage < 0 || percentage > 1) {
            throw new IllegalArgumentException("Percentage must be between 0 and 1");
        }
    }
}