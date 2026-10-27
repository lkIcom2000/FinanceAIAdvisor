package de.financetool.core.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The Rule Parameter versions that started in a period, as keyed results, and the announced rule changes, from one
 * {@code CoreGateway} call.
 */
@NamedInterface("results")
public record RuleChangeFigures(List<Change> changes, List<Announcement> announcements) {

    /**
     * A version whose validity starts in the period.
     *
     * @param name the rule in plain words, e.g. "ECB deposit facility rate"
     * @param figures outputs {@link RuleParameterChange#OUTPUT_VALUE} and, if an older version exists,
     *        {@link RuleParameterChange#OUTPUT_PREVIOUS}
     */
    @NamedInterface("results")
    public record Change(String key, String name, String version, LocalDate validFrom, CoreResult figures) {

        public Change {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(validFrom, "validFrom");
            Objects.requireNonNull(figures, "figures");
        }
    }

    /**
     * An announced rule change.
     *
     * @param source the source of the announcement, or {@code null} if none is confirmed
     * @param rulesValid whether every one of its keys has a Rule Parameter version valid on {@code effectiveFrom}
     */
    @NamedInterface("results")
    public record Announcement(String id, String topic, String title, List<String> keys, LocalDate announcedOn,
            LocalDate effectiveFrom, String source, boolean rulesValid) {

        public Announcement {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(topic, "topic");
            Objects.requireNonNull(title, "title");
            keys = List.copyOf(keys);
            Objects.requireNonNull(announcedOn, "announcedOn");
            Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        }
    }

    public RuleChangeFigures {
        changes = List.copyOf(changes);
        announcements = List.copyOf(announcements);
    }
}
