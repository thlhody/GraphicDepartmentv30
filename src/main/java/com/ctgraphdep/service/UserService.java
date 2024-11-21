package com.ctgraphdep.service;

import com.ctgraphdep.model.User;
import java.util.List;
import java.util.Optional;

public interface UserService {
    Optional<User> getAdminUser();

    List<User> getAllUsers();
    Optional<User> getUserById(Integer userId);
    Optional<User> getUserByUsername(String username);
    User saveUser(User user);
    void deleteUser(Integer userId);
    boolean updateUser(User user);
    boolean validateCredentials(String username, String password);
    boolean changePassword(Integer userId, String currentPassword, String newPassword);
    List<User> getNonAdminUsers(List<User> allUsers);
    String getPasswordHash(String username);
}