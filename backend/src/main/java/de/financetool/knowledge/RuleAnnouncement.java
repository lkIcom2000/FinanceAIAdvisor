package de.financetool.knowledge;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Module API: an announced rule change from {@code data/rules/announcements.yaml}, known before its Rule Parameters
 * may exist.
 *
 * @param topic what the change is about, e.g. {@code subsidy}
 * @param title the change in plain words, e.g. "Retirement savings reform"
 * @param keys the Rule Parameter keys the change will set
 * @param source the confirmed source, or {@code null} if none exists yet
 */
public record RuleAnnouncement(String id, String topic, String title, List<String> keys, LocalDate announcedOn,
        LocalDate effectiveFrom, String source) {

    public RuleAnnouncement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(title, "title");
        keys = List.copyOf(keys);
        Objects.requireNonNull(announcedOn, "announcedOn");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveFrom.isBefore(announcedOn)) {
            throw new IllegalArgumentException(id + ": effective_from is before announced_on");
        }
    }
}
