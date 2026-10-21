-- Story 2.1: sandbox bank tables of the bank module. Rows come from the persona fixtures (data/personas/*.json)
-- and are written at startup or by persona reset; only BankPort books further transactions (origin BANK_PORT).
-- persona_id is an id-only reference to the personas module (AD-12): no foreign key. Foreign keys stay inside the
-- module. Money is NUMERIC(14,2).

CREATE TABLE accounts (
    id              UUID          PRIMARY KEY,
    persona_id      VARCHAR(64)   NOT NULL,
    type            VARCHAR(16)   NOT NULL CHECK (type IN ('CURRENT', 'OVERNIGHT', 'DEPOT')),
    name            VARCHAR(120)  NOT NULL,
    iban            VARCHAR(34)   NOT NULL,
    opening_date    DATE          NOT NULL,
    opening_balance NUMERIC(14,2) NOT NULL
);

CREATE INDEX accounts_persona ON accounts (persona_id);

CREATE TABLE contracts (
    id               UUID          PRIMARY KEY,
    persona_id       VARCHAR(64)   NOT NULL,
    coverage         VARCHAR(32)   NOT NULL CHECK (coverage IN ('LIABILITY', 'CAR', 'HOUSEHOLD_CONTENTS',
                                       'OCCUPATIONAL_DISABILITY', 'LEGAL_PROTECTION', 'RIESTER_PENSION')),
    provider         VARCHAR(120)  NOT NULL,
    policy_number    VARCHAR(64)   NOT NULL,
    policyholder     VARCHAR(120)  NOT NULL,
    insured_role     VARCHAR(16)   NOT NULL CHECK (insured_role IN ('POLICYHOLDER', 'CO_INSURED')),
    premium          NUMERIC(14,2) CHECK (premium > 0),
    payment_interval VARCHAR(16)   CHECK (payment_interval IN ('MONTHLY', 'QUARTERLY', 'ANNUAL')),
    next_due_date    DATE,
    debit_account_id UUID          REFERENCES accounts (id),
    start_date       DATE          NOT NULL,
    CHECK ((premium IS NULL) = (payment_interval IS NULL)
        AND (premium IS NULL) = (next_due_date IS NULL)
        AND (premium IS NULL) = (debit_account_id IS NULL))
);

CREATE INDEX contracts_persona ON contracts (persona_id);

CREATE TABLE transactions (
    id            UUID          PRIMARY KEY,
    persona_id    VARCHAR(64)   NOT NULL,
    account_id    UUID          NOT NULL REFERENCES accounts (id),
    booking_date  DATE          NOT NULL,
    amount        NUMERIC(14,2) NOT NULL,
    category      VARCHAR(32)   NOT NULL CHECK (category IN ('SALARY', 'CHILD_BENEFIT', 'OTHER_INCOME', 'RENT',
                                    'UTILITIES', 'TELECOM', 'SUBSCRIPTIONS', 'PUBLIC_TRANSPORT', 'CHILDCARE',
                                    'INSURANCE', 'PENSION', 'SAVINGS_PLAN', 'TRANSFER', 'GROCERIES', 'DINING',
                                    'SHOPPING', 'LEISURE', 'HEALTH', 'MOBILITY', 'CASH_WITHDRAWAL', 'FEES')),
    counterparty  VARCHAR(120)  NOT NULL,
    reference     VARCHAR(200)  NOT NULL,
    recurrence_id VARCHAR(64),
    contract_id   UUID          REFERENCES contracts (id),
    origin        VARCHAR(16)   NOT NULL CHECK (origin IN ('FIXTURE', 'BANK_PORT')),
    CHECK (recurrence_id IS NULL OR contract_id IS NULL)
);

CREATE INDEX transactions_persona_booking_date ON transactions (persona_id, booking_date);
CREATE INDEX transactions_account ON transactions (account_id);

CREATE TABLE holdings (
    id             UUID          PRIMARY KEY,
    persona_id     VARCHAR(64)   NOT NULL,
    account_id     UUID          NOT NULL REFERENCES accounts (id),
    name           VARCHAR(120)  NOT NULL,
    asset_class    VARCHAR(32)   NOT NULL CHECK (asset_class IN ('EQUITY_ETF', 'BOND_ETF', 'MONEY_MARKET_FUND')),
    units          NUMERIC(18,6) NOT NULL,
    unit_price     NUMERIC(14,2) NOT NULL,
    valuation_date DATE          NOT NULL,
    cost_basis     NUMERIC(14,2) NOT NULL
);

CREATE INDEX holdings_persona ON holdings (persona_id);

CREATE TABLE savings_plans (
    id               UUID          PRIMARY KEY,
    persona_id       VARCHAR(64)   NOT NULL,
    account_id       UUID          NOT NULL REFERENCES accounts (id),
    debit_account_id UUID          NOT NULL REFERENCES accounts (id),
    name             VARCHAR(120)  NOT NULL,
    asset_class      VARCHAR(32)   NOT NULL CHECK (asset_class IN ('EQUITY_ETF', 'BOND_ETF', 'MONEY_MARKET_FUND')),
    amount           NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    day_of_month     SMALLINT      NOT NULL CHECK (day_of_month BETWEEN 1 AND 28),
    start_date       DATE          NOT NULL,
    recurrence_id    VARCHAR(64)   NOT NULL
);

CREATE INDEX savings_plans_persona ON savings_plans (persona_id);
