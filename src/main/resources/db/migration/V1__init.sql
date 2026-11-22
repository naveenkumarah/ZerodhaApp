-- TradeDiscipline Pro - initial schema (MySQL 8.0+, InnoDB, utf8mb4)
CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(120)  NOT NULL,
    email           VARCHAR(255)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100)  NOT NULL,
    kite_user_id    VARCHAR(32),
    access_token    VARCHAR(2048),          -- AES-256-GCM encrypted
    refresh_token   VARCHAR(2048),          -- AES-256-GCM encrypted
    created_at      DATETIME(6)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE risk_profiles (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE,
    capital             DECIMAL(19,2) NOT NULL DEFAULT 0,
    risk_percentage     DECIMAL(6,2)  NOT NULL DEFAULT 1,
    daily_loss_limit    DECIMAL(6,2)  NOT NULL DEFAULT 3,
    weekly_loss_limit   DECIMAL(6,2)  NOT NULL DEFAULT 6,
    monthly_loss_limit  DECIMAL(6,2)  NOT NULL DEFAULT 10,
    CONSTRAINT fk_risk_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE trades (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    symbol       VARCHAR(64) NOT NULL,
    entry_price  DECIMAL(19,4) NOT NULL,
    qty          INT NOT NULL,
    stop_loss    DECIMAL(19,4),
    target       DECIMAL(19,4),
    trade_type   VARCHAR(4)  NOT NULL,
    strategy     VARCHAR(32) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    pnl          DECIMAL(19,2),
    created_at   DATETIME(6) NOT NULL,
    closed_at    DATETIME(6),
    CONSTRAINT fk_trades_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_trades_qty CHECK (qty > 0),
    CONSTRAINT chk_trades_type CHECK (trade_type IN ('BUY', 'SELL')),
    CONSTRAINT chk_trades_status CHECK (status IN ('OPEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_trades_user_created ON trades (user_id, created_at);
CREATE INDEX idx_trades_user_symbol_status ON trades (user_id, symbol, status);
CREATE TABLE journals (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT NOT NULL,
    trade_id              BIGINT NOT NULL,
    emotion_before        VARCHAR(16),
    emotion_after         VARCHAR(16),
    lessons               VARCHAR(4000),
    mistakes              VARCHAR(4000),
    notes                 VARCHAR(4000),
    entry_screenshot_url  VARCHAR(512),
    exit_screenshot_url   VARCHAR(512),
    CONSTRAINT fk_journals_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_journals_trade FOREIGN KEY (trade_id) REFERENCES trades (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_journals_user ON journals (user_id);
