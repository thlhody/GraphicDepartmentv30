package com.ctgraphdep.fileOperations.model;

import com.ctgraphdep.fileOperations.core.FileOperationResult;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a file transaction.
 * Contains information about all operations performed in the transaction.
 */
@Getter
public class FileTransactionResult {
    private final String transactionId;
    private final boolean success;
    private final String errorMessage;
    private final List<FileOperationResult> operationResults;

    private FileTransactionResult(String transactionId, boolean success, String errorMessage, List<FileOperationResult> operationResults) {
        this.transactionId = transactionId;
        this.success = success;
        this.errorMessage = errorMessage;
        this.operationResults = operationResults != null ? operationResults : new ArrayList<>();
    }

    public static FileTransactionResult success(String transactionId, List<FileOperationResult> results) {
        return new FileTransactionResult(transactionId, true, null, results);
    }

    public static FileTransactionResult failure(String transactionId, String errorMessage) {
        return new FileTransactionResult(transactionId, false, errorMessage, null);
    }

    public static FileTransactionResult failure(String transactionId, String errorMessage, List<FileOperationResult> results) {
        return new FileTransactionResult(transactionId, false, errorMessage, results);
    }

    @Override
    public String toString() {
        if (success) {
            return "Transaction " + transactionId + " succeeded with " + operationResults.size() + " operations";
        } else {
            return "Transaction " + transactionId + " failed: " + errorMessage;
        }
    }
}