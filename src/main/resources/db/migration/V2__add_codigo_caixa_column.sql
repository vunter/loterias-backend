-- Adiciona coluna codigo_caixa se não existir
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'time_timemania' AND column_name = 'codigo_caixa') THEN
        ALTER TABLE time_timemania ADD COLUMN codigo_caixa INTEGER;
    END IF;
END $$;

-- Atualizar codigo_caixa para times existentes baseado no nome_completo
UPDATE time_timemania SET codigo_caixa = 293 WHERE nome_completo = 'ABC/RN' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 805 WHERE nome_completo = 'ALTOS/PI' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 333 WHERE nome_completo = 'AMAZONAS/AM' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 38 WHERE nome_completo = 'AMERICA/MG' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 40 WHERE nome_completo = 'AMERICA/RN' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 389 WHERE nome_completo = 'APARECIDENSE/GO' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 830 WHERE nome_completo = 'ATHLETIC CLUB/MG' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 22 WHERE nome_completo = 'ATHLETICO/PR' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 143 WHERE nome_completo = 'ATLETICO/GO' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 3 WHERE nome_completo = 'ATLETICO/MG' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 18 WHERE nome_completo = 'AVAI/SC' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 20 WHERE nome_completo = 'BAHIA/BA' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 499 WHERE nome_completo = 'BAHIA DE FEIRA/BA' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 298 WHERE nome_completo = 'BOTAFOGO/PB' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 2 WHERE nome_completo = 'BOTAFOGO/RJ' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 42 WHERE nome_completo = 'BOTAFOGO/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 157 WHERE nome_completo = 'BRAGANTINO/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 232 WHERE nome_completo = 'BRASIL DE PELOTAS/RS' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 21 WHERE nome_completo = 'BRASILIENSE/DF' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 302 WHERE nome_completo = 'BRUSQUE/SC' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 297 WHERE nome_completo = 'CAMPINENSE/PB' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 951 WHERE nome_completo = 'CASCAVEL/PR' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 43 WHERE nome_completo = 'CAXIAS/RS' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 34 WHERE nome_completo = 'CEARA/CE' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 75 WHERE nome_completo = 'CEILANDIA ESPORTE/DF' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 250 WHERE nome_completo = 'CHAPECOENSE/SC' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 138 WHERE nome_completo = 'CONFIANCA/SE' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 10 WHERE nome_completo = 'CORINTHIANS/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 13 WHERE nome_completo = 'CORITIBA/PR' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 44 WHERE nome_completo = 'CRB/AL' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 19 WHERE nome_completo = 'CRICIUMA/SC' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 24 WHERE nome_completo = 'CRUZEIRO/MG' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 340 WHERE nome_completo = 'CSA/AL' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 609 WHERE nome_completo = 'CUIABA/MT' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 342 WHERE nome_completo = 'FERROVIARIA/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 145 WHERE nome_completo = 'FERROVIARIO/CE' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 23 WHERE nome_completo = 'FIGUEIRENSE/SC' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 4 WHERE nome_completo = 'FLAMENGO/RJ' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 824 WHERE nome_completo = 'FLORESTA/CE' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 11 WHERE nome_completo = 'FLUMINENSE/RJ' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 9 WHERE nome_completo = 'FORTALEZA/CE' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 8 WHERE nome_completo = 'GOIAS/GO' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 29 WHERE nome_completo = 'GREMIO/RS' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 31 WHERE nome_completo = 'GUARANI/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 25 WHERE nome_completo = 'INTERNACIONAL/RS' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 35 WHERE nome_completo = 'ITUANO/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 820 WHERE nome_completo = 'JACUIPENSE/BA' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 759 WHERE nome_completo = 'JUAZEIRENSE/BA' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 28 WHERE nome_completo = 'JUVENTUDE/RS' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 47 WHERE nome_completo = 'LONDRINA/PR' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 821 WHERE nome_completo = 'MANAUS/AM' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 311 WHERE nome_completo = 'MIRASSOL/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 16 WHERE nome_completo = 'NAUTICO/PE' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 150 WHERE nome_completo = 'NOVA IGUACU/RJ' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 808 WHERE nome_completo = 'NOVORIZONTINO/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 556 WHERE nome_completo = 'OESTE/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 526 WHERE nome_completo = 'OPERARIO/PR' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 1 WHERE nome_completo = 'PALMEIRAS/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 12 WHERE nome_completo = 'PARANA/PR' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 14 WHERE nome_completo = 'PAYSANDU/PA' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 5 WHERE nome_completo = 'PONTE PRETA/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 149 WHERE nome_completo = 'PORTUGUESA/RJ' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 831 WHERE nome_completo = 'POUSO ALEGRE/MG' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 49 WHERE nome_completo = 'REMO/PA' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 958 WHERE nome_completo = 'RETRO BRASIL/PE' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 364 WHERE nome_completo = 'SAMP CORREA/MA' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 50 WHERE nome_completo = 'SANTA CRUZ/PE' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 27 WHERE nome_completo = 'SANTOS/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 76 WHERE nome_completo = 'SAO BERNARDO/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 551 WHERE nome_completo = 'SAO JOSE/RS' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 6 WHERE nome_completo = 'SAO PAULO/SP' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 934 WHERE nome_completo = 'SAORAIMUNDO/RR' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 33 WHERE nome_completo = 'SPORT/PE' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 215 WHERE nome_completo = 'TOCANTINOPOLIS/TO' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 653 WHERE nome_completo = 'TOMBENSE/MG' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 7 WHERE nome_completo = 'VASCO DA GAMA/RJ' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 53 WHERE nome_completo = 'VILA NOVA/GO' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 15 WHERE nome_completo = 'VITORIA/BA' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 55 WHERE nome_completo = 'VOLTA REDONDA/RJ' AND codigo_caixa IS NULL;
UPDATE time_timemania SET codigo_caixa = 656 WHERE nome_completo = 'YPIRANGA/RS' AND codigo_caixa IS NULL;

-- Tornar a coluna NOT NULL e adicionar UNIQUE constraint
DO $$
BEGIN
    ALTER TABLE time_timemania ALTER COLUMN codigo_caixa SET NOT NULL;
EXCEPTION
    WHEN others THEN NULL;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'time_timemania_codigo_caixa_key') THEN
        ALTER TABLE time_timemania ADD CONSTRAINT time_timemania_codigo_caixa_key UNIQUE (codigo_caixa);
    END IF;
EXCEPTION
    WHEN others THEN NULL;
END $$;
