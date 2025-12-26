-- Critical FK indexes: eliminates sequential scans on JOIN operations
-- faixa_premiacao.concurso_id was causing 1.6B sequential tuple reads
CREATE INDEX IF NOT EXISTS idx_faixa_concurso_id ON faixa_premiacao(concurso_id);

-- ganhador_uf.concurso_id was causing 250M sequential tuple reads
CREATE INDEX IF NOT EXISTS idx_ganhador_concurso_id ON ganhador_uf(concurso_id);

-- Partial index for regional winner aggregation queries (GROUP BY uf)
CREATE INDEX IF NOT EXISTS idx_ganhador_uf_estado ON ganhador_uf(uf) WHERE uf IS NOT NULL;

-- Cleanup: idx_tipo_numero is redundant with uk_concurso_tipo_numero (same columns, unique covers all lookups)
DROP INDEX IF EXISTS idx_tipo_numero;
