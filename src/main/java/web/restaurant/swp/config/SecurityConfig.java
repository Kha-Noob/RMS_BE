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
    public org.springframework.security.authentication.AuthenticationProvider authenticationProvider() {
        return new org.springframework.security.authentication.AuthenticationProvider() {
            @Override
            public org.springframework.security.core.Authentication authenticate(org.springframework.security.core.Authentication authentication) throws org.springframework.security.core.AuthenticationException {
                String email = authentication.getName();
                String password = authentication.getCredentials().toString();

                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Email không tồn tại trong hệ thống."));

                // Enforce Gmail-only login for CUSTOMER role
                boolean isCustomer = user.getRoles().stream().anyMatch(r -> "CUSTOMER".equalsIgnoreCase(r.getName()));
                if (isCustomer && !email.toLowerCase().endsWith("@gmail.com") && !email.toLowerCase().endsWith("@googlemail.com")) {
                    throw new org.springframework.security.authentication.BadCredentialsException("Tài khoản khách hàng bắt buộc phải sử dụng Gmail.");
                }

                if (!user.isActive()) {
                    throw new org.springframework.security.authentication.DisabledException("Tài khoản đang bị khoá.");
                }

                // 1. Check temporary google passcode first
                String tempHash = web.restaurant.swp.modules.auth.service.AuthService.googleTempPasscodes.get(email);
                if (tempHash != null && passwordEncoder().matches(password, tempHash)) {
                    Collection<GrantedAuthority> authorities = new ArrayList<>();
                    user.getRoles().forEach(r -> authorities.add(() -> "ROLE_" + r.getName().toUpperCase()));
                    return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(email, password, authorities);
                }

                // 2. Check standard password
                if (passwordEncoder().matches(password, user.getPassword())) {
                    Collection<GrantedAuthority> authorities = new ArrayList<>();
                    user.getRoles().forEach(r -> authorities.add(() -> "ROLE_" + r.getName().toUpperCase()));
                    return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(email, password, authorities);
                }

                throw new org.springframework.security.authentication.BadCredentialsException("Mật khẩu không chính xác.");
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
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
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/events/public", "/api/events/public/**").permitAll()
                .requestMatchers("/api/events/**").hasAnyRole("ADMIN", "COOPERATOR")
                .requestMatchers("/api/floor-plans/files/**").permitAll()
                .requestMatchers("/api/admin/tenants/**").hasRole("ADMIN")
                .requestMatchers("/api/cooperator/tenant/bank/**").hasAnyRole("ADMIN", "COOPERATOR")
                .requestMatchers("/api/pos/bank-setting/**").hasAnyRole("ADMIN", "COOPERATOR", "MANAGER")
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "MANAGER", "COOPERATOR")
                .requestMatchers("/api/hr/**").hasAnyRole("ADMIN", "MANAGER", "HR")
                .requestMatchers("/api/pos/branch-admins/**").hasAnyRole("ADMIN", "COOPERATOR")
                .requestMatchers("/api/branches/**").hasAnyRole("ADMIN", "COOPERATOR")
                .requestMatchers("/api/pos/**").hasAnyRole("ADMIN", "COOPERATOR", "MANAGER", "CASHIER", "EMPLOYEE", "KITCHEN", "CHEF")
                .requestMatchers("/api/floor-plans/**").hasAnyRole("ADMIN", "MANAGER", "CASHIER", "EMPLOYEE", "KITCHEN", "CHEF")
                .requestMatchers("/api/kds/**").hasAnyRole("ADMIN", "MANAGER", "KITCHEN", "CHEF")
                .requestMatchers("/api/inventory/**").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE", "CHEF")
                .requestMatchers("/api/procurement/**").hasAnyRole("ADMIN", "MANAGER", "PROCUREMENT")
                .requestMatchers("/api/dashboard/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService()))
                .successHandler((request, response, authentication) -> {
                org.springframework.security.oauth2.core.user.OAuth2User oauthUser =
                        (org.springframework.security.oauth2.core.user.OAuth2User) authentication.getPrincipal();
                String email = oauthUser.getAttribute("email");
                if (email != null) {
                    User user = userRepository.findByEmail(email).orElse(null);
                    if (user != null) {
                        // Only generate temporary passcode for CUSTOMER role users
                        boolean isCustomer = user.getRoles().stream()
                                .anyMatch(r -> "CUSTOMER".equalsIgnoreCase(r.getName()));
                         if (isCustomer) {
                             String passcode = java.util.UUID.randomUUID().toString().replace("-", "");
                             web.restaurant.swp.modules.auth.service.AuthService.googleTempPasscodes.put(email, passwordEncoder().encode(passcode));
                             response.sendRedirect(
                                     (System.getenv("FRONTEND_URL") != null ? System.getenv("FRONTEND_URL") : "http://localhost:3000")
                                             + "/oauth2/callback?email=" + email + "&credentials=" + passcode);
                         } else {
                             // Non‑customer users: redirect to login with error
                             response.sendRedirect(
                                     (System.getenv("FRONTEND_URL") != null ? System.getenv("FRONTEND_URL") : "http://localhost:3000")
                                             + "/login?error=not_customer");
                         }
                         return;
                    }
                }
                response.sendRedirect(
                        (System.getenv("FRONTEND_URL") != null ? System.getenv("FRONTEND_URL") : "http://localhost:3000")
                                + "/oauth2/callback?error=user_not_found");
            })
                .failureHandler((request, response, exception) -> {
                    response.sendRedirect("http://localhost:3000/oauth2/callback?error=" + java.net.URLEncoder.encode(exception.getMessage(), "UTF-8"));
                })
            )
            .httpBasic(httpBasic -> httpBasic
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"" + authException.getMessage() + "\"}");
                })
            );

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

            String pictureUrl = oAuth2User.getAttribute("picture");
            Optional<User> userOpt = userRepository.findByEmail(email);
            User user;
            if (userOpt.isEmpty()) {
                String name = oAuth2User.getAttribute("name");
                if (name == null || name.trim().isEmpty()) {
                    name = email.split("@")[0];
                }

                Role defaultRole = roleRepository.findByName("CUSTOMER")
                        .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error("role_not_found"), "Vai trò mặc định không tồn tại."));

                java.util.Set<Role> roles = new java.util.HashSet<>();
                roles.add(defaultRole);

                user = User.builder()
                        .email(email)
                        .password(passwordEncoder().encode("GoogleUser123!"))
                        .name(name)
                        .isActive(true)
                        .roles(roles)
                        .avatarUrl(pictureUrl)
                        .failedLoginAttempts(0)
                        .isTwoFactorEnabled(false)
                        .isPasswordSet(false)
                        .build();

                user = userRepository.save(user);
            } else {
                user = userOpt.get();
                boolean updated = false;
                if (!user.isPasswordSet()) {
                    user.setPassword(passwordEncoder().encode("GoogleUser123!"));
                    updated = true;
                }
                if (pictureUrl != null && !pictureUrl.equals(user.getAvatarUrl())) {
                    user.setAvatarUrl(pictureUrl);
                    updated = true;
                }
                if (updated) {
                    user = userRepository.save(user);
                }
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
