package com.ctgraphdep.service;

import com.ctgraphdep.model.User;
import java.util.List;
import java.util.Optional;

public interface UserService {
    List<User> getAllUsers();
    Optional<User> getUserById(Integer userId);
    Optional<User> getUserByUsername(String username);
    void deleteUser(Integer userId);
    boolean updateUser(User user);
    boolean changePassword(Integer userId, String currentPassword, String newPassword);
    List<User> getNonAdminUsers(List<User> allUsers);
    Optional<User> findByEmployeeId(Integer employeeId);
}