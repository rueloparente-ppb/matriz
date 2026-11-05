package com.jiraclone.history.web;

import java.time.Instant;

record HistoryView(String actor, Instant createdAt, String kind, String field, String oldValue, String newValue) {

	static String shorten(String value) {
		if (value == null || value.isEmpty()) {
			return "None";
		}
		return value.length() > 100 ? value.substring(0, 100) + "…" : value;
	}
}
