# Forged Idea: Letter & Contract Assistant in Fine

Extends the existing forged idea `../autonomous-hybrid-finance-agent/forged-idea.md` and the final PRD. It does not reopen them.

## Why it belongs to the thesis
- **Research contribution:** the uploaded document is a new *untrusted input channel* and a new *Trigger source*.
  - LLM-extracted facts stay untrusted until the customer confirms them.
  - Only confirmed facts enter the Deterministic Core.
  - This generalises the AD-24 parameter proposal and the AD-12 confirm control.
  - It answers "where is the AI?" without giving the LLM any authority.
- **Not a second product:** a "paperless office" (filing, search, task manager) is administration, not advice, so it is outlook only.

## Locked decisions
- **Core:** plain-language explanation plus confirmed fact extraction.
  - To-dos exist only as Notifications with a due date (open, done, dismissed).
  - Storage keeps only the original and its facts with provenance. There is no search.
- **Document types:**
  - Mandatory: **insurance premium increase**, as UJ-7.
  - Stretch 1: insurance policy (closes UJ-4).
  - Stretch 2: payment reminder or dunning letter, with an instalment-request template.
- **UJ-7:** Lena's car-insurance premium rises from 620 to 744 EUR, with a special termination right until 30 Nov.
  - She uploads the letter, Fine shows the fact card with source highlights, and she confirms it.
  - Fine explains the impact, including the forecast effect from the Core.
  - The deadline becomes a Notification, and the monitoring loop reminds her before it expires.
  - Fine offers a special-termination template for printing (L1).
  - If she asks for an objection letter, Fine refuses transparently and points to the consumer advice centre.
- **Input:** digital text PDFs only, of about 2 pages.
  - Persona fixtures are generated from templates with ground truth. Users may also upload their own text PDFs.
  - Text extraction is deterministic (PDFBox).
  - Truncation leads to low Data Confidence.
  - Images and scans are rejected transparently.
- **Fixed document pipeline:**
  1. PDF to text.
  2. Deterministic redaction: known persona attributes are replaced by string replace, and IBANs and contract numbers by regex, with placeholders. Amounts and dates are kept. This is a fixed document exposure level `redacted`, so E4 is untouched.
  3. NLU extraction with a JSON schema, temperature 0 and a fixed seed.
  4. **Deterministic span-grounding check:** every value must occur in the text and is stored with its location. Plausibility rules (zero premium, a deadline in the past) set Data Confidence to `low`.
  5. Fact card with plain-language labels and glossary tooltips, which the customer confirms or edits.
  6. Only then the explanation, written by the phrasing role and passing through the normal Check Chain and Number Check.
- **No path from a document to an Action.** Accounts and IBANs found in documents are never Action targets.
- **Gate:**
  - Confirming facts is a confirm control, not an Action.
  - A deadline enters monitoring automatically as the Trigger `DOCUMENT_DEADLINE`, at L0/L1.
  - A letter draft is **L1**. It is a **deterministic template** filled from confirmed fields, and the LLM never writes letter text (RDG § 2; BGH *Smartlaw* 2021).
  - **Fine never sends.** The letter is a printable HTML view that the customer prints or saves as PDF.
  - New gate criterion `legal` (letters to third parties), capped at L1, set as configuration.
- **Module `documents`:**
  - It owns `documents` and `document_facts` and depends on `llm`, `audit`, `personas` and `shared`.
  - `documents` holds the original, its SHA-256, the received date on the simulated clock and a status (`EXTRACTED`, `CONFIRMED`, `REJECTED`, `DELETED`).
  - `document_facts` holds field, raw and normalised value, source location, grounding status, Data Confidence, `confirmedAt`, `editedByUser` and `supersededBy`.
  - `core` and `monitoring` read confirmed facts through `documents.DocumentFactsPort`.
  - In the Core, confirmed facts become expected cash flows with their own `provId`. They are never written to `bank`.
  - `DUPLICATE_COVERAGE` may use policy facts.
- **Audit:** a new record kind `DOCUMENT_EXTRACTION` holds digests, hashes, the redacted extraction and the grounding results. It never holds the original text.
- **GDPR:**
  - When the customer deletes a document, the original and its raw values are hard-deleted, its facts are superseded and the forecast reverts.
  - The audit is kept, so AD-13 is untouched.
  - There is no automatic retention period; the customer decides.
- **Evaluation:**
  - **Test set:** 8 premium-increase variants (layout, wording, number and date formats, old and new premium as distractors) plus 6 red-team letters (embedded instructions, a foreign IBAN, a 0 EUR premium, a deadline in the past). It grows by 8 per stretch type.
  - **Metrics [V]:**
    - SM-10a: ungrounded values on the card, target **0%**.
    - SM-10b: field accuracy, reported.
    - SM-10c: values that are grounded but assigned to the wrong field, reported. This is the residual risk that the confirmation carries.
    - SM-5 extended by `DOCUMENT_DEADLINE` scenarios.
    - SM-7 extended by the red-team letters.
  - There is no E5, and E1–E4 are unchanged. Optionally, the E1 Colab script adds a field-accuracy column.
  - **UAT task:** the fact card contains one deliberately misassigned but grounded value. We observe whether participants catch it, which tests human confirmation as a safety principle. A debriefing is required, and a negative result is reported as a finding.

## Rejected
- Filing, search and a task manager ("paperless office"): this is administration, not advice → outlook.
- Utility-bill statements (tenancy law), pension notices (social law, long documents) and cancellation confirmations (low value) → outlook.
- OCR and a vision model: a second model family outside E1 and confounded error sources → outlook. The phone-photo case is a threat to validity.
- Objection letters (Widerspruch): they require a legal assessment of the individual case, which is a legal service under the RDG.
- LLM-written letters: they lose the Smartlaw argument.
- Sending by Fine (L2 send): it would create an outbound channel and liability. A delivered termination is irreversible anyway.
- Writing document facts into `bank.contracts`: it blurs provenance and breaks AD-12.
- Original text in the audit: it collides with GDPR Art. 17.

## Open
- The RDG reading is the author's, not a legal opinion → threat to validity. The instalment request (Stretch 2) is close to debt counselling.
- Language of the synthetic letters: German (realistic) versus English (consistent with the UI).
- Redaction of user-uploaded PDFs: unknown third-party names are not caught; only fixtures are fully controlled.
- The exact plausibility rules and the source-highlight UI on the fact card.
- Ethics wording for the planted-error UAT task.
