package com.helpdesk.user;

import com.helpdesk.security.UserContext;
import com.helpdesk.user.dto.UserDtos.ChangePasswordRequest;
import com.helpdesk.user.dto.UserDtos.UpdateProfileRequest;
import com.helpdesk.user.dto.UserDtos.UserProfile;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal UserContext current) {
        return userService.profile(current.id());
    }

    @PatchMapping("/me")
    public UserProfile update(@AuthenticationPrincipal UserContext current,
                              @Valid @RequestBody UpdateProfileRequest request) {
        return userService.update(current.id(), request);
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserContext current,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(current.id(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal UserContext current) {
        userService.deactivate(current.id());
        return ResponseEntity.noContent().build();
    }
}
