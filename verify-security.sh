#!/bin/bash

# Security Verification Script for HealthSec Project
# This script performs automated security checks and verification
# Date: December 30, 2025
# Updated: After security fixes (FileTypeValidator → EncryptedFileValidator, zero-trust file upload)

# Do not exit on first failure so we can show the full report
set +e

echo "🔐 HealthSec Security Verification Script"
echo "=========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
PASSED=0
FAILED=0
WARNINGS=0

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to print status
print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ PASS${NC}: $2"
        ((PASSED++))
    else
        echo -e "${RED}❌ FAIL${NC}: $2"
        ((FAILED++))
    fi
}

print_warning() {
    echo -e "${YELLOW}⚠️  WARNING${NC}: $1"
    ((WARNINGS++))
}

# 1. Check Docker services are running
echo "1️⃣  Checking Docker Services..."
echo "-----------------------------------"

docker compose ps > /dev/null 2>&1
if [ $? -eq 0 ]; then
    # Check each critical service
    services=("backend" "frontend" "postgres" "keycloak" "elasticsearch" "kibana" "logstash" "nginx")
    for service in "${services[@]}"; do
        if docker compose ps | grep -q "$service.*Up\|healthy"; then
            print_status 0 "$service is running"
        else
            print_status 1 "$service is NOT running"
        fi
    done
else
    print_status 1 "Docker Compose not running"
fi

echo ""

# 2. Check TLS/HTTPS Configuration
echo "2️⃣  Checking TLS/HTTPS Configuration..."
echo "-----------------------------------"

# Check backend HTTPS through nginx (accept 200/401/403)
BACKEND_STATUS=$(curl -k -s -o /dev/null -w "%{http_code}" https://localhost/api/actuator/health)
if [[ "$BACKEND_STATUS" == "200" || "$BACKEND_STATUS" == "401" || "$BACKEND_STATUS" == "403" ]]; then
    print_status 0 "Backend HTTPS responding (status $BACKEND_STATUS)"
else
    print_status 1 "Backend HTTPS not responding (status $BACKEND_STATUS)"
fi

# Check Elasticsearch HTTPS
if docker compose exec -T elasticsearch curl -s -k -u elastic:changeme https://localhost:9200 > /dev/null 2>&1; then
    print_status 0 "Elasticsearch HTTPS + auth working"
else
    print_status 1 "Elasticsearch HTTPS not working"
fi

# Check Kibana HTTPS
if curl -k -s https://localhost:5601/api/status > /dev/null 2>&1; then
    print_status 0 "Kibana HTTPS responding"
else
    print_status 1 "Kibana HTTPS not responding"
fi

# Check nginx HTTPS
if curl -k -s https://localhost/ > /dev/null 2>&1; then
    print_status 0 "Nginx HTTPS responding"
else
    print_status 1 "Nginx HTTPS not responding"
fi

echo ""

# 3. Check Security Headers
echo "3️⃣  Checking Security Headers..."
echo "-----------------------------------"

HEADERS=$(curl -k -I -s https://localhost/)

if echo "$HEADERS" | grep -q "Strict-Transport-Security"; then
    print_status 0 "HSTS header present"
else
    print_status 1 "HSTS header missing"
fi

if echo "$HEADERS" | grep -q "X-Content-Type-Options"; then
    print_status 0 "X-Content-Type-Options header present"
else
    print_status 1 "X-Content-Type-Options header missing"
fi

if echo "$HEADERS" | grep -q "X-Frame-Options"; then
    print_status 0 "X-Frame-Options header present"
else
    print_status 1 "X-Frame-Options header missing"
fi

if echo "$HEADERS" | grep -q "Content-Security-Policy"; then
    print_status 0 "CSP header present"
else
    print_status 1 "CSP header missing"
fi

echo ""

# 4. Check JWT Configuration
echo "4️⃣  Checking JWT Configuration..."
echo "-----------------------------------"

if grep -q "issuer-uri" backend/src/main/resources/application.yml; then
    print_status 0 "JWT issuer-uri configured"
else
    print_status 1 "JWT issuer-uri not configured"
fi

if grep -q "jwk-set-uri" backend/src/main/resources/application.yml; then
    print_status 0 "JWT jwk-set-uri configured"
else
    print_status 1 "JWT jwk-set-uri not configured"
fi

echo ""

# 5. Check Encryption Implementation
echo "5️⃣  Checking Encryption Implementation..."
echo "-----------------------------------"

if grep -q "AES-GCM" frontend/src/app/core/services/crypto.service.ts; then
    print_status 0 "AES-GCM encryption found in frontend"
else
    print_status 1 "AES-GCM encryption not found"
fi

if grep -q "RSA-OAEP" frontend/src/app/core/services/crypto.service.ts; then
    print_status 0 "RSA-OAEP encryption found in frontend"
else
    print_status 1 "RSA-OAEP encryption not found"
fi

echo ""

# 6. Check Authorization
echo "6️⃣  Checking Authorization..."
echo "-----------------------------------"

PREAUTHORIZE_COUNT=$(grep -r "@PreAuthorize" backend/src/main/java --include="*.java" | wc -l)
if [ "$PREAUTHORIZE_COUNT" -gt 0 ]; then
    print_status 0 "Role-based authorization found ($PREAUTHORIZE_COUNT usages)"
else
    print_status 1 "No role-based authorization found"
fi

AUTH_PRINCIPAL_COUNT=$(grep -r "@AuthenticationPrincipal" backend/src/main/java --include="*.java" | wc -l)
if [ "$AUTH_PRINCIPAL_COUNT" -gt 0 ]; then
    print_status 0 "JWT authentication found ($AUTH_PRINCIPAL_COUNT usages)"
else
    print_status 1 "No JWT authentication found"
fi

echo ""

# 7. Check Input Validation
echo "7️⃣  Checking Input Validation..."
echo "-----------------------------------"

# Check EncryptedFileValidator (replaces FileTypeValidator for encrypted files only)
if grep -r "EncryptedFileValidator" backend/src/main/java/be/he2b/healthsec > /dev/null 2>&1; then
    print_status 0 "Encrypted file validation implemented"
else
    print_status 1 "Encrypted file validation not found"
fi

# Check that all file uploads require .enc extension (zero-trust)
if grep -E "endsWith.*\.enc|UNENCRYPTED_FILE_REJECTED" backend/src/main/java/be/he2b/healthsec/medical_records/controller/MedicalFileController.java > /dev/null 2>&1; then
    print_status 0 "File upload requires .enc extension (zero-trust enforced)"
else
    print_status 1 "File upload .enc requirement not found"
fi

if grep -r "@Valid" backend/src/main/java/be/he2b/healthsec > /dev/null 2>&1; then
    print_status 0 "Bean validation (@Valid) found"
else
    print_status 1 "Bean validation not found"
fi

echo ""

# 8. Check Rate Limiting
echo "8️⃣  Checking Rate Limiting..."
echo "-----------------------------------"

if grep -r "RateLimitService" backend/src/main/java/be/he2b/healthsec > /dev/null 2>&1; then
    print_status 0 "Rate limiting service found"
else
    print_status 1 "Rate limiting service not found"
fi

echo ""

# 9. Check Inactivity Timeout
echo "9️⃣  Checking Inactivity Timeout..."
echo "-----------------------------------"

if grep -q "InactivityTimeoutService" frontend/src/app/core/services/inactivity-timeout.service.ts; then
    print_status 0 "Inactivity timeout service found"
else
    print_status 1 "Inactivity timeout service not found"
fi

echo ""

# 10. Check Logging Configuration
echo "🔟 Checking Logging Configuration..."
echo "-----------------------------------"

# Check if Elasticsearch is indexing logs
ES_INDICES=$(docker compose exec -T elasticsearch curl -s -k -u elastic:changeme https://localhost:9200/_cat/indices?v 2>/dev/null)
if echo "$ES_INDICES" | grep -q "healthsec"; then
    print_status 0 "Elasticsearch healthsec index exists"
else
    print_warning "Elasticsearch healthsec index not found (logs may not be flowing yet)"
fi

echo ""

# 11. Check for SQL Injection Vulnerabilities
echo "1️⃣1️⃣  Checking for SQL Injection Vulnerabilities..."
echo "-----------------------------------"

NATIVE_QUERY_COUNT=$(grep -r "createNativeQuery" backend/src/main/java --include="*.java" | grep -v "test" | wc -l)
if [ "$NATIVE_QUERY_COUNT" -eq 0 ]; then
    print_status 0 "No native SQL queries found (using JPA)"
else
    print_warning "Found $NATIVE_QUERY_COUNT native SQL queries (manual review recommended)"
fi

echo ""

# 12. Check Elasticsearch Security
echo "1️⃣2️⃣  Checking Elasticsearch Security..."
echo "-----------------------------------"

if grep -q "xpack.security.enabled=true" docker-compose.yml; then
    print_status 0 "Elasticsearch xpack security enabled"
else
    print_status 1 "Elasticsearch xpack security not enabled"
fi

if grep -q "ELASTIC_PASSWORD" .env 2>/dev/null; then
    print_status 0 "Elasticsearch password configured in .env"
else
    print_status 1 "Elasticsearch password not found in .env"
fi

if grep -q "KIBANA_PASSWORD" .env 2>/dev/null; then
    print_status 0 "Kibana password configured in .env"
else
    print_status 1 "Kibana password not found in .env"
fi

echo ""

# 13. Check Certificate Files
echo "1️⃣3️⃣  Checking Certificate Files..."
echo "-----------------------------------"

CERT_FILES=("ca.crt" "backend.crt" "keycloak.crt" "elasticsearch.crt" "kibana.crt" "logstash.crt")
for cert in "${CERT_FILES[@]}"; do
    if docker compose exec -T internal_certs sh -c "test -f /certs/internal/$cert" 2>/dev/null; then
        print_status 0 "Certificate $cert exists"
    else
        print_status 1 "Certificate $cert not found"
    fi
done

echo ""

# 14. Check for Hardcoded Secrets
echo "1️⃣4️⃣  Checking for Hardcoded Secrets..."
echo "-----------------------------------"

HARDCODED_SECRETS=$(grep -r "password.*=" backend/src/main/resources --include="*.yml" --include="*.properties" | grep -v "ELASTIC_PASSWORD" | grep -v "#" | wc -l)
if [ "$HARDCODED_SECRETS" -eq 0 ]; then
    print_status 0 "No hardcoded passwords in config files"
else
    print_warning "Found $HARDCODED_SECRETS potential hardcoded passwords (review recommended)"
fi

echo ""

# 15. Summary
echo "=========================================="
echo "📊 VERIFICATION SUMMARY"
echo "=========================================="
echo ""
echo -e "✅ ${GREEN}PASSED${NC}: $PASSED"
echo -e "❌ ${RED}FAILED${NC}: $FAILED"
echo -e "⚠️  ${YELLOW}WARNINGS${NC}: $WARNINGS"
echo ""

TOTAL=$((PASSED + FAILED))
if [ $TOTAL -gt 0 ]; then
    PERCENTAGE=$((PASSED * 100 / TOTAL))
    echo "Security Score: $PERCENTAGE%"
    echo ""
    
    if [ $PERCENTAGE -ge 90 ]; then
        echo -e "${GREEN}🎉 EXCELLENT SECURITY POSTURE${NC}"
    elif [ $PERCENTAGE -ge 70 ]; then
        echo -e "${YELLOW}⚠️  GOOD BUT NEEDS IMPROVEMENT${NC}"
    else
        echo -e "${RED}❌ CRITICAL ISSUES FOUND${NC}"
    fi
fi

echo ""
echo "For detailed security checklist, see: SECURITY_CHECKLIST.md"
echo "For verification guide, see: SECURITY_VERIFICATION.md"
echo "For cleanup report, see: CLEANUP_REPORT.md"
echo ""

# Exit with appropriate code
if [ $FAILED -eq 0 ]; then
    exit 0
else
    exit 1
fi
