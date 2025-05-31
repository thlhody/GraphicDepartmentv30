package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.fileOperations.core.*;
import com.ctgraphdep.fileOperations.model.FileTransactionResult;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing file transactions.
 * Provides methods for creating, committing, and rolling back file transactions.
 */
@Service
public class FileTransactionManager {
    private final ThreadLocal<FileTransaction> activeTransaction = new ThreadLocal<>();
    private final Map<String, FileTransaction> transactions = new ConcurrentHashMap<>();

    public FileTransactionManager() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Begin a new file transaction
     * @return The new transaction
     */
    public FileTransaction beginTransaction() {
        FileTransaction transaction = new FileTransaction();
        activeTransaction.set(transaction);
        transactions.put(transaction.getTransactionId(), transaction);
        LoggerUtil.debug(this.getClass(), "Created new transaction: " + transaction.getTransactionId());
        return transaction;
    }

    /**
     * Get the current transaction for this thread, or create a new one if none exists
     * @return The current transaction
     */
    public FileTransaction getCurrentTransaction() {
        FileTransaction transaction = activeTransaction.get();
        if (transaction == null || !transaction.isActive()) {
            transaction = beginTransaction();
        }
        return transaction;
    }

    /**
     * Commit the current transaction
     * @return The transaction result
     */
    public FileTransactionResult commitTransaction() {
        FileTransaction transaction = activeTransaction.get();
        if (transaction == null) {
            LoggerUtil.warn(this.getClass(), "No active transaction to commit");
            return FileTransactionResult.failure("NONE", "No active transaction");
        }

        try {
            LoggerUtil.info(this.getClass(), "Committing transaction " + transaction.getTransactionId() + " with " + transaction.getOperations().size() + " operations");
            FileTransactionResult result = transaction.commit();
            LoggerUtil.info(this.getClass(), "Transaction " + transaction.getTransactionId() + " committed with result: " + (result.isSuccess() ? "SUCCESS" : "FAILURE"));
            transactions.remove(transaction.getTransactionId());
            activeTransaction.remove();
            return result;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error committing transaction: " + e.getMessage(), e);
            try {
                transaction.rollback();
            } catch (Exception re) {
                LoggerUtil.error(this.getClass(), "Error rolling back transaction: " + re.getMessage(), re);
            }
            transactions.remove(transaction.getTransactionId());
            activeTransaction.remove();
            return FileTransactionResult.failure(transaction.getTransactionId(), "Exception during commit: " + e.getMessage());
        }
    }

    /**
     * Roll back the current transaction
     * @return The transaction result
     */
    public FileTransactionResult rollbackTransaction() {
        FileTransaction transaction = activeTransaction.get();
        if (transaction == null) {
            LoggerUtil.warn(this.getClass(), "No active transaction to roll back");
            return FileTransactionResult.failure("NONE", "No active transaction");
        }

        try {
            FileTransactionResult result = transaction.rollback();
            transactions.remove(transaction.getTransactionId());
            activeTransaction.remove();
            LoggerUtil.info(this.getClass(), "Transaction " + transaction.getTransactionId() + " rolled back");
            return result;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error rolling back transaction: " + e.getMessage(), e);
            transactions.remove(transaction.getTransactionId());
            activeTransaction.remove();
            return FileTransactionResult.failure(transaction.getTransactionId(), "Exception during rollback: " + e.getMessage());
        }
    }

    /**
     * Get a transaction by ID
     * @param transactionId The transaction ID
     * @return The transaction, if it exists
     */
    public Optional<FileTransaction> getTransaction(String transactionId) {
        return Optional.ofNullable(transactions.get(transactionId));
    }

    /**
     * Check if a transaction exists
     * @param transactionId The transaction ID
     * @return True if the transaction exists
     */
    public boolean hasTransaction(String transactionId) {
        return transactions.containsKey(transactionId);
    }
}