#!/bin/bash

PROJECT_ROOT="/d/Config/TestAzure/Back-end/pillihuaman-com-pe-engine"
OUTPUT="java_project_report.txt"

if [ ! -d "$PROJECT_ROOT" ]; then
    echo "Project directory not found: $PROJECT_ROOT"
    exit 1
fi

echo "Generating report..."

cat > "$OUTPUT" << EOF
====================================================
SPRING BOOT PROJECT REPORT
Generated: $(date)
Project: $PROJECT_ROOT
====================================================

EOF

#####################################################
# ESTRUCTURA DEL PROYECTO
#####################################################

echo "================ PROJECT STRUCTURE ================" >> "$OUTPUT"

find "$PROJECT_ROOT" \
    -path "*/target/*" -prune -o \
    -path "*/logs/*" -prune -o \
    -path "*/.git/*" -prune -o \
    -type f \
    -print >> "$OUTPUT"

echo "" >> "$OUTPUT"

#####################################################
# ARCHIVOS DE CONFIGURACION
#####################################################

echo "================ CONFIGURATION FILES ================" >> "$OUTPUT"

find "$PROJECT_ROOT" \
    -path "*/target/*" -prune -o \
    -path "*/logs/*" -prune -o \
    -path "*/.git/*" -prune -o \
    -type f \
    \( \
        -name "pom.xml" \
        -o -name "*.properties" \
        -o -name "*.yml" \
        -o -name "*.yaml" \
        -o -name "*.xml" \
    \) \
    -print | while read file
do
    echo "" >> "$OUTPUT"
    echo "====================================================" >> "$OUTPUT"
    echo "FILE: $file" >> "$OUTPUT"
    echo "====================================================" >> "$OUTPUT"
    cat "$file" >> "$OUTPUT"
    echo "" >> "$OUTPUT"
done

#####################################################
# CODIGO JAVA PRODUCTIVO
#####################################################

echo "" >> "$OUTPUT"
echo "================ JAVA SOURCE =======================" >> "$OUTPUT"

find "$PROJECT_ROOT/src/main/java" \
    -type f \
    -name "*.java" | sort | while read file
do
    echo "" >> "$OUTPUT"
    echo "====================================================" >> "$OUTPUT"
    echo "FILE: $file" >> "$OUTPUT"
    echo "====================================================" >> "$OUTPUT"
    cat "$file" >> "$OUTPUT"
    echo "" >> "$OUTPUT"
done

#####################################################
# RECURSOS PRODUCTIVOS
#####################################################

echo "" >> "$OUTPUT"
echo "================ MAIN RESOURCES ====================" >> "$OUTPUT"

find "$PROJECT_ROOT/src/main/resources" \
    -type f \
    \( \
        -name "*.properties" \
        -o -name "*.yml" \
        -o -name "*.yaml" \
        -o -name "*.xml" \
        -o -name "*.sql" \
    \) | while read file
do
    echo "" >> "$OUTPUT"
    echo "====================================================" >> "$OUTPUT"
    echo "FILE: $file" >> "$OUTPUT"
    echo "====================================================" >> "$OUTPUT"
    cat "$file" >> "$OUTPUT"
    echo "" >> "$OUTPUT"
done

#####################################################
# RESUMEN
#####################################################

echo "" >> "$OUTPUT"
echo "================ SUMMARY ===========================" >> "$OUTPUT"

echo "Java files:" >> "$OUTPUT"
find "$PROJECT_ROOT/src/main/java" -name "*.java" | wc -l >> "$OUTPUT"

echo "" >> "$OUTPUT"

echo "Configuration files:" >> "$OUTPUT"
find "$PROJECT_ROOT" \
    \( \
        -name "*.properties" \
        -o -name "*.yaml" \
        -o -name "*.yml" \
        -o -name "*.xml" \
        -o -name "pom.xml" \
    \) | wc -l >> "$OUTPUT"

echo ""
echo "Report generated: $OUTPUT"