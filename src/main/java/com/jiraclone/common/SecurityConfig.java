package com.jiraclone.common;

import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

@Configuration
@EnableWebSecurity
class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		// Only remember full-page GETs; an htmx fragment URL is not a page to return to after login.
		var requestCache = new HttpSessionRequestCache();
		requestCache.setRequestMatcher(
				request -> "GET".equals(request.getMethod()) && !HtmxAuthenticationEntryPoint.isHtmx(request));

		http.authorizeHttpRequests(auth -> auth
						.requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
						.requestMatchers("/login", "/error", "/actuator/health/**").permitAll()
						.anyRequest().authenticated())
				.requestCache(cache -> cache.requestCache(requestCache))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(new HtmxAuthenticationEntryPoint()))
				.formLogin(form -> form
						.loginPage(HtmxAuthenticationEntryPoint.LOGIN_URL)
						.failureUrl("/login?error")
						.defaultSuccessUrl("/", false))
				.logout(logout -> logout.logoutSuccessUrl("/login?logout"));
		return http.build();
	}
}
