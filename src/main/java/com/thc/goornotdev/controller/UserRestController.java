package com.thc.goornotdev.controller;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.UserDto;
import com.thc.goornotdev.security.PrincipalDetails;
import com.thc.goornotdev.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/api/user")
@RestController
public class UserRestController {
    private final UserService userService;

    // 요청한 사용자의 ID 반환
    public Long getUserId(PrincipalDetails principalDetails) {
        if (principalDetails != null && principalDetails.getUser() != null) {
            return principalDetails.getUser().getId();
        }

        return null;
    }

    @PreAuthorize("permitAll()")
    @PostMapping("")
    public ResponseEntity<DefaultDto.CreateResDto> create(@Valid @RequestBody UserDto.CreateReqDto param,
                                                          @AuthenticationPrincipal PrincipalDetails principalDetails) {
        return ResponseEntity.ok(userService.create(param, getUserId(principalDetails)));
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping("")
    public ResponseEntity<Void> update(@Valid @RequestBody UserDto.UpdateReqDto param,
                                       @AuthenticationPrincipal PrincipalDetails principalDetails) {
        userService.update(param, getUserId(principalDetails));

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("")
    public ResponseEntity<Void> delete(@RequestBody UserDto.UpdateReqDto param,
                                       @AuthenticationPrincipal PrincipalDetails principalDetails) {
        userService.delete(param, getUserId(principalDetails));

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("")
    public ResponseEntity<UserDto.DetailResDto> detail(DefaultDto.DetailReqDto param,
                                                       @AuthenticationPrincipal PrincipalDetails principalDetails) {
        return ResponseEntity.ok(userService.detail(param, getUserId(principalDetails)));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/list")
    public ResponseEntity<List<UserDto.DetailResDto>> list(UserDto.ListReqDto param,
                                                           @AuthenticationPrincipal PrincipalDetails principalDetails) {
        return ResponseEntity.ok(userService.list(param, getUserId(principalDetails)));
    }
}
