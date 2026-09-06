package com.eidcricketfest.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {

        http
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exceptionHandling ->
                        exceptionHandling
                                .authenticationEntryPoint(
                                        authenticationEntryPoint
                                )
                                .accessDeniedHandler(
                                        accessDeniedHandler
                                )
                )

                .authorizeHttpRequests(auth -> auth

                        // =========================
                        // PUBLIC AUTH ENDPOINTS
                        // =========================
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/bootstrap-admin",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()

                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")

                        // =========================
                        // SWAGGER / OPENAPI
                        // =========================
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // =========================
                        // ACTUATOR
                        // =========================
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // =========================
                        // PLAYER ENDPOINTS
                        // =========================

                        // Logged-in user can view own profile
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/players/me"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/players/me/**"
                        ).hasRole("PLAYER")

                        // Only PLAYER can create own player profile
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/players/me"
                        ).hasRole("PLAYER")

                        // Organizer/Admin can manually create a player
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/players"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Public player profiles
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/players/**"
                        ).permitAll()

                        // =========================
                        // PLAYER REGISTRATION
                        // =========================
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/tournament-editions/*/registrations/me"
                        ).hasRole("PLAYER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/registrations/me"
                        ).hasRole("PLAYER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/registrations"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // =========================
                        // TOURNAMENT
                        // =========================

                        // Public tournament information
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournaments/**"
                        ).permitAll()

                        // Only organizer/admin can create tournaments
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/tournaments/**"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Only organizer/admin can update tournament state
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/tournaments/**"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/tournaments/**"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )
                                // Player submitting own payment
                                .requestMatchers(
                                        HttpMethod.POST,
                                "/api/v1/registrations/*/payments/me"
                        ).hasRole("PLAYER")

                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/registrations/*/payments/me"
                                ).hasRole("PLAYER")

// Organizer records payment manually
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/v1/registrations/*/payments"
                                ).hasAnyRole(
                                        "ORGANIZER",
                                        "ADMIN"
                                )

// Payment verification/rejection
                                .requestMatchers(
                                        HttpMethod.PATCH,
                                        "/api/v1/registration-payments/*/verify",
                                        "/api/v1/registration-payments/*/reject"
                                ).hasAnyRole(
                                        "ORGANIZER",
                                        "ADMIN"
                                )

// Payment list
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/tournament-editions/*/payments"
                                ).hasAnyRole(
                                        "ORGANIZER",
                                        "ADMIN"
                                )

// Registration approval/rejection
                                .requestMatchers(
                                        HttpMethod.PATCH,
                                        "/api/v1/registrations/*/approve",
                                        "/api/v1/registrations/*/reject"
                                ).hasAnyRole(
                                        "ORGANIZER",
                                        "ADMIN"
                                )

                        // Permanent team creation
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/teams"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Public team list
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/teams"
                        ).permitAll()

                        // Add team to tournament
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/tournament-editions/*/teams/*"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Public tournament team list
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/teams"
                        ).permitAll()

                        // Captain assignment
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/tournament-teams/*/captain"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Draft pool
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/draft-pool"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Create draft
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/tournament-editions/*/draft"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Lottery and start
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/drafts/*/lottery",
                                "/api/v1/drafts/*/start"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Make draft pick
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/drafts/*/picks"
                        ).hasAnyRole(
                                "PLAYER",
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Public draft state/history
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/draft",
                                "/api/v1/drafts/*/picks"
                        ).permitAll()

                        // Fixture generation
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/tournament-editions/*/fixtures/**"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Friendly match creation/options
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/friendly-matches"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/friendly-matches/player-options"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/friendly-matches"
                        ).permitAll()

                        // Match scheduling
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/matches/*/schedule"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/matches/*/operations/reschedule"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/matches/*/operations/**"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Match setup readback
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/matches/*/setup"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Assign scorer
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/matches/*/scorers"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Scorer selection
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users/scorers"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Playing XI
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/matches/*/teams/*/playing-xi"
                        ).hasAnyRole(
                                "PLAYER",
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Toss
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/matches/*/toss"
                        ).hasAnyRole(
                                "SCORER",
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Public fixtures/results
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/matches"
                        ).permitAll()

                        // Venue management
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/venues"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/venues"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/matches/*/innings",
                                "/api/v1/innings/*/deliveries",
                                "/api/v1/innings/*/deliveries/undo"
                        ).hasAnyRole(
                                "SCORER",
                                "ORGANIZER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/innings/*/batters",
                                "/api/v1/innings/*/bowler"
                        ).hasAnyRole(
                                "SCORER",
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Scorer console
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/scorer/matches",
                                "/api/v1/scorer/matches/*"
                        ).hasAnyRole(
                                "SCORER",
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // WebSocket spectators
                        .requestMatchers(
                                "/ws",
                                "/ws/**"
                        ).permitAll()

                        // Public live scores
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/matches/live-centre",
                                "/api/v1/matches/*/live",
                                "/api/v1/matches/*/scorecard"
                        ).permitAll()

                        // Delivery corrections
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/deliveries/*"
                        ).hasAnyRole(
                                "SCORER",
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Public standings
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/standings"
                        ).permitAll()

                        // Public tournament statistics
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/statistics"
                        ).permitAll()

                        // Mark match as No Result
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/matches/*/no-result"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Generate knockout semi-finals
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/tournament-editions/*/knockout/semi-finals"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Public knockout bracket
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/knockout"
                        ).permitAll()

                        // Resolve tied / forfeited knockout match
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/matches/*/knockout-winner"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Assign tournament awards
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/tournament-editions/*/awards"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Award recipient options
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/awards/player-options"
                        ).hasAnyRole(
                                "ORGANIZER",
                                "ADMIN"
                        )

                        // Public awards
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournament-editions/*/awards"
                        ).permitAll()

                        // Public permanent player career stats
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/players/*/career"
                        ).permitAll()

                        // Public tournament history
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tournaments/*/history"
                        ).permitAll()

                        // Everything else requires authentication
                        .anyRequest()
                        .authenticated()
                )

                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter rolesConverter =
                new JwtGrantedAuthoritiesConverter();

        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                rolesConverter
        );

        return converter;
    }
}
