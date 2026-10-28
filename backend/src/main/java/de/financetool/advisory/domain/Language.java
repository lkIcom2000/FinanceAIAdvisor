package de.financetool.advisory.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The answer language of a turn (Story 13.1): German or English, detected once per turn from the user's message,
 * deterministically (word and character cues, no model call). Every stage of the turn reads the same value.
 */
public enum Language {

    DE("German"),
    EN("English");

    /** The placeholder of the role prompt that names the answer language. */
    public static final String PLACEHOLDER = "{answer_language}";

    /** Words that mark a German message; words also common in English ("was", "will", "also", "an") are left out. */
    static final Set<String> GERMAN = Set.of("ich", "du", "sie", "er", "wir", "mein", "meine", "meinen", "meinem",
            "meiner", "mir", "mich", "ihr", "ihre", "ihren", "ihrem", "ihrer", "und", "oder", "nicht", "kein", "keine",
            "ist", "sind", "bin", "habe", "haben", "wie", "wann", "warum", "wieso", "welche", "welcher", "welches",
            "wo", "wer", "viel", "viele", "soll", "sollte", "sollen", "kann", "kannst", "können", "muss", "müssen",
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "mit", "für", "auf",
            "von", "zu", "zum", "zur", "bei", "nach", "über", "mehr", "jahr", "jahre", "jahren", "monat", "monatlich",
            "pro", "ja", "nein", "danke", "bitte", "hallo", "gut", "auch", "noch", "schon", "jetzt", "wenn", "dass",
            "es", "sich", "bekomme", "bekommen", "gibt", "geld", "rente", "sparen", "sparplan", "garantiert",
            "lohnt", "kostet", "kosten", "würde", "wäre", "möchte", "erklär", "erkläre", "erklären",
            "bedeutet", "bedeutung", "funktioniert", "heißt", "zahle", "zahlen", "verliere", "wieviel", "sinnvoll",
            "unterschied", "zwischen");

    /** Words that mark an English message; words also common in German ("in", "so", "an", "was") are left out. */
    static final Set<String> ENGLISH = Set.of("i", "you", "my", "me", "your", "we", "our", "what", "how", "why",
            "when", "which", "who", "where", "much", "many", "will", "would", "should", "could", "can", "do",
            "does", "did", "is", "are", "were", "be", "have", "has", "the", "a", "and", "or", "not",
            "of", "to", "for", "with", "on", "it", "this", "that", "get", "if", "about", "yes", "no", "thanks",
            "thank", "please", "hello", "explain", "tell", "year", "years", "month", "money", "pension",
            "save", "invest", "worth");

    private static final Pattern WORD = Pattern.compile("\\p{L}+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern GERMAN_LETTER = Pattern.compile("[äöüßÄÖÜ]");

    private final String englishName;

    Language(String englishName) {
        this.englishName = englishName;
    }

    /** The language's name as the model-facing prompt names it ("German"). */
    public String englishName() {
        return englishName;
    }

    /** The model-facing answer-language instruction, e.g. {@code Answer in German.} */
    public String instruction() {
        return "Answer in " + englishName + ".";
    }

    /** The language's code as the Audit Record holds it ({@code de}, {@code en}). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The language of {@code text}: German words and words with an umlaut or ß against English words. A tie (no cues,
     * e.g. "ok", "150 EUR?", "ETF") takes {@code previous}, else English.
     *
     * @param previous the language of the previous released turn; {@code null} without one
     */
    public static Language detect(String text, Language previous) {
        int german = 0;
        int english = 0;
        Matcher m = WORD.matcher(text == null ? "" : text);
        while (m.find()) {
            String word = m.group().toLowerCase(Locale.ROOT);
            if (GERMAN.contains(word) || GERMAN_LETTER.matcher(word).find()) {
                german++;
            } else if (ENGLISH.contains(word)) {
                english++;
            }
        }
        if (german > english) {
            return DE;
        }
        if (english > german) {
            return EN;
        }
        return previous == null ? EN : previous;
    }

    /** The language of a stored turn: its user message, else its released answer, else English. */
    public static Language of(String userText, String agentText) {
        return detect(userText, detect(agentText, null));
    }

    /**
     * The role prompt with the answer language filled in; a role prompt without the placeholder gets the instruction
     * as its last line.
     */
    public String fill(String rolePrompt) {
        return rolePrompt.contains(PLACEHOLDER) ? rolePrompt.replace(PLACEHOLDER, englishName)
                : rolePrompt.strip() + "\n" + instruction();
    }
}
