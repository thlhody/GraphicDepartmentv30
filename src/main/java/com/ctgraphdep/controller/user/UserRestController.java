package com.ctgraphdep.controller.user;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class UserRestController extends BaseController {

    public UserRestController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService) {
        super(userService, folderStatus, timeValidationService);
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers(@AuthenticationPrincipal UserDetails userDetails) {
        LoggerUtil.info(this.getClass(), "Fetching all users at " + getStandardCurrentDateTime());

        // Use validateUserAccess for admin role verification
        User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<User> users = getUserService().getAllUsers()
                .stream()
                .filter(user -> !user.isAdmin()) // Filter out admin users from the list
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer userId) {
        LoggerUtil.info(this.getClass(), "Fetching user with ID: " + userId + " at " + getStandardCurrentDateTime());

        // Use validateUserAccess for admin role verification
        User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<User> user = getUserService().getUserById(userId);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{userId}")
    public ResponseEntity<String> updateUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer userId,
            @RequestBody User user) {
        LoggerUtil.info(this.getClass(), "Updating user with ID: " + userId + " at " + getStandardCurrentDateTime());

        // Use validateUserAccess for admin role verification
        User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        // Validate that the path userId matches the user object's userId
        if (!userId.equals(user.getUserId())) {
            return ResponseEntity.badRequest().body("User ID mismatch");
        }

        // Prevent updating to ADMIN role
        if (SecurityConstants.ROLE_ADMIN.equals(user.getRole())) {
            return ResponseEntity.badRequest().body("Cannot update to ADMIN role");
        }

        // Check if username already exists (excluding current user)
        boolean usernameExists = getUserService().getAllUsers().stream()
                .anyMatch(existingUser ->
                        existingUser.getUsername().equals(user.getUsername()) &&
                                !existingUser.getUserId().equals(userId)
                );

        if (usernameExists) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        boolean updated = getUserService().updateUser(user);
        if (updated) {
            return ResponseEntity.ok("User updated successfully");
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<String> deleteUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer userId) {
        LoggerUtil.info(this.getClass(), "Deleting user with ID: " + userId + " at " + getStandardCurrentDateTime());

        // Use validateUserAccess for admin role verification
        User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        // Prevent deletion of admin users
        Optional<User> userToDelete = getUserService().getUserById(userId);
        if (userToDelete.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (userToDelete.get().isAdmin()) {
            return ResponseEntity.badRequest().body("Cannot delete admin user");
        }

        getUserService().deleteUser(userId);
        return ResponseEntity.ok("User deleted successfully");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        LocalDateTime currentTime = getStandardCurrentDateTime();
        LoggerUtil.error(this.getClass(),
                String.format("Error at %s: %s", currentTime, e.getMessage()),
                e);
        return ResponseEntity.internalServerError().body("An error occurred while processing your request");
    }
}