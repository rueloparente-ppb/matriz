package com.jiraclone.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
class GlobalExceptionHandler {

	/** Full page for browser requests, a small fragment for htmx requests; never a stack trace. */
	@ExceptionHandler(NotFoundException.class)
	ModelAndView notFound(NotFoundException ex, HttpServletRequest request) {
		String view = HtmxAuthenticationEntryPoint.isHtmx(request) ? "common/not-found :: notFound" : "common/not-found";
		return new ModelAndView(view, HttpStatus.NOT_FOUND);
	}
}
