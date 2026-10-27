package de.financetool.knowledge.adapter;

import de.financetool.knowledge.NoValidRuleParameterException;
import de.financetool.knowledge.RuleAnnouncement;
import de.financetool.knowledge.RuleParamPort;
import de.financetool.knowledge.RuleParameter;
import de.financetool.shared.AppHome;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The Rule Parameters in {@code APP_HOME/data/rules/rule_parameters.yaml}, loaded once at startup. Startup fails on a
 * malformed entry, a duplicate version of a key, or two versions of one key with overlapping validity.
 */
@Component
class YamlRuleParameters implements RuleParamPort {

    static final String FILE = "rules/rule_parameters.yaml";
    static final String ANNOUNCEMENTS = "announcements.yaml";

    private final List<RuleParameter> parameters;
    private final List<RuleAnnouncement> announcements;

    @Autowired
    YamlRuleParameters(AppHome appHome) {
        this(appHome.data().resolve(FILE));
    }

    /** @param file the Rule Parameters; the announcements are read from {@value #ANNOUNCEMENTS} next to it */
    YamlRuleParameters(Path file) {
        this.parameters = load(file);
        this.announcements = loadAnnouncements(file.resolveSibling(ANNOUNCEMENTS));
    }

    @Override
    public RuleParameter valid(String key, LocalDate asOf) {
        List<RuleParameter> versions = parameters.stream().filter(p -> p.key().equals(key)).toList();
        if (versions.isEmpty()) {
            throw new NoValidRuleParameterException("Unknown Rule Parameter " + key);
        }
        return versions.stream().filter(p -> p.isValidAt(asOf)).findFirst()
                .orElseThrow(() -> new NoValidRuleParameterException("No version of Rule Parameter " + key
                        + " is valid on " + asOf));
    }

    @Override
    public List<RuleParameter> allValid(LocalDate asOf) {
        return parameters.stream().filter(p -> p.isValidAt(asOf)).toList();
    }

    @Override
    public List<RuleParameter> startingBetween(LocalDate after, LocalDate until) {
        return parameters.stream()
                .filter(p -> p.validFrom().isAfter(after) && !p.validFrom().isAfter(until))
                .toList();
    }

    @Override
    public Optional<RuleParameter> previous(RuleParameter version) {
        return parameters.stream()
                .filter(p -> p.key().equals(version.key()) && p.validFrom().isBefore(version.validFrom()))
                .max(Comparator.comparing(RuleParameter::validFrom));
    }

    @Override
    public List<RuleAnnouncement> announcements() {
        return announcements;
    }

    /** The announcements next to the Rule Parameters; none if the file does not exist. */
    static List<RuleAnnouncement> loadAnnouncements(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        Object root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read rule announcements " + file, e);
        }
        if (!(root instanceof Map<?, ?> map) || !(map.get("announcements") instanceof List<?> entries)) {
            throw new IllegalStateException(file + " needs an 'announcements' list");
        }
        List<RuleAnnouncement> result = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> e) || !(e.get("keys") instanceof List<?> keys)) {
                throw new IllegalStateException(file + ": every announcement is a map with a 'keys' list");
            }
            try {
                result.add(new RuleAnnouncement(text(e, "id"), text(e, "topic"), text(e, "title"),
                        keys.stream().map(Object::toString).toList(), date(e.get("announced_on")),
                        date(e.get("effective_from")), e.get("source") == null ? null : e.get("source").toString()));
            } catch (RuntimeException ex) {
                throw new IllegalStateException(file + ": invalid announcement " + e.get("id") + ": "
                        + ex.getMessage(), ex);
            }
        }
        if (result.stream().map(RuleAnnouncement::id).distinct().count() != result.size()) {
            throw new IllegalStateException(file + ": announcement ids must be unique");
        }
        return result.stream().sorted(Comparator.comparing(RuleAnnouncement::announcedOn)).toList();
    }

    static List<RuleParameter> load(Path file) {
        Object root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read Rule Parameters " + file, e);
        }
        if (!(root instanceof Map<?, ?> map) || !(map.get("parameters") instanceof List<?> entries)) {
            throw new IllegalStateException(file + " needs a 'parameters' list");
        }
        List<RuleParameter> result = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> e)) {
                throw new IllegalStateException(file + ": every parameter is a map");
            }
            try {
                result.add(new RuleParameter(text(e, "key"), new BigDecimal(text(e, "value")), text(e, "unit"),
                        date(e.get("valid_from")), date(e.get("valid_to")), text(e, "source"), text(e, "version")));
            } catch (RuntimeException ex) {
                throw new IllegalStateException(file + ": invalid Rule Parameter " + e.get("key") + ": "
                        + ex.getMessage(), ex);
            }
        }
        for (RuleParameter a : result) {
            for (RuleParameter b : result) {
                if (a != b && a.key().equals(b.key()) && a.version().equals(b.version())) {
                    throw new IllegalStateException(file + ": version " + a.version() + " of " + a.key()
                            + " appears twice; a change adds a new version");
                }
                if (a != b && a.key().equals(b.key()) && overlap(a, b)) {
                    throw new IllegalStateException(file + ": versions " + a.version() + " and " + b.version()
                            + " of " + a.key() + " overlap");
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean overlap(RuleParameter a, RuleParameter b) {
        boolean aEndsBeforeB = a.validTo() != null && a.validTo().isBefore(b.validFrom());
        boolean bEndsBeforeA = b.validTo() != null && b.validTo().isBefore(a.validFrom());
        return !aEndsBeforeB && !bEndsBeforeA;
    }

    private static String text(Map<?, ?> entry, String field) {
        Object value = entry.get(field);
        if (value == null) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value.toString();
    }

    /** SnakeYAML reads an unquoted ISO date as a {@link Date} at UTC midnight. */
    private static LocalDate date(Object value) {
        return switch (value) {
            case null -> null;
            case Date date -> date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            default -> LocalDate.parse(value.toString());
        };
    }
}
