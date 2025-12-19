#!/bin/bash

# Script pour interroger facilement la base de données PostgreSQL
# Affiche tous les attributs de chaque modèle

DB_CONTAINER="postgres_app"
DB_USER="app"
DB_NAME="mydb"

echo "=========================================="
echo "=== Tables disponibles ==="
echo "=========================================="
docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -c "\dt"

echo ""
echo "=========================================="
echo "=== USERS (Tous les attributs) ==="
echo "=========================================="
docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -c "
SELECT 
    id,
    keycloak_id,
    user_type_role as role,
    CASE 
        WHEN public_key IS NULL THEN 'NULL'
        ELSE LENGTH(public_key)::text || ' chars (' || SUBSTRING(public_key, 1, 30) || '...)'
    END as public_key_info,
    created_at,
    updated_at
FROM users
ORDER BY created_at DESC;
"

echo ""
echo "=========================================="
echo "=== PATIENTS (Tous les attributs) ==="
echo "=========================================="
docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -c "
SELECT 
    p.id,
    u.keycloak_id,
    u.user_type_role as role,
    encode(p.first_name_enc, 'hex') as first_name_enc_hex,
    encode(p.last_name_enc, 'hex') as last_name_enc_hex,
    encode(p.email_enc, 'hex') as email_enc_hex,
    encode(p.dob_enc, 'hex') as dob_enc_hex,
    CASE 
        WHEN u.public_key IS NULL THEN 'NULL'
        ELSE SUBSTRING(u.public_key, 1, 50) || '...'
    END as public_key_preview,
    (SELECT COUNT(*) FROM patient_doctor pd WHERE pd.patient_id = p.id) as nb_doctors
FROM patients p
JOIN users u ON p.id = u.id
ORDER BY u.created_at DESC;
"

echo ""
echo "=========================================="
echo "=== DOCTORS (Tous les attributs) ==="
echo "=========================================="
docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -c "
SELECT 
    d.id,
    u.keycloak_id,
    u.user_type_role as role,
    d.first_name,
    d.last_name,
    d.email,
    d.medical_organisation,
    CASE 
        WHEN u.public_key IS NULL THEN 'NULL'
        ELSE LENGTH(u.public_key)::text || ' chars'
    END as public_key_length,
    u.created_at,
    u.updated_at,
    (SELECT COUNT(*) FROM patient_doctor pd WHERE pd.doctor_id = d.id) as nb_patients
FROM doctors d
JOIN users u ON d.id = u.id
ORDER BY u.created_at DESC;
"

echo ""
echo "=========================================="
echo "=== PATIENT_DOCTOR (Tous les attributs) ==="
echo "=========================================="
docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -c "
SELECT 
    pd.patient_id,
    pd.doctor_id,
    d.first_name || ' ' || d.last_name as doctor_name,
    pd.approved_by_patient,
    pd.appointed_at,
    CASE 
        WHEN pd.encrypted_sym_key_for_doctor IS NULL THEN 'NULL'
        ELSE SUBSTRING(encode(pd.encrypted_sym_key_for_doctor, 'hex'), 1, 60) || '... (' || LENGTH(pd.encrypted_sym_key_for_doctor) || ' bytes)'
    END as encrypted_aes_key_preview
FROM patient_doctor pd
JOIN doctors d ON pd.doctor_id = d.id
ORDER BY pd.appointed_at DESC NULLS LAST;
"

echo ""
echo "=========================================="
echo "=== Détails des données chiffrées ==="
echo "=========================================="
docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -c "
SELECT 
    'Prénom patient (chiffré)' as data_type,
    COUNT(*) FILTER (WHERE first_name_enc IS NOT NULL) as count_with_data,
    AVG(LENGTH(first_name_enc)) FILTER (WHERE first_name_enc IS NOT NULL)::int as avg_length_bytes
FROM patients
UNION ALL
SELECT 
    'Nom patient (chiffré)',
    COUNT(*) FILTER (WHERE last_name_enc IS NOT NULL),
    AVG(LENGTH(last_name_enc)) FILTER (WHERE last_name_enc IS NOT NULL)::int
FROM patients
UNION ALL
SELECT 
    'Email patient (chiffré)',
    COUNT(*) FILTER (WHERE email_enc IS NOT NULL),
    AVG(LENGTH(email_enc)) FILTER (WHERE email_enc IS NOT NULL)::int
FROM patients
UNION ALL
SELECT 
    'Date de naissance patient (chiffrée)',
    COUNT(*) FILTER (WHERE dob_enc IS NOT NULL),
    AVG(LENGTH(dob_enc)) FILTER (WHERE dob_enc IS NOT NULL)::int
FROM patients
UNION ALL
SELECT 
    'Clé AES patient (chiffrée pour médecin)',
    COUNT(*) FILTER (WHERE encrypted_sym_key_for_doctor IS NOT NULL),
    AVG(LENGTH(encrypted_sym_key_for_doctor)) FILTER (WHERE encrypted_sym_key_for_doctor IS NOT NULL)::int
FROM patient_doctor;
"

echo ""
echo "=========================================="
echo "=== Résumé des relations ==="
echo "=========================================="
docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -c "
SELECT 
    'Total Users' as metric,
    COUNT(*)::text as value
FROM users
UNION ALL
SELECT 
    'Total Patients',
    COUNT(*)::text
FROM patients
UNION ALL
SELECT 
    'Total Doctors',
    COUNT(*)::text
FROM doctors
UNION ALL
SELECT 
    'Relations Patient-Doctor',
    COUNT(*)::text
FROM patient_doctor
UNION ALL
SELECT 
    'Patients avec médecins',
    COUNT(DISTINCT patient_id)::text
FROM patient_doctor
UNION ALL
SELECT 
    'Médecins avec patients',
    COUNT(DISTINCT doctor_id)::text
FROM patient_doctor;
"
