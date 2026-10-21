# Knowledge Base corpus (RAG)

Put the PDFs for retrieval in `pdfs/`. Every PDF also needs one row in `metadata.csv`. A PDF without a row is skipped, because a passage without a source and validity date cannot be cited (PRD FR-23).

## metadata.csv

| Column | Meaning | Example |
|---|---|---|
| `filename` | File name in `pdfs/` | `esma_guidelines_fund_names_2024.pdf` |
| `doc_id` | Short stable id, no spaces | `esma-fundnames` |
| `title` | Document title. It is also used to verify premises in trap questions. | `ESMA Guidelines on funds' names using ESG or sustainability-related terms` |
| `source` | Issuer and reference | `ESMA34-1592494965-657` |
| `version` | Version or publication date | `2024-05-14` |
| `valid_from` | First day the document applies (ISO date) | `2024-11-21` |
| `valid_to` | Last day it applies; leave empty if still valid | |

Example row:

```csv
esma_guidelines_fund_names_2024.pdf,esma-fundnames,ESMA Guidelines on funds' names using ESG or sustainability-related terms,ESMA34-1592494965-657,2024-05-14,2024-11-21,
```

## Rules

- **Expired documents.** Documents whose `valid_to` lies before the as-of date are not retrieved (FR-23). Keep superseded versions in the corpus with the correct `valid_to` instead of deleting them.
- **Changing the corpus.** Any change to a PDF or to `metadata.csv` produces a new KB snapshot id. The id is written to every run manifest, and the index is rebuilt automatically.
- **Language.** English sources fit best, because the experiments run in English. The embedding model `bge-m3` is multilingual, so German sources (e.g. BaFin, EStG) also work.
- **Coverage.** The Q&A set covers MiFID II (target market, cost transparency, suitability report), PRIIPs KID, UCITS/ETF replication, tracking error and difference, securities lending, SFDR Articles 6/8/9, ESMA fund-name guidelines, sustainability preferences, best execution, bond liquidity, the Vorabpauschale (InvStG), money market funds vs. deposit guarantee, emergency fund guidance, Riester and private pension insurance, and liability and occupational disability insurance basics.

Which sources make up the first corpus, and the reference date, is PRD Open Question 4.
