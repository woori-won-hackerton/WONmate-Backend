-- Existing entities explicitly reference these PostgreSQL type names.
-- Add missing types only; never replace types or remove existing enum labels.
BEGIN;
DO $$
BEGIN
    IF to_regtype('public.payment_frequency') IS NULL THEN
        CREATE TYPE public.payment_frequency AS ENUM ('monthly', 'quarterly', 'semiannual', 'annual', 'single');
    END IF;
    IF to_regtype('public.consultation_status') IS NULL THEN
        CREATE TYPE public.consultation_status AS ENUM ('draft', 'scheduled', 'completed', 'cancelled');
    END IF;
    IF to_regtype('public.contract_status') IS NULL THEN
        CREATE TYPE public.contract_status AS ENUM ('active', 'lapsed', 'terminated', 'matured');
    END IF;
    IF to_regtype('public.gender_code') IS NULL THEN
        CREATE TYPE public.gender_code AS ENUM ('male', 'female', 'other');
    END IF;
    IF to_regtype('public.assignment_status') IS NULL THEN
        CREATE TYPE public.assignment_status AS ENUM ('active', 'inactive');
    END IF;
END $$;
COMMIT;
