package com.jiraclone.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;

/** Helpers for non-200 view responses shared by all modules. */
public final class ErrorViews {

	private ErrorViews() {
	}

	/**
	 * Re-renders {@code view} with status {@code 422}. Return it from a handler that has a
	 * {@code BindingResult}; the model (including the bound form and its errors) is preserved.
	 */
	public static ModelAndView unprocessable(String view) {
		return new ModelAndView(view, HttpStatus.UNPROCESSABLE_ENTITY);
	}
}
