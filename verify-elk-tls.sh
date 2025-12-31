#!/bin/bash
# Script de vérification du flux de logging ELK avec TLS/HTTPS
#
# SECURITY NOTE: Default password "changeme" is for DEVELOPMENT ONLY.
# In production, set ELASTIC_PASSWORD environment variable.

echo "🔐 Vérification du flux de logging ELK avec TLS/HTTPS"
echo "======================================================"
echo ""

# Couleurs
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

ERRORS=0

# 1. Vérifier que les services sont en cours d'exécution
echo "1️⃣  Vérification des services Docker..."
echo "-----------------------------------"
services=("elasticsearch" "kibana" "logstash")
for service in "${services[@]}"; do
    if docker compose ps | grep -q "$service.*Up\|healthy"; then
        echo -e "${GREEN}✅${NC} $service est en cours d'exécution"
    else
        echo -e "${RED}❌${NC} $service n'est PAS en cours d'exécution"
        ((ERRORS++))
    fi
done
echo ""

# 2. Vérifier que Elasticsearch utilise HTTPS
echo "2️⃣  Vérification TLS/HTTPS Elasticsearch..."
echo "-----------------------------------"
# SECURITY NOTE: Using default password "changeme" for DEV. In production, use ELASTIC_PASSWORD env var.
ELASTIC_PASS="${ELASTIC_PASSWORD:-changeme}"
ES_RESPONSE=$(docker compose exec -T elasticsearch curl -s -k -u "elastic:${ELASTIC_PASS}" "https://localhost:9200/_cluster/health" 2>&1)
if echo "$ES_RESPONSE" | grep -q "cluster_name"; then
    echo -e "${GREEN}✅${NC} Elasticsearch répond en HTTPS"
else
    echo -e "${RED}❌${NC} Elasticsearch ne répond pas en HTTPS"
    echo "   Réponse: $ES_RESPONSE"
    ((ERRORS++))
fi
echo ""

# 3. Vérifier que Logstash envoie vers Elasticsearch en HTTPS
echo "3️⃣  Vérification connexion Logstash → Elasticsearch (HTTPS)..."
echo "-----------------------------------"
LS_LOGS=$(docker compose logs logstash --tail=200 | grep -i "elasticsearch output\|https://elasticsearch" | tail -3)
if echo "$LS_LOGS" | grep -q "https://elasticsearch"; then
    echo -e "${GREEN}✅${NC} Logstash est configuré pour utiliser HTTPS vers Elasticsearch"
    echo "$LS_LOGS" | head -2
else
    echo -e "${YELLOW}⚠️${NC}  Aucune confirmation trouvée dans les logs (peut être normal si pas récent)"
fi
echo ""

# 4. Vérifier que Kibana se connecte à Elasticsearch en HTTPS
echo "4️⃣  Vérification connexion Kibana → Elasticsearch (HTTPS)..."
echo "-----------------------------------"
KIBANA_LOGS=$(docker compose logs kibana --tail=100 | grep -i "unable to authenticate\|security_exception\|https://elasticsearch" | tail -3)
if echo "$KIBANA_LOGS" | grep -q "unable to authenticate\|security_exception"; then
    echo -e "${RED}❌${NC} Kibana ne peut pas s'authentifier auprès d'Elasticsearch"
    echo "$KIBANA_LOGS" | head -2
    ((ERRORS++))
else
    echo -e "${GREEN}✅${NC} Kibana se connecte correctement à Elasticsearch (plus d'erreurs d'authentification)"
fi
echo ""

# 5. Vérifier que les indices Elasticsearch existent et contiennent des données
echo "5️⃣  Vérification des indices Elasticsearch..."
echo "-----------------------------------"
# SECURITY NOTE: Using default password "changeme" for DEV. In production, use ELASTIC_PASSWORD env var.
INDICES=$(docker compose exec -T elasticsearch curl -s -k -u "elastic:${ELASTIC_PASS}" "https://localhost:9200/_cat/indices/healthsec*?v" 2>&1)
if echo "$INDICES" | grep -q "healthsec"; then
    echo -e "${GREEN}✅${NC} Les indices healthsec existent"
    echo "$INDICES" | grep "healthsec" | head -3
    DOC_COUNT=$(echo "$INDICES" | grep "healthsec" | awk '{sum+=$7} END {print sum}')
    if [ "$DOC_COUNT" -gt 0 ]; then
        echo -e "${GREEN}   ✅${NC} Total: $DOC_COUNT documents dans les indices"
    else
        echo -e "${YELLOW}   ⚠️${NC}  Aucun document trouvé (normal si aucun log n'a été envoyé)"
    fi
else
    echo -e "${YELLOW}⚠️${NC}  Aucun index healthsec trouvé (normal si aucun log n'a été envoyé)"
fi
echo ""

# 6. Vérifier qu'il n'y a pas de connexions HTTP
echo "6️⃣  Vérification absence de connexions HTTP non sécurisées..."
echo "-----------------------------------"
HTTP_ATTEMPTS_ES=$(docker compose logs elasticsearch --since=5m 2>/dev/null | grep -c "received plaintext http" 2>/dev/null || echo "0")
HTTP_ATTEMPTS_LS=$(docker compose logs logstash --since=5m 2>/dev/null | grep -c "http://elasticsearch" 2>/dev/null || echo "0")

# Nettoyer les valeurs (enlever les espaces/newlines)
HTTP_ATTEMPTS_ES=$(echo "$HTTP_ATTEMPTS_ES" | tr -d ' \n')
HTTP_ATTEMPTS_LS=$(echo "$HTTP_ATTEMPTS_LS" | tr -d ' \n')

if [ "${HTTP_ATTEMPTS_ES:-0}" -eq 0 ] && [ "${HTTP_ATTEMPTS_LS:-0}" -eq 0 ]; then
    echo -e "${GREEN}✅${NC} Aucune tentative de connexion HTTP détectée"
else
    echo -e "${RED}❌${NC} Des tentatives de connexion HTTP ont été détectées:"
    echo "   Elasticsearch: $HTTP_ATTEMPTS_ES tentatives"
    echo "   Logstash: $HTTP_ATTEMPTS_LS tentatives"
    ((ERRORS++))
fi
echo ""

# 7. Vérifier la configuration Logstash
echo "7️⃣  Vérification configuration Logstash..."
echo "-----------------------------------"
if [ -f "logstash/pipeline/logstash.conf" ]; then
    # Vérifier input HTTP (HTTPS pour frontend)
    HTTP_SSL=$(grep -q "ssl => true" logstash/pipeline/logstash.conf && echo "ok" || echo "")
    # Vérifier output Elasticsearch (HTTPS)
    ES_HTTPS=$(grep -q "https://elasticsearch:9200" logstash/pipeline/logstash.conf && echo "ok" || echo "")
    ES_SSL=$(grep -q "ssl_enabled => true" logstash/pipeline/logstash.conf && echo "ok" || echo "")
    
    if [ -n "$HTTP_SSL" ] && [ -n "$ES_HTTPS" ] && [ -n "$ES_SSL" ]; then
        echo -e "${GREEN}✅${NC} Configuration Logstash utilise HTTPS:"
        echo "   • Input HTTP (frontend): SSL activé"
        echo "   • Output Elasticsearch: HTTPS avec SSL activé"
    else
        echo -e "${RED}❌${NC} Configuration Logstash n'utilise pas HTTPS correctement"
        [ -z "$HTTP_SSL" ] && echo "   • Input HTTP SSL manquant"
        [ -z "$ES_HTTPS" ] && echo "   • Output Elasticsearch HTTPS manquant"
        [ -z "$ES_SSL" ] && echo "   • Output Elasticsearch SSL manquant"
        ((ERRORS++))
    fi
else
    echo -e "${RED}❌${NC} Fichier de configuration Logstash non trouvé"
    ((ERRORS++))
fi
echo ""

# 8. Vérifier la configuration Kibana
echo "8️⃣  Vérification configuration Kibana..."
echo "-----------------------------------"
KIBANA_HOSTS=$(docker compose config 2>/dev/null | grep -A 5 "ELASTICSEARCH_HOSTS" | grep -o "https://elasticsearch" || echo "")
if [ -n "$KIBANA_HOSTS" ]; then
    echo -e "${GREEN}✅${NC} Kibana est configuré pour utiliser HTTPS vers Elasticsearch"
else
    echo -e "${RED}❌${NC} Kibana n'est pas configuré pour HTTPS"
    ((ERRORS++))
fi
echo ""

# Résumé
echo "======================================================"
echo "📊 RÉSUMÉ"
echo "======================================================"
if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✅ TOUT EST CORRECT${NC}"
    echo "Le flux de logging ELK fonctionne avec TLS/HTTPS:"
    echo "  • Logs → Logstash (HTTPS pour frontend)"
    echo "  • Logstash → Elasticsearch (HTTPS)"
    echo "  • Kibana → Elasticsearch (HTTPS)"
    exit 0
else
    echo -e "${RED}❌ $ERRORS PROBLÈME(S) DÉTECTÉ(S)${NC}"
    exit 1
fi

