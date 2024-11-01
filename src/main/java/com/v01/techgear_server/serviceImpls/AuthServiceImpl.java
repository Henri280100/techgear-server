package com.v01.techgear_server.serviceImpls;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;

import com.v01.techgear_server.enums.Roles;
import com.v01.techgear_server.exception.UserAlreadyExistsException;
import com.v01.techgear_server.model.PasswordResetToken;
import com.v01.techgear_server.model.Role;
import com.v01.techgear_server.model.User;
import com.v01.techgear_server.model.UserAddress;
import com.v01.techgear_server.model.UserPhoneNo;
import com.v01.techgear_server.repo.PasswordResetTokenRepository;
import com.v01.techgear_server.repo.RoleRepository;
import com.v01.techgear_server.repo.UserRepository;
import com.v01.techgear_server.service.CacheEvictionService;
import com.v01.techgear_server.service.CacheService;
import com.v01.techgear_server.utils.PasswordValidation;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthServiceImpl implements UserDetailsManager {

    private static final String AUTH_CODES_HASH = "auth_codes";
    private static final int AUTH_CODE_EXPIRATION_MINUTES = 30;
    private static final int MAX_AUTH_CODES = 1000;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserPhoneNoServiceImpl userPhoneNumberService;

    @Autowired
    private AddressServiceImpl addressService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private CacheEvictionService cacheEvictionService;

    private static Logger LOGGER = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User with username not found: " + username));

        if (!user.isActive()) {
            throw new IllegalArgumentException("Email not verified. Please verify your email.");
        }

        return user;
    }

    public User loadUserEntityByUsername(String username) throws UsernameNotFoundException {

        return userRepository.findByUsername(username)

                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

    }

    @Override
    public boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    @Override
    public void createUser(UserDetails user) {
        try {
            User users = convertToUserEntity(user);
            validateNewUser(users);

            users.setPassword(PasswordValidation.encodePassword(users.getPassword()));
            users.setUsername(users.getUsername());
            users.setGenders(users.getGenders());

            setUserRoles(users);
            handleUserAddressAndPhone(users);

            User savedUser = userRepository.save(users);
            if (savedUser != null) {
                storeAuthCode(savedUser);
            } else {
                LOGGER.error("Failed to save user: {}", users);
            }
            // LOGGER.info("User created successfully: {}", users);
        } catch (IllegalArgumentException | UserAlreadyExistsException e) {
            // Log and handle the specific argument errors
            LOGGER.error("User creation error: {}", e.getMessage());
            throw e; // Re-throwing to be caught in the controller
        } catch (Exception e) {
            LOGGER.error("Unexpected error during user creation: ", e);
            throw new RuntimeException("Failed to create user", e);
        }
    }

    private void storeAuthCode(User user) {
        String authCode = generateAuthCode(user);
        cacheService.hashPut(AUTH_CODES_HASH, user.getUsername(), authCode);
        cacheService.expire(AUTH_CODES_HASH, AUTH_CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        cacheEvictionService.evictLRU(AUTH_CODES_HASH, MAX_AUTH_CODES);
    }

    private String generateAuthCode(User user) {
        String baseString = user.getUsername() + user.getEmail() + System.currentTimeMillis();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(baseString.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);

            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            // Take the first 32 characters of the hex string as the auth code
            return hexString.toString().substring(0, 32);

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            // Fallback to UUID if SHA-256 is not available
            return UUID.randomUUID().toString();
        }
    }

    private void setUserRoles(User user) {
        Set<Role> roles = assignRoles(user);
        user.setRoles(roles);
    }

    private void handleUserAddressAndPhone(User user) {
        if (user.getAddresses() != null) {
            UserAddress savedAddress = addressService.createUserNewAddress(user);
            user.setAddresses(savedAddress); // Link the saved address with the user
        }

        if (user.getPhoneNumbers() != null) {
            UserPhoneNo savUserPhoneNo = userPhoneNumberService.saveUserPhoneNo(user);
            user.setPhoneNumbers(savUserPhoneNo);
        }
    }

    private void validateNewUser(User user) {
        // Check if the username is already taken
        if (!isUsernameAvailable(user.getUsername())) { // NOTE: Logic fixed
            LOGGER.error("Username already exists");
            throw new UserAlreadyExistsException("Username already exists");
        }
        // Check if the email is already taken
        if (!isUserEmailAvailable(user.getEmail())) { // Logic is negated
            LOGGER.error("Email {} already exists", user.getEmail());
            throw new UserAlreadyExistsException("Email already taken");
        }
        // Validate password format
        if (!PasswordValidation.isValidPassword(user.getPassword())) {
            LOGGER.error("Password does not meet security requirements");
            throw new IllegalArgumentException("Password does not meet security requirements");
        }

        // Check if the password has been used before (e.g., reusing old passwords)
        if (isPasswordAvailable(user.getPassword())) {
            LOGGER.error("Password has been used before");
            throw new IllegalArgumentException("Password has been used before");
        }
    }

    private User convertToUserEntity(UserDetails userDetails) {
        return modelMapper.map(userDetails, User.class);
    }

    private Set<Role> assignRoles(User user) {
        Set<Role> roles = new HashSet<>();

        // If no roles provided, assign the default USER role
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            // Assign the default USER role if no roles provided
            Role defaultRole = roleRepository.findByRoleType(Roles.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Default USER role not found."));
            roles.add(defaultRole);
        } else {
            // Iterate through provided roles and fetch from the database
            user.getRoles().forEach(providedRole -> {
                Role role = roleRepository.findByRoleType(providedRole.getRoleType())
                        .orElseThrow(() -> new RuntimeException("Error: Role not found."));
                roles.add(role);
            });
        }

        return roles;
    }

    private boolean isUserEmailAvailable(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        return user.isEmpty();
    }

    private boolean isUsernameAvailable(String username) {
        // Check if the username already exists in the database
        Optional<User> user = userRepository.findByUsername(username);
        return user.isEmpty();
    }

    private boolean isPasswordAvailable(String newPassword) {
        // Check if the new password is already in the password history
        // Encode the new password and compare it with the password history
        // if it's OK then return true, if not return false.
        String hashedPassword = passwordEncoder.encode(newPassword);
        for (User user : userRepository.findAll()) {
            if (user.getPassword().contains(hashedPassword)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void updateUser(UserDetails user) {
        // try {
        // // TODO: Handle update logic here, currently only handle the userName and
        // // password fields,
        // // after the tested, I should handle other fields in the future.
        // // For now, just check if the user is an instance of User and check if
        // username,
        // // password exists.
        // if (!(user instanceof User)) {
        // throw new IllegalArgumentException("User must be an instance of User");
        // }

        // User updatedUser = (User) user;

        // // Fetch the existing user from the repository
        // User existingUser = userRepository.findByUserName(updatedUser.getUsername())
        // .orElseThrow(() -> new IllegalArgumentException(
        // "User with username " + updatedUser.getUsername() + " not found"));

        // // Update fields from the UserDetails
        // existingUser.setUserName(updatedUser.getUsername());
        // if (updatedUser.getPassword() != null &&
        // !updatedUser.getPassword().isEmpty()) {
        // existingUser.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
        // }

        // // TODO: It should be handled email and roles assignment right here
        // // and the phoneNo and address should be handled in the future.

        // // Save the updated user
        // userRepository.save(existingUser);
        // ResponseEntity.status(HttpStatus.OK).body("User updated successfully");
        // } catch (Exception e) {
        // LOGGER.error("Exception occurred while updating user: {}", e.getMessage(),
        // e);
        // ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to
        // update user");
        // }

    }

    @Override
    public void deleteUser(String username) {
        if (!userExists(username)) {
            throw new IllegalArgumentException("User with username " + username + " not found");
        } else {
            userRepository.deleteByUsername(username);
        }

    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new IllegalStateException("User is not authenticated");
            }

            String username = authentication.getName();

            if (!passwordEncoder.matches(oldPassword, userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("User not found")).getPassword())) {
                throw new IllegalArgumentException("Old password is not correct");
            }

            if (!PasswordValidation.isValidPassword(newPassword)) {
                throw new IllegalArgumentException("New password is not meet the security requirements");
            }

            userRepository.updatePassword(username, passwordEncoder.encode(newPassword));
        } catch (Exception e) {
            LOGGER.error("Exception occurred while updating password: {}", e.getMessage(), e);
        }

    }

    public void onPasswordResetToken(User user, String token) {
        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setToken(token);
        passwordResetToken.setUser(user);
        passwordResetToken.setExpiredDate(LocalDateTime.now().plusHours(24)); // Token valid for 24 hours
        passwordResetTokenRepository.save(passwordResetToken);
    }

    public User findUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User's email not found"));
        return user;
    }

    public PasswordResetToken validatePasswordResetToken(String token) {
        return passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
    }

    public void updateUserPassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // TODO: Login with a third party such as Google or Facebook

}
