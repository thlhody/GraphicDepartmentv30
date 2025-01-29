package com.ctgraphdep.controller;

import com.ctgraphdep.model.User;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserRestController {
    private final UserService userService;

    public UserRestController(UserService userService) {
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        LoggerUtil.info(this.getClass(), "Fetching all users");
        List<User> users = userService.getAllUsers()
                .stream()
                .filter(user -> !user.isAdmin()) // Filter out admin users from the list
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUser(@PathVariable Integer userId) {
        LoggerUtil.info(this.getClass(), "Fetching user with ID: " + userId);
        Optional<User> user = userService.getUserById(userId);
        return user.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{userId}")
    public ResponseEntity<String> updateUser(@PathVariable Integer userId, @RequestBody User user) {
        LoggerUtil.info(this.getClass(), "Updating user with ID: " + userId);

        // Validate that the path userId matches the user object's userId
        if (!userId.equals(user.getUserId())) {
            return ResponseEntity.badRequest().body("User ID mismatch");
        }

        // Prevent updating to ADMIN role
        if ("ADMIN".equals(user.getRole())) {
            return ResponseEntity.badRequest().body("Cannot update to ADMIN role");
        }

        // Check if username already exists (excluding current user)
        boolean usernameExists = userService.getAllUsers().stream()
                .anyMatch(existingUser ->
                        existingUser.getUsername().equals(user.getUsername()) &&
                                !existingUser.getUserId().equals(userId)
                );

        if (usernameExists) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        boolean updated = userService.updateUser(user);
        if (updated) {
            return ResponseEntity.ok("User updated successfully");
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Integer userId) {
        LoggerUtil.info(this.getClass(), "Deleting user with ID: " + userId);

        // Prevent deletion of admin users
        Optional<User> userToDelete = userService.getUserById(userId);
        if (userToDelete.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (userToDelete.get().isAdmin()) {
            return ResponseEntity.badRequest().body("Cannot delete admin user");
        }

        userService.deleteUser(userId);
        return ResponseEntity.ok("User deleted successfully");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        LoggerUtil.error(this.getClass(), "Error in user operations: " + e.getMessage());
        return ResponseEntity.internalServerError().body("An error occurred while processing your request");
    }
}