#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# WhatsApp / Evolution / DB Local Diagnostic Script
# CONFIGURACIÓN COMPLETA DEL ENTORNO REAL
# ============================================================

# ============================================================
# CONFIGURACIÓN - VALORES REALES DEL ENTORNO
# ============================================================

# URLs de los servicios
EVOLUTION_URL="https://evolution-api-1068521170727.us-central1.run.app"
ENGINE_URL="https://engine-service-1068521170727.us-central1.run.app"

# Configuración del tenant
TENANT_ID="alamodaperu.online"

# API Key de Evolution (de las variables de entorno)
API_KEY="alamoda-key-2026"

# URL de la base de datos (de DATABASE_CONNECTION_URI)
DATABASE_URL="postgres://postgres.yqiwrvrameuskwtvlctd:%241988DEza....@aws-0-us-west-1.pooler.supabase.com:6543/postgres?sslmode=require&pgbouncer=true&connection_limit=10"

# Configuración de webhook (de las variables de entorno)
WEBHOOK_PATH="/public/v1/whatsapp/webhook"
WEBHOOK_URL="https://engine-service-1068521170727.us-central1.run.app/public/v1/whatsapp/webhook"

# Configuración de Evolution (de las variables de entorno)
WEBHOOK_EVENTS="MESSAGES_UPSERT"
STORE_MESSAGES="false"
STORE_CONTACTS="false"
STORE_CHATS="false"
GROUPS_IGNORE="true"
SKIP_HISTORY_SYNC="false"
CONFIG_SESSION_SAVE="false"

# Endpoints del engine
QR_PATH="/private/v1/whatsapp/qr"
STATUS_PATH="/public/v1/whatsapp/status"

# ============================================================
# FIN DE LA CONFIGURACIÓN - NO MODIFICAR DEBAJO DE ESTA LÍNEA
# ============================================================

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Funciones de logging
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_ok() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_err() { echo -e "${RED}[ERROR]${NC} $1"; }
log_success() { echo -e "${GREEN}✅${NC} $1"; }
log_fail() { echo -e "${RED}❌${NC} $1"; }
log_separator() { echo -e "\n${CYAN}════════════════════════════════════════════════════════════════${NC}"; }

# Verificar herramientas necesarias
check_tools() {
    log_info "Verificando herramientas locales..."
    
    local missing_tools=()
    
    if ! command -v curl >/dev/null 2>&1; then
        missing_tools+=("curl")
    else
        log_ok "✅ curl encontrado"
    fi
    
    if ! command -v jq >/dev/null 2>&1; then
        log_warn "⚠️  jq no instalado - los JSON se mostrarán sin formatear"
    else
        log_ok "✅ jq encontrado"
    fi
    
    if ! command -v psql >/dev/null 2>&1; then
        if command -v docker >/dev/null 2>&1; then
            log_ok "✅ docker encontrado (se usará para psql)"
        else
            log_warn "⚠️  No se encontró psql ni docker - no se podrá verificar BD"
        fi
    else
        log_ok "✅ psql encontrado"
    fi
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        log_err "❌ Faltan herramientas: ${missing_tools[*]}"
        exit 1
    fi
}

# Mostrar configuración completa
show_config() {
    log_separator
    log_info "📋 CONFIGURACIÓN COMPLETA DEL ENTORNO"
    echo ""
    echo -e "${CYAN}📡 SERVICIOS:${NC}"
    echo "  EVOLUTION_URL: $EVOLUTION_URL"
    echo "  ENGINE_URL: $ENGINE_URL"
    echo ""
    echo -e "${CYAN}🔐 AUTENTICACIÓN:${NC}"
    echo "  TENANT_ID: $TENANT_ID"
    echo "  API_KEY: ${API_KEY:0:4}***"
    echo "  AUTHENTICATION_TYPE: apikey"
    echo ""
    echo -e "${CYAN}🗄️  BASE DE DATOS:${NC}"
    echo "  DATABASE_URL: ${DATABASE_URL:0:60}..."
    echo "  DATABASE_PROVIDER: postgresql"
    echo ""
    echo -e "${CYAN}🔗 WEBHOOK:${NC}"
    echo "  WEBHOOK_URL: $WEBHOOK_URL"
    echo "  WEBHOOK_EVENTS: $WEBHOOK_EVENTS"
    echo "  WEBHOOK_GLOBAL_ENABLED: true"
    echo ""
    echo -e "${CYAN}⚙️  CONFIGURACIÓN EVOLUTION:${NC}"
    echo "  STORE_MESSAGES: $STORE_MESSAGES"
    echo "  STORE_CONTACTS: $STORE_CONTACTS"
    echo "  STORE_CHATS: $STORE_CHATS"
    echo "  GROUPS_IGNORE: $GROUPS_IGNORE"
    echo "  SKIP_HISTORY_SYNC: $SKIP_HISTORY_SYNC"
    echo "  CONFIG_SESSION_SAVE: $CONFIG_SESSION_SAVE"
    echo ""
    echo -e "${CYAN}📊 LOGS:${NC}"
    echo "  LOG_LEVEL: DEBUG"
    echo "  SERVER_PORT: 8080"
}

# Extraer contraseña de DATABASE_URL
extract_db_password() {
    if [[ -z "$DATABASE_URL" ]]; then
        return 1
    fi
    
    local no_scheme="${DATABASE_URL#postgresql://}"
    no_scheme="${no_scheme#postgres://}"
    local userpass="${no_scheme%%@*}"
    local pass="${userpass#*:}"
    
    if [[ "$pass" != "$userpass" ]]; then
        export PGPASSWORD="$pass"
        return 0
    fi
    return 1
}

# 1. Verificar autenticación de Evolution
check_evolution_auth() {
    log_separator
    log_info "🔐 1. Verificando autenticación en Evolution API..."
    
    local response
    response=$(curl -s -w "\n%{http_code}" \
        -H "apikey: ${API_KEY}" \
        "${EVOLUTION_URL}/instance/fetchInstances" 2>/dev/null || echo -e "\n000")
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [[ "$http_code" == "200" ]]; then
        log_success "Autenticación exitosa"
        if command -v jq >/dev/null 2>&1; then
            echo "$body" | jq '.' 2>/dev/null || echo "$body"
        else
            echo "$body"
        fi
        
        # Verificar si la instancia existe
        if echo "$body" | grep -q "\"$TENANT_ID\""; then
            log_success "Instancia '$TENANT_ID' encontrada"
        else
            log_warn "⚠️  Instancia '$TENANT_ID' no encontrada - se creará automáticamente"
        fi
    else
        log_fail "Error de autenticación (HTTP $http_code)"
        echo "$body"
        log_info "Posibles soluciones:"
        echo "  1. Verifica que API_KEY='$API_KEY' sea correcta"
        echo "  2. Revisa la URL de Evolution API"
        echo "  3. Verifica que el servicio esté corriendo"
        return 1
    fi
}

# 2. Crear/Verificar instancia y obtener QR
check_evolution_instance() {
    log_separator
    log_info "📱 2. Configurando instancia en Evolution API..."
    
    # Intentar crear la instancia con la configuración real
    local create_response
    create_response=$(curl -s -X POST \
        -H "apikey: ${API_KEY}" \
        -H "Content-Type: application/json" \
        -d '{
            "instanceName": "'"${TENANT_ID}"'",
            "number": "",
            "webhook": {
                "url": "'"${WEBHOOK_URL}"'",
                "events": ["messages.upsert", "messages.update", "connection.update"]
            },
            "webhook_by_events": true,
            "events": ["messages.upsert", "messages.update", "connection.update"],
            "reconnect_every": 60,
            "store_messages": '"${STORE_MESSAGES}"',
            "store_contacts": '"${STORE_CONTACTS}"',
            "store_chats": '"${STORE_CHATS}"',
            "groups_ignore": '"${GROUPS_IGNORE}"',
            "skip_history_sync": '"${SKIP_HISTORY_SYNC}"',
            "config_session_save": '"${CONFIG_SESSION_SAVE}"'
        }' \
        "${EVOLUTION_URL}/instance/create" 2>/dev/null)
    
    if command -v jq >/dev/null 2>&1; then
        echo "$create_response" | jq '.' 2>/dev/null || echo "$create_response"
    else
        echo "$create_response"
    fi
    
    # Obtener estado de conexión
    log_info "📊 Estado de conexión de la instancia:"
    local state_response
    state_response=$(curl -s \
        -H "apikey: ${API_KEY}" \
        "${EVOLUTION_URL}/instance/connectionState/${TENANT_ID}" 2>/dev/null)
    
    if command -v jq >/dev/null 2>&1; then
        echo "$state_response" | jq '.' 2>/dev/null || echo "$state_response"
    else
        echo "$state_response"
    fi
    
    # Intentar obtener QR
    log_info "📸 Generando QR para escaneo:"
    local qr_response
    qr_response=$(curl -s \
        -H "apikey: ${API_KEY}" \
        "${EVOLUTION_URL}/instance/qr/${TENANT_ID}" 2>/dev/null)
    
    if command -v jq >/dev/null 2>&1; then
        echo "$qr_response" | jq '.' 2>/dev/null || echo "$qr_response"
    else
        echo "$qr_response"
    fi
    
    # Verificar si hay un QR válido
    if echo "$qr_response" | grep -q "base64"; then
        log_success "QR generado exitosamente - escanea con WhatsApp"
    elif echo "$qr_response" | grep -q "pairing"; then
        log_success "Usando método pairing - verifica el código"
    else
        log_warn "⚠️  No se pudo generar QR - verifica que la instancia esté activa"
    fi
    
    # Mostrar comando para ver logs
    log_info "📝 Para ver logs de la instancia:"
    echo "  curl -H 'apikey: ${API_KEY}' ${EVOLUTION_URL}/instance/logs/${TENANT_ID}"
}

# 3. Probar webhook del engine
test_engine_webhook() {
    log_separator
    log_info "🔗 3. Probando webhook del engine..."
    
    local payload='{
        "event": "messages.upsert",
        "instance": "'"${TENANT_ID}"'",
        "data": {
            "key": {
                "fromMe": false,
                "remoteJid": "51999999999@s.whatsapp.net"
            },
            "message": {
                "conversation": "Test diagnóstico webhook - $(date)"
            },
            "timestamp": '$(date +%s)'
        }
    }'
    
    log_info "📤 Enviando payload a ${WEBHOOK_URL}"
    
    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d "$payload" \
        "${WEBHOOK_URL}" 2>/dev/null || echo -e "\n000")
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    log_info "📡 Webhook respondió con HTTP $http_code"
    
    if [[ "$http_code" == "200" ]] || [[ "$http_code" == "201" ]] || [[ "$http_code" == "204" ]]; then
        log_success "Webhook funcionando correctamente"
        if [[ -n "$body" ]]; then
            echo "$body"
        fi
    else
        log_fail "Webhook falló (HTTP $http_code)"
        echo "$body"
        log_info "Posibles problemas:"
        echo "  1. El endpoint no existe en el engine"
        echo "  2. Problemas de CORS o autenticación"
        echo "  3. El payload no es válido para el engine"
        return 1
    fi
}

# 4. Verificar endpoints del engine
check_engine_endpoints() {
    log_separator
    log_info "🌐 4. Verificando endpoints del engine..."
    
    # Health check
    log_info "🏥 Health check:"
    local health_response
    health_response=$(curl -s -w "\n%{http_code}" \
        "${ENGINE_URL}/actuator/health" 2>/dev/null || echo -e "\n000")
    
    local health_code=$(echo "$health_response" | tail -n1)
    local health_body=$(echo "$health_response" | sed '$d')
    
    if [[ "$health_code" == "200" ]]; then
        log_success "Engine health check OK"
        echo "$health_body"
    else
        log_warn "⚠️  Health check devolvió HTTP $health_code"
    fi
    
    # Endpoint QR (requiere autenticación)
    log_info "📱 Endpoint QR (requiere autenticación):"
    local qr_endpoint_response
    qr_endpoint_response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer ${API_KEY}" \
        "${ENGINE_URL}${QR_PATH}" 2>/dev/null || echo -e "\n000")
    
    local qr_code=$(echo "$qr_endpoint_response" | tail -n1)
    log_info "QR endpoint respondió con HTTP $qr_code"
    
    # Endpoint de estado
    log_info "📊 Endpoint de estado:"
    local status_response
    status_response=$(curl -s -w "\n%{http_code}" \
        "${ENGINE_URL}${STATUS_PATH}" 2>/dev/null || echo -e "\n000")
    
    local status_code=$(echo "$status_response" | tail -n1)
    if [[ "$status_code" == "200" ]] || [[ "$status_code" == "404" ]]; then
        log_success "Status endpoint accesible"
    else
        log_warn "⚠️  Status endpoint devolvió HTTP $status_code"
    fi
}

# 5. Verificar base de datos
check_database() {
    log_separator
    log_info "🗄️  5. Verificando base de datos..."
    
    if [[ -z "$DATABASE_URL" ]]; then
        log_warn "⚠️  DATABASE_URL no configurada - omitiendo verificación de BD"
        return 1
    fi
    
    # Extraer password
    extract_db_password
    
    local psql_cmd=""
    local use_docker=false
    
    if command -v psql >/dev/null 2>&1; then
        psql_cmd="psql"
    elif command -v docker >/dev/null 2>&1; then
        psql_cmd="docker run --rm -e PGPASSWORD=$PGPASSWORD postgres:16 psql"
        use_docker=true
    else
        log_warn "⚠️  No se encontró psql o docker - no se puede verificar BD"
        return 1
    fi
    
    # Función helper para ejecutar SQL
    run_sql() {
        local sql="$1"
        if [[ "$use_docker" == "true" ]]; then
            $psql_cmd "$DATABASE_URL" -c "$sql" 2>/dev/null || echo "Error ejecutando SQL"
        else
            $psql_cmd "$DATABASE_URL" -c "$sql" 2>/dev/null || echo "Error ejecutando SQL"
        fi
    }
    
    # Probar conexión
    log_info "🔗 Conectando a la base de datos..."
    local test_result
    test_result=$(run_sql "SELECT NOW() as connected_at;")
    if echo "$test_result" | grep -q "connected_at"; then
        log_success "Conexión a BD exitosa"
        echo "$test_result"
    else
        log_fail "No se pudo conectar a la base de datos"
        log_info "Verifica la URL y que la BD esté accesible"
        return 1
    fi
    
    # Verificar tablas de WhatsApp
    log_info "📋 Buscando tablas de WhatsApp..."
    local tables_result
    tables_result=$(run_sql "
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
          AND table_name LIKE '%whatsapp%'
        ORDER BY table_name;
    ")
    echo "$tables_result"
    
    # Contar contactos si existe la tabla
    if echo "$tables_result" | grep -q "whatsapp_contacts"; then
        log_info "👤 Contactos en la BD:"
        run_sql "SELECT COUNT(*) as total_contacts FROM whatsapp_contacts;"
        
        log_info "📇 Últimos contactos:"
        run_sql "
            SELECT id, phone_number, name, preferred_language, tenant_id, created_at
            FROM whatsapp_contacts 
            ORDER BY created_at DESC 
            LIMIT 10;
        "
    fi
    
    # Contar mensajes si existe la tabla
    if echo "$tables_result" | grep -q "whatsapp_messages"; then
        log_info "💬 Mensajes en la BD:"
        run_sql "SELECT COUNT(*) as total_messages FROM whatsapp_messages;"
        
        log_info "📨 Últimos mensajes:"
        run_sql "
            SELECT id, sender, recipient, tenant_id, outgoing, timestamp
            FROM whatsapp_messages 
            ORDER BY timestamp DESC 
            LIMIT 10;
        "
    fi
    
    # Verificar actividad actual
    log_info "⚡ Actividad actual de la BD:"
    run_sql "
        SELECT state, COUNT(*) as connections
        FROM pg_stat_activity
        GROUP BY state
        ORDER BY connections DESC;
    "
}

# 6. Verificar configuración de la instancia
check_instance_config() {
    log_separator
    log_info "⚙️  6. Verificando configuración de instancia..."
    
    log_info "🔗 Webhook URL configurado: ${WEBHOOK_URL}"
    log_info "📨 Webhook Events: ${WEBHOOK_EVENTS}"
    log_info "💾 Store Messages: ${STORE_MESSAGES}"
    log_info "👤 Store Contacts: ${STORE_CONTACTS}"
    log_info "💬 Store Chats: ${STORE_CHATS}"
    log_info "🚫 Groups Ignore: ${GROUPS_IGNORE}"
    
    # Verificar que el webhook sea accesible desde Evolution
    local webhook_check
    webhook_check=$(curl -s -o /dev/null -w "%{http_code}" \
        -X OPTIONS \
        "${WEBHOOK_URL}" 2>/dev/null || echo "000")
    
    if [[ "$webhook_check" == "200" ]] || [[ "$webhook_check" == "204" ]] || [[ "$webhook_check" == "405" ]]; then
        log_success "Webhook endpoint accesible"
    else
        log_warn "⚠️  Webhook endpoint respondió con HTTP $webhook_check"
    fi
}

# 7. Verificar estado de WhatsApp
check_whatsapp_status() {
    log_separator
    log_info "📱 7. Verificando estado de WhatsApp..."
    
    local status_response
    status_response=$(curl -s \
        -H "apikey: ${API_KEY}" \
        "${EVOLUTION_URL}/instance/connectionState/${TENANT_ID}" 2>/dev/null)
    
    if command -v jq >/dev/null 2>&1; then
        echo "$status_response" | jq '.' 2>/dev/null || echo "$status_response"
    else
        echo "$status_response"
    fi
    
    # Extraer estado
    local state=$(echo "$status_response" | grep -o '"state":"[^"]*"' | cut -d'"' -f4)
    
    if [[ -n "$state" ]]; then
        case "$state" in
            "open")
                log_success "WhatsApp CONECTADO ✅"
                ;;
            "connecting")
                log_warn "WhatsApp CONECTANDO... ⏳"
                ;;
            "close")
                log_fail "WhatsApp DESCONECTADO ❌"
                log_info "Escanea el QR para conectar"
                ;;
            *)
                log_warn "Estado desconocido: $state"
                ;;
        esac
    fi
}

# Función principal
main() {
    echo -e "\n${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║         DIAGNÓSTICO COMPLETO DE WHATSAPP                 ║${NC}"
    echo -e "${CYAN}║              ENTORNO PRODUCCIÓN                          ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
    
    # Verificar herramientas
    check_tools
    
    # Mostrar configuración completa
    show_config
    
    # Ejecutar todas las pruebas
    check_evolution_auth || true
    check_evolution_instance || true
    test_engine_webhook || true
    check_engine_endpoints || true
    check_database || true
    check_instance_config || true
    check_whatsapp_status || true
    
    # Resumen final
    log_separator
    log_success "✅ DIAGNÓSTICO COMPLETADO"
    echo -e "\n${CYAN}📋 RESUMEN DE ACCIONES RECOMENDADAS:${NC}"
    echo "  1. 🔐 Si la autenticación falla, verifica API_KEY='$API_KEY'"
    echo "  2. 📱 Escanea el QR con WhatsApp para conectar"
    echo "  3. 🔗 Verifica que el webhook esté funcionando en ${WEBHOOK_URL}"
    echo "  4. 📊 Revisa los logs de Evolution para más detalles"
    echo "  5. 🗄️  Asegúrate que las tablas de BD existan"
    echo ""
    echo -e "${YELLOW}💡 COMANDOS ÚTILES:${NC}"
    echo "  # Ver logs de Evolution:"
    echo "  curl -H 'apikey: ${API_KEY}' ${EVOLUTION_URL}/instance/logs/${TENANT_ID}"
    echo ""
    echo "  # Ver estado de WhatsApp:"
    echo "  curl -H 'apikey: ${API_KEY}' ${EVOLUTION_URL}/instance/connectionState/${TENANT_ID}"
    echo ""
    echo "  # Forzar reconexión:"
    echo "  curl -X POST -H 'apikey: ${API_KEY}' ${EVOLUTION_URL}/instance/connect/${TENANT_ID}"
}

# Manejo de señales
trap 'echo -e "\n${YELLOW}⚠️  Diagnóstico interrumpido${NC}"; exit 1' INT TERM

# Ejecutar
main "$@"