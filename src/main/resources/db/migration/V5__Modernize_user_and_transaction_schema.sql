DO $$
DECLARE
    foreign_key RECORD;
BEGIN
    FOR foreign_key IN
        SELECT constraint_name
        FROM information_schema.table_constraints
        WHERE table_name = 'transaction_tb'
          AND table_schema = current_schema()
          AND constraint_type = 'FOREIGN KEY'
    LOOP
        EXECUTE format('ALTER TABLE transaction_tb DROP CONSTRAINT %I', foreign_key.constraint_name);
    END LOOP;
END $$;

ALTER TABLE user_tb ALTER COLUMN id TYPE BIGINT;
ALTER TABLE transaction_tb ALTER COLUMN id TYPE BIGINT;
ALTER TABLE transaction_tb ALTER COLUMN payer_id TYPE BIGINT;
ALTER TABLE transaction_tb ALTER COLUMN payee_id TYPE BIGINT;

ALTER TABLE user_tb ALTER COLUMN complete_name TYPE VARCHAR(100);
ALTER TABLE user_tb ALTER COLUMN email TYPE VARCHAR(254);
ALTER TABLE user_tb ALTER COLUMN cpf DROP NOT NULL;
ALTER TABLE user_tb ALTER COLUMN cnpj DROP NOT NULL;
ALTER TABLE user_tb ADD COLUMN document VARCHAR(14);
UPDATE user_tb
SET document = COALESCE(NULLIF(cpf, ''), NULLIF(cnpj, ''));
ALTER TABLE user_tb DROP COLUMN cpf;
ALTER TABLE user_tb DROP COLUMN cnpj;
CREATE UNIQUE INDEX uk_user_document ON user_tb(document);

ALTER TABLE user_tb ALTER COLUMN wallet TYPE NUMERIC(19, 2)
    USING wallet::NUMERIC(19, 2);
ALTER TABLE user_tb ADD CONSTRAINT ck_user_wallet_nonnegative CHECK (wallet >= 0);

ALTER TABLE user_tb ALTER COLUMN user_type TYPE VARCHAR(20)
    USING user_type::TEXT;
DROP TYPE user_type;

ALTER TABLE user_tb ADD COLUMN password_hash VARCHAR(100);
UPDATE user_tb SET password_hash = '!' WHERE password_hash IS NULL;
ALTER TABLE user_tb ALTER COLUMN password_hash SET NOT NULL;
ALTER TABLE user_tb ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE user_tb ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE transaction_tb ADD COLUMN amount NUMERIC(19, 2);
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'transaction_tb'
          AND column_name = 'transferred_value'
    ) THEN
        EXECUTE 'UPDATE transaction_tb SET amount = transferred_value::NUMERIC(19, 2) WHERE amount IS NULL';
        ALTER TABLE transaction_tb DROP COLUMN transferred_value;
    END IF;
END $$;
ALTER TABLE transaction_tb ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE transaction_tb ADD COLUMN transaction_type VARCHAR(20) NOT NULL DEFAULT 'TRANSFER';
ALTER TABLE transaction_tb ALTER COLUMN transfer_date TYPE TIMESTAMP WITH TIME ZONE
    USING transfer_date AT TIME ZONE 'UTC';
ALTER TABLE transaction_tb ALTER COLUMN transfer_date SET NOT NULL;
ALTER TABLE transaction_tb ADD CONSTRAINT ck_transaction_amount_positive
    CHECK (amount IS NULL OR amount > 0);

ALTER TABLE transaction_tb
    ADD CONSTRAINT fk_payer_id_user_id FOREIGN KEY (payer_id) REFERENCES user_tb(id);
ALTER TABLE transaction_tb
    ADD CONSTRAINT fk_payee_id_user_id FOREIGN KEY (payee_id) REFERENCES user_tb(id);

CREATE INDEX idx_transaction_payer_date ON transaction_tb(payer_id, transfer_date);
CREATE INDEX idx_transaction_payee_date ON transaction_tb(payee_id, transfer_date);
