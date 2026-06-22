package web.restaurant.swp.config;

import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.auth.service.*;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                throw new UsernameNotFoundException("User not found");
            }
            User user = userOpt.get();
            if (!user.isActive()) {
                throw new RuntimeException("Tài khoản đang bị khoá.");
            }

            Collection<GrantedAuthority> authorities = new ArrayList<>();
            user.getRoles().forEach(r -> authorities.add(() -> "ROLE_" + r.getName().toUpperCase()));

            return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                    .password(user.getPassword())
                    .authorities(authorities)
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/forgot-password/**", "/api/auth/oauth2/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers("/api/hr/**").hasAnyRole("ADMIN", "MANAGER", "HR")
                .requestMatchers("/api/pos/**").hasAnyRole("ADMIN", "MANAGER", "CASHIER", "EMPLOYEE", "KITCHEN", "CHEF")
                .requestMatchers("/api/kds/**").hasAnyRole("ADMIN", "MANAGER", "KITCHEN", "CHEF")
                .requestMatchers("/api/inventory/**").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE", "CHEF")
                .requestMatchers("/api/procurement/**").hasAnyRole("ADMIN", "MANAGER", "PROCUREMENT")
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService()))
                .successHandler((request, response, authentication) -> {
                    response.sendRedirect("/api/auth/oauth2/success");
                })
                .failureHandler((request, response, exception) -> {
                    response.sendRedirect("/api/auth/oauth2/failure?error=" + exception.getMessage());
                })
            )
            .httpBasic(httpBasic -> {});

        return http.build();
    }

    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return request -> {
            OAuth2User oAuth2User = delegate.loadUser(request);
            String email = oAuth2User.getAttribute("email");
            if (email == null) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Không tìm thấy email từ tài khoản Google.");
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            User user;
            if (userOpt.isEmpty()) {
                String name = oAuth2User.getAttribute("name");
                if (name == null || name.trim().isEmpty()) {
                    name = email.split("@")[0];
                }

                Role defaultRole = roleRepository.findByName("EMPLOYEE")
                        .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error("role_not_found"), "Vai trò mặc định không tồn tại."));

                java.util.Set<Role> roles = new java.util.HashSet<>();
                roles.add(defaultRole);

                user = User.builder()
                        .email(email)
                        .password(passwordEncoder().encode(java.util.UUID.randomUUID().toString()))
                        .name(name)
                        .isActive(false)
                        .roles(roles)
                        .failedLoginAttempts(0)
                        .isTwoFactorEnabled(false)
                        .build();

                user = userRepository.save(user);
            } else {
                user = userOpt.get();
            }

            if (!user.isActive()) {
                throw new OAuth2AuthenticationException(new OAuth2Error("unauthorized_client"), "Tài khoản đang bị khoá.");
            }

            Collection<GrantedAuthority> authorities = new ArrayList<>();
            user.getRoles().forEach(r -> authorities.add(() -> "ROLE_" + r.getName().toUpperCase()));

            String userNameAttributeName = request.getClientRegistration()
                    .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

            return new DefaultOAuth2User(authorities, oAuth2User.getAttributes(), userNameAttributeName);
        };
    }
}
