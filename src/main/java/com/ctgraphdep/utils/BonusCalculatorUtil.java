package com.ctgraphdep.utils;

import com.ctgraphdep.model.dto.bonus.BonusCalculationResultDTO;
import com.ctgraphdep.model.BonusConfiguration;
import org.springframework.stereotype.Component;

@Component
public class BonusCalculatorUtil {

    //Calculates the entries component of the bonus formula
    public double calculateEntriesComponent(int numberOfEntries, int workedDays, double entriesPercentage) {
        entriesPercentage = validatePercentageAndGetDefault(entriesPercentage);
        // Instead of throwing an error, return 0 for the component if no worked days
        if (workedDays <= 0) {
            LoggerUtil.debug(this.getClass(), "No worked days found, entries component will be 0");
            return 0.0;
        }
        return (((double) numberOfEntries / workedDays) * entriesPercentage);
    }


    //Calculates the articles component of the bonus formula
    public double calculateArticlesComponent(double sumArticleNumbers, int numberOfEntries, double articlesPercentage) {
        articlesPercentage = validatePercentageAndGetDefault(articlesPercentage);
        if (numberOfEntries <= 0) {
            LoggerUtil.error(this.getClass(),
                    String.format("Invalid number of entries: %d. Number of entries must be greater than 0", numberOfEntries));
            return 0.0;
        }
        return ((sumArticleNumbers / numberOfEntries) * articlesPercentage);
    }

    //Calculates the complexity component of the bonus formula
    public double calculateComplexityComponent(double sumComplexity, int numberOfEntries, double complexityPercentage) {
        complexityPercentage = validatePercentageAndGetDefault(complexityPercentage);
        if (numberOfEntries <= 0) {
            LoggerUtil.error(this.getClass(),
                    String.format("Invalid number of entries: %d. Number of entries must be greater than 0", numberOfEntries));
            return 0.0;
        }
        return ((sumComplexity / numberOfEntries) * complexityPercentage);
    }

    // Calculates the misc component of the bonus formula
    public double calculateMiscComponent(double miscValue, double miscPercentage) {
        miscPercentage = validatePercentageAndGetDefault(miscPercentage);
        return (miscValue * miscPercentage);
    }

    //Calculates the complete bonus result using configuration
    public BonusCalculationResultDTO calculateBonus(int numberOfEntries, int workedDays, double sumArticleNumbers,
                                                    double sumComplexity, BonusConfiguration config) {
        // Validate configuration
        if (config == null) {
            LoggerUtil.error(this.getClass(), "Bonus calculation failed: configuration is null");
            return createEmptyBonusResult(0, 0, 0, 0);
        }

        if (config.notValid()) {
            LoggerUtil.error(this.getClass(),
                    String.format("Invalid bonus configuration: percentages must sum to 1.0. Current sum: %.2f",
                            config.getEntriesPercentage() + config.getArticlesPercentage() +
                                    config.getComplexityPercentage() + config.getMiscPercentage()));

            // Return empty result with config's misc value if available
            return createEmptyBonusResult(numberOfEntries,
                    numberOfEntries > 0 ? sumArticleNumbers / numberOfEntries : 0,
                    numberOfEntries > 0 ? sumComplexity / numberOfEntries : 0,
                    config.getMiscValue());
        }

        // If no worked days, return a result with zeroed values but show the raw totals
        if (workedDays <= 0) {
            LoggerUtil.warn(this.getClass(),
                    String.format("Bonus calculation with zero worked days for %d entries", numberOfEntries));

            return createEmptyBonusResult(numberOfEntries,
                    numberOfEntries > 0 ? sumArticleNumbers / numberOfEntries : 0,
                    numberOfEntries > 0 ? sumComplexity / numberOfEntries : 0,
                    config.getMiscValue());
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
        return BonusCalculationResultDTO.builder()
                .entries(numberOfEntries)
                .articleNumbers(numberOfEntries > 0 ? sumArticleNumbers / numberOfEntries : 0)
                .graphicComplexity(numberOfEntries > 0 ? sumComplexity / numberOfEntries : 0)
                .misc(config.getMiscValue())
                .workedDays(workedDays)
                .workedPercentage(workedPercentage)
                .bonusPercentage(bonusPercentage)
                .bonusAmount(bonusAmount)
                .build();
    }

    // Helper method to create empty bonus result
    private BonusCalculationResultDTO createEmptyBonusResult(int entries, double articleNumbers,
                                                             double graphicComplexity, double miscValue) {
        return BonusCalculationResultDTO.builder()
                .entries(entries)
                .articleNumbers(articleNumbers)
                .graphicComplexity(graphicComplexity)
                .misc(miscValue)
                .workedDays(0)
                .workedPercentage(0.0)
                .bonusPercentage(0.0)
                .bonusAmount(0.0)
                .build();
    }

    private boolean isValidPercentage(double percentage) {
        return percentage >= 0 && percentage <= 1;
    }

    private double validatePercentageAndGetDefault(double percentage) {
        if (!isValidPercentage(percentage)) {
            LoggerUtil.error(this.getClass(),
                    String.format("Invalid percentage value: %f. Percentage must be between 0 and 1", percentage));
            return 0.0;
        }
        return percentage;
    }
}