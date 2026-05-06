#!/bin/bash

# Rutas (formato Git Bash)
BACKEND="/d/Config/TestAzure/Back-end/pillihuaman-com-pe-pdfengine/src"
FRONTEND="/d/Config/TestAzure/Front-end/src/app/@presentation/pages/tools/pdf-editor"

OUTPUT="java_project_report.txt"

# Validación de rutas
if [ ! -d "$BACKEND" ]; then
    echo "Backend directory not found: $BACKEND"
    exit 1
fi

if [ ! -d "$FRONTEND" ]; then
    echo "Frontend directory not found: $FRONTEND"
    exit 1
fi

echo "============================================" > "$OUTPUT"
echo "FULL PROJECT STRUCTURE REPORT" >> "$OUTPUT"
echo "Generated on: $(date)" >> "$OUTPUT"
echo "Backend Root: $BACKEND" >> "$OUTPUT"
echo "Frontend Root: $FRONTEND" >> "$OUTPUT"
echo "============================================" >> "$OUTPUT"
echo "" >> "$OUTPUT"

# ============================================
# 1️⃣ BACKEND STRUCTURE
# ============================================
echo "---- BACKEND DIRECTORY STRUCTURE ----" >> "$OUTPUT"
find "$BACKEND" -type f >> "$OUTPUT"
echo "" >> "$OUTPUT"

echo "---- BACKEND JAVA FILE CONTENT ----" >> "$OUTPUT"

find "$BACKEND" -type f -name "*.java" | while read file; do
    echo "" >> "$OUTPUT"
    echo "============================================" >> "$OUTPUT"
    echo "FILE: $file" >> "$OUTPUT"
    echo "============================================" >> "$OUTPUT"
    cat "$file" >> "$OUTPUT"
    echo "" >> "$OUTPUT"
done

echo "" >> "$OUTPUT"
echo "---- BACKEND CONFIG FILE CONTENT (.properties/.xml) ----" >> "$OUTPUT"

find "$BACKEND" -type f \( -name "*.properties" -o -name "*.xml" \) | while read file; do
    echo "" >> "$OUTPUT"
    echo "============================================" >> "$OUTPUT"
    echo "FILE: $file" >> "$OUTPUT"
    echo "============================================" >> "$OUTPUT"
    cat "$file" >> "$OUTPUT"
    echo "" >> "$OUTPUT"
done

# ============================================
# 2️⃣ FRONTEND STRUCTURE
# ============================================
echo "" >> "$OUTPUT"
echo "---- FRONTEND DIRECTORY STRUCTURE ----" >> "$OUTPUT"
find "$FRONTEND" -type f >> "$OUTPUT"
echo "" >> "$OUTPUT"

echo "---- FRONTEND FILE CONTENT (.ts/.html/.css/.scss) ----" >> "$OUTPUT"

find "$FRONTEND" -type f \( -name "*.ts" -o -name "*.html" -o -name "*.css" -o -name "*.scss" \) | while read file; do
    echo "" >> "$OUTPUT"
    echo "============================================" >> "$OUTPUT"
    echo "FILE: $file" >> "$OUTPUT"
    echo "============================================" >> "$OUTPUT"
    cat "$file" >> "$OUTPUT"
    echo "" >> "$OUTPUT"
done

echo "✅ Report generated: $OUTPUT"