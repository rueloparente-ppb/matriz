package com.jiraclone.history.core;

import com.jiraclone.identity.IdentityApi;
import com.jiraclone.issues.IssueCreated;
import com.jiraclone.issues.IssueFieldChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryRecorder {

	private final HistoryRepository entries;
	private final IdentityApi identity;

	HistoryRecorder(HistoryRepository entries, IdentityApi identity) {
		this.entries = entries;
		this.identity = identity;
	}

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void created(IssueCreated event) {
		record(event.issueId(), event.actorId(), "CREATED", null, null, null);
	}

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void changed(IssueFieldChanged event) {
		record(event.issueId(), event.actorId(), "FIELD_CHANGED", event.field(), event.oldValue(), event.newValue());
	}

	private void record(long issueId, long actorId, String kind, String field, String oldValue, String newValue) {
		String actorName = identity.findUser(actorId).orElseThrow().displayName();
		entries.save(new HistoryEntry(issueId, actorId, actorName, kind, field, oldValue, newValue));
	}
}
