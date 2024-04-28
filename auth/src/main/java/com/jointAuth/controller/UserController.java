package com.jointAuth.controller;

import com.jointAuth.bom.user.JwtResponse;
import com.jointAuth.bom.user.UserProfileBom;
import com.jointAuth.model.user.*;
import com.jointAuth.bom.user.UserBom;
import com.jointAuth.converter.UserConverter;
import com.jointAuth.repository.VerificationCodeRepository;
import com.jointAuth.service.UserService;
import com.jointAuth.service.VerificationCodeService;
import com.jointAuth.util.JwtTokenUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "auth")
public class UserController {

    private final UserService userService;

    private final JwtTokenUtils jwtTokenUtils;

    private final VerificationCodeService verificationCodeService;

    private final VerificationCodeRepository verificationCodeRepository;

    public UserController(@Autowired UserService userService,
                          @Autowired JwtTokenUtils jwtTokenUtils,
                          @Autowired VerificationCodeService verificationCodeService,
                          @Autowired VerificationCodeRepository verificationCodeRepository) {
        this.userService = userService;
        this.jwtTokenUtils = jwtTokenUtils;
        this.verificationCodeService = verificationCodeService;
        this.verificationCodeRepository = verificationCodeRepository;
    }

    @PostMapping(path = "/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        User registeredUser = userService.register(user);

        if (registeredUser != null) {
            UserDTO userDTO = UserConverter.convertToDto(registeredUser);
            return ResponseEntity.ok(userDTO);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to register user.");
    }

    @PostMapping(path = "/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest) {
        User user = userService.login(loginRequest.getEmail(), loginRequest.getPassword());

        if (user != null && userService.passwordsMatch(user.getPassword(), loginRequest.getPassword())) {
            if (user.getTwoFactorVerified()) {
                return ResponseEntity.ok("Two-factor authentication enabled. Verification code sent.");
            } else {
                String token = jwtTokenUtils.generateToken(user);
                return ResponseEntity.ok(new JwtResponse(token));
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password.");
    }

    @PostMapping(path = "/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody VerifyCodeRequest verifyCodeRequest) {
        boolean isValid = verificationCodeService.verifyVerificationCode(verifyCodeRequest.getUserId(),verifyCodeRequest.getCode());

        if (isValid) {
            User user = userService.getUserById(verifyCodeRequest.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found with userId: " + verifyCodeRequest.getUserId()));

            String token = jwtTokenUtils.generateToken(user);
            return  ResponseEntity.ok(new JwtResponse(token));
        } else  {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid verification code.");
        }
    }

    @GetMapping(path = "/user")
    public ResponseEntity<UserProfileBom> getUserByIdWithToken(@RequestHeader("Authorization") String token) {
        Long currentUserId = jwtTokenUtils.getCurrentUserId(token);
        UserProfileBom userResponseDTO = userService.getUserInfoById(currentUserId);

        if (userResponseDTO != null) {
            return ResponseEntity.ok(userResponseDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/user/get")
    public ResponseEntity<UserBom> getUserDetailsById(@RequestBody UserDetailsRequest userDetailsRequestDTO) {
        UserBom userDetailsDTO = userService.getUserByIdWithoutToken(userDetailsRequestDTO.getUserId());

        if (userDetailsDTO != null) {
            return ResponseEntity.ok(userDetailsDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(path = "/get-all")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        try {
            List<User> users = userService.getAllUsers();

            if (users.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            List<UserDTO> userDTOs = users.stream()
                    .map(user -> {
                        UserDTO dto = new UserDTO();
                        BeanUtils.copyProperties(user, dto);
                        return dto;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(userDTOs);
        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping(path = "/change-password")
    public ResponseEntity<?> changePassword(@RequestHeader("Authorization") String token,
                                            @RequestBody String password) {

        Long currentUserId = jwtTokenUtils.getCurrentUserId(token);

        Optional<User> existingUser = userService.getUserById(currentUserId);

        if (existingUser.isPresent()) {
            userService.changePassword(currentUserId, password);
            return ResponseEntity.ok("Password changed successfully");
        }

        return ResponseEntity.notFound().build();

    }

    @DeleteMapping(path = "/delete")
    public ResponseEntity<?> deleteUser(@RequestHeader("Authorization") String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long currentUserId = jwtTokenUtils.getCurrentUserId(token);

        Optional<User> existingUser = userService.getUserById(currentUserId);

        if (existingUser.isPresent()) {
            userService.deleteUser(currentUserId);
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.notFound().build();
    }

    @PostMapping(path = "/two-factor/enable")
    public ResponseEntity<?> enableTwoFactorAuth(@RequestHeader("Authorization") String token) {
        Long currentUserId = jwtTokenUtils.getCurrentUserId(token);
        userService.enableTwoFactorAuth(currentUserId);
        return ResponseEntity.ok("Two-factor authentication enabled successfully");
    }

    @PostMapping(path = "/two-factor/disable")
    public ResponseEntity<?> disableTwoFactorAuth(@RequestHeader("Authorization") String token) {
        Long currentUserId = jwtTokenUtils.getCurrentUserId(token);
        userService.disableTwoFactorAuth(currentUserId);
        return ResponseEntity.ok("Two-factor authentication disabled successfully");
    }
}