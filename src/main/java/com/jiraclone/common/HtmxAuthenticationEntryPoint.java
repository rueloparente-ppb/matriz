package com.jiraclone.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Unauthenticated htmx requests get {@code 401} + {@code HX-Redirect} (a 302 would be followed inside the swap);
 * other requests are redirected to the login page. Neither response has a body.
 */
class HtmxAuthenticationEntryPoint implements AuthenticationEntryPoint {

	static final String LOGIN_URL = "/login";

	private final AuthenticationEntryPoint browserEntryPoint = new LoginUrlAuthenticationEntryPoint(LOGIN_URL);

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws java.io.IOException, jakarta.servlet.ServletException {
		if (isHtmx(request)) {
			response.setHeader("HX-Redirect", LOGIN_URL);
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
		} else {
			browserEntryPoint.commence(request, response, ex);
		}
	}

	static boolean isHtmx(HttpServletRequest request) {
		return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
	}
}
