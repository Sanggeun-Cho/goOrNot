package com.thc.goornotdev.security;

import com.thc.goornotdev.domain.User;
import com.thc.goornotdev.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PrincipalDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    /**
     *  principalDetails 생성을 위한 함수.
     *  username 으로 user 조회, principalDetails 생성
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            // AuthenticationException 계열로 던져야 Spring Security 가 401 로 변환한다
            throw new UsernameNotFoundException("username : " + username);
        }

        return new PrincipalDetails(user);
    }
}
