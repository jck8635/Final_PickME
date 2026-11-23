
package com.pickme.member;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/users/me/profile")
public class UserProfileController {

    private final UserProfileService profileService;

    public UserProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public UserProfileService.ProfileResponse getProfile() {
        Integer userId = LoginUser.requireId();
        return profileService.getProfile(userId);
    }

    @PutMapping
    public UserProfileService.ProfileResponse updateProfile(
            @RequestBody UserProfileService.ProfileUpdateRequest req) {
        Integer userId = LoginUser.requireId();
        return profileService.updateProfile(userId, req);
    }

}
