// MODE 2: SAFE REFACTOR
package pillihuaman.com.pe.engine.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pillihuaman.com.pe.engine.application.dto.UserMeasurementMetadata;
import pillihuaman.com.pe.engine.application.port.inbound.MeasurementEngineUseCase;
import pillihuaman.com.pe.engine.application.port.outbound.AiMeasurementPort;
import pillihuaman.com.pe.engine.application.port.outbound.ProductExternalPort;
import pillihuaman.com.pe.engine.application.port.outbound.PromptExternalPort;
import pillihuaman.com.pe.engine.application.port.outbound.persistence.CustomerBiometricProfilePort;
import pillihuaman.com.pe.engine.application.port.outbound.persistence.GarmentPersistencePort;
import pillihuaman.com.pe.engine.application.port.outbound.persistence.MeasurementPersistencePort;
import pillihuaman.com.pe.engine.domain.exception.BusinessLogicException;
import pillihuaman.com.pe.engine.domain.exception.ResourceNotFoundException;
import pillihuaman.com.pe.engine.domain.model.external.ReqPrompt;
import pillihuaman.com.pe.engine.domain.model.measurement.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasurementEngineService implements MeasurementEngineUseCase {

    private final AiMeasurementPort aiMeasurementPort;
    private final MeasurementPersistencePort measurementPersistencePort;
    private final GarmentPersistencePort garmentPersistencePort;
    private final PromptExternalPort promptExternalPort;
    private final ProductExternalPort productExternalPort;
    @Value("${application.body.size.ai-prompt-code:AI_BODY_SIZE_RECOMMENDATION_V1}")
    private String aiPromptCode;
    private final CustomerBiometricProfilePort customerBiometricProfilePort;


    @Override
    public Mono<MeasurementHistory> processEphemeralMeasurement(
            final String tenantId,
            final String userId,
            final String garmentId,
            final UserMeasurementMetadata metadata,
            final String bearerToken) {

        final ReqPrompt reqPrompt = ReqPrompt.builder().code(aiPromptCode).build();


        return productExternalPort.getProductById(garmentId, bearerToken)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(TimeoutException.class, e -> {
                    log.warn("[Measurement-Engine] TIMEOUT conectando a Support MS. Usando prenda fallback.");
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, e -> {
                    log.warn("[Measurement-Engine] Error HTTP/Red ({}). Usando prenda fallback.", e.getMessage());
                    return Mono.empty();
                })
                // <<< CHANGE
                .defaultIfEmpty(new Garment(garmentId, "Desconocida", java.util.Collections.emptyList()))
                .doOnNext(g -> {
                    if ("Desconocida".equals(g.category())) {
                        log.warn("[Measurement-Engine] Prenda {} no encontrada o falló API. Continuando flujo Edge AI...", garmentId);
                    }
                    log.info("[Measurement-Engine] Garment resolved. Proceeding to fetch AI Prompt from Support MS...");
                })
                .flatMap(garment ->
                        // 2. Ejecutar Matemática Local usando Schedulers.boundedElastic() nativo
                        Mono.fromCallable(() -> calculateDeterministically(metadata))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(localMath ->
                                        // 3. Obtener el Prompt de la Base de Datos (Support MS)
                                        promptExternalPort.getPromptByCharacteristics(reqPrompt, bearerToken)
                                                .doOnNext(p -> log.info("[Measurement-Engine] Prompt successfully retrieved from Support MS! Building context..."))
                                                .flatMap(promptDto -> {
                                                    final String basePrompt = promptDto.getFinalVersion() != null && promptDto.getFinalVersion().getTemplate() != null
                                                            ? promptDto.getFinalVersion().getTemplate()
                                                            : "Analiza las medidas antropométricas.";

                                                    final String enhancedPrompt = buildHybridPrompt(basePrompt, localMath, garment, metadata);

                                                    // 5. Delegar Validación 100% a NeuroIA
                                                    return aiMeasurementPort.extractMeasurements(enhancedPrompt, metadata, bearerToken);
                                                })
                                                .flatMap(aiResult -> {
                                                    if ("REJECTED".equalsIgnoreCase(aiResult.status())) {
                                                        log.warn("[Measurement-Engine] NeuroIA Analysis rejected: {}", aiResult.clothingValidation());
                                                        return Mono.error(new BusinessLogicException("Análisis IA rechazado: " + aiResult.clothingValidation()));
                                                    }

                                                    // 6. Decisión final y Auditoría
                                                    double bodyChestCm = aiResult.chest();
                                                    double allowance = calculateAllowance(metadata.getFitPreference());
                                                    double targetChest = bodyChestCm + allowance;

                                                    String recommendedSize = "N/A";
                                                    if (garment.measurements() != null && !garment.measurements().isEmpty()) {
                                                        recommendedSize = garment.measurements().stream()
                                                                .filter(m -> m.chestContour() != null && m.chestContour() >= targetChest)
                                                                .min(Comparator.comparingDouble(ProductMeasurementModel::chestContour))
                                                                .map(ProductMeasurementModel::size)
                                                                .orElseGet(() -> garment.measurements().stream()
                                                                        .max(Comparator.comparingDouble(ProductMeasurementModel::chestContour))
                                                                        .map(ProductMeasurementModel::size)
                                                                        .orElse("N/A"));
                                                    }

                                                    String selectedSize = metadata.getCustomFormFields() != null ? metadata.getCustomFormFields().getSelectedSize() : null;
                                                    boolean matchSuccess = recommendedSize.equalsIgnoreCase(selectedSize) || selectedSize == null;

                                                    CustomerBiometricProfile profile = new CustomerBiometricProfile(
                                                            null, userId, metadata.getHeightCm(), metadata.getWeightKg(),
                                                            metadata.getFitPreference(), bodyChestCm, aiResult.shoulderWidthCm(),
                                                            Instant.now(), tenantId
                                                    );

                                                    final MeasurementHistory history = new MeasurementHistory(
                                                            UUID.randomUUID().toString(),
                                                            userId, garmentId, aiResult.chest(), aiResult.waist(), aiResult.hips(),
                                                            recommendedSize, matchSuccess,
                                                            aiResult.clothingValidation(),
                                                            matchSuccess ? null : "La IA recomienda Talla " + recommendedSize + " basada en tu preferencia.",
                                                            Instant.now());

                                                    return customerBiometricProfilePort.saveOrUpdate(profile)
                                                            .then(measurementPersistencePort.save(history))
                                                            .timeout(Duration.ofSeconds(3))
                                                            .onErrorResume(e -> {
                                                                log.warn("Error guardando el historial (Posible DB caída). Retornando resultado generado en memoria.");
                                                                return Mono.just(history);
                                                            });
                                                })
                                ))
                .doFinally(signal -> log.info("[Measurement-Engine] Hybrid Neuro-Symbolic cycle completed with Signal: {}", signal))
                .onErrorResume(e -> {
                    log.error("[Measurement-Engine] Unexpected flow error: {}", e.getMessage(), e);
                    if (e instanceof BusinessLogicException || e instanceof ResourceNotFoundException)
                        return Mono.error(e);
                    return Mono.error(new BusinessLogicException("Error procesando la validación IA: " + e.getMessage()));
                });
    }

    private String buildHybridPrompt(
            String basePrompt,
            BodyMeasurementResult localMath,
            Garment garment,
            UserMeasurementMetadata metadata) {

        StringBuilder sb = new StringBuilder(basePrompt);

        sb.append("\n\n=================================================================\n");
        sb.append(">>> INPUT DE DATOS ESTRUCTURADOS (VERDAD BASE MATEMÁTICA) <<<\n");
        sb.append("=================================================================\n\n");

        sb.append("### 1. METADATA DEL USUARIO (EDGE SENSOR - FRONTEND)\n");
        sb.append("- Estatura: ").append(metadata.getHeightCm() != null ? metadata.getHeightCm() : "Desconocida").append(" cm\n");
        sb.append("- Peso: ").append(metadata.getWeightKg() != null ? metadata.getWeightKg() : "Desconocido").append(" kg\n");
        sb.append("- Sexo / Género: ").append(metadata.getGender() != null ? metadata.getGender() : "UNISEX").append("\n");
        sb.append("- Preferencia de Ajuste (Fit Preference): ").append(metadata.getFitPreference() != null ? metadata.getFitPreference() : "REGULAR").append("\n");

        if (metadata.getCustomFormFields() != null && metadata.getCustomFormFields().getSelectedSize() != null) {
            sb.append("- Talla pre-seleccionada por usuario: ")
                    .append(metadata.getCustomFormFields().getSelectedSize()).append("\n");
        }

        sb.append("\n### 2. PERFIL ANTROPOMÉTRICO (MOTOR MATEMÁTICO BACKEND ISO-8559-1)\n");
        sb.append("- Contorno de Pecho / Busto: ").append(String.format("%.1f", localMath.chest())).append(" cm\n");
        sb.append("- Contorno de Cintura: ").append(String.format("%.1f", localMath.waist())).append(" cm\n");
        sb.append("- Contorno de Cadera: ").append(String.format("%.1f", localMath.hips())).append(" cm\n");
        sb.append("- Ancho de Hombros (Bi-deltoideo): ").append(String.format("%.1f", localMath.shoulderWidthCm())).append(" cm\n");
        sb.append("- Nivel de Confianza de Medición: Absoluto (Determinístico basado en coordenadas óseas 3D)\n");

        sb.append("\n### 3. DATOS DE LA PRENDA Y TABLA DE TALLAS\n");
        sb.append("- Categoría de Prenda: ").append(garment.category() != null ? garment.category() : "General").append("\n");

        if (garment.measurements() != null && !garment.measurements().isEmpty()) {
            garment.measurements().forEach(m -> {
                sb.append("- Talla ").append(m.size()).append(" -> ");
                if (m.chestContour() != null) sb.append("Pecho: ").append(m.chestContour()).append(" cm, ");
                if (m.shoulderWidth() != null) sb.append("Hombros: ").append(m.shoulderWidth()).append(" cm, ");
                if (m.totalLength() != null) sb.append("Largo: ").append(m.totalLength()).append(" cm, ");
                if (m.sleeveLength() != null) sb.append("Manga: ").append(m.sleeveLength()).append(" cm");
                sb.append("\n");
            });
        } else {
            sb.append("- ADVERTENCIA: No hay medidas de prenda disponibles en la base de datos. Utiliza estándares universales para la categoría.\n");
        }

        sb.append("\n### INSTRUCCIÓN DE EJECUCIÓN FINAL\n");
        sb.append("Aplica tus reglas de Arquitectura de Tallas. Evalúa las medidas del cuerpo contra las de la prenda, considera el EASE (holgura), la elasticidad esperada y la preferencia del usuario.\n");
        sb.append("OBLIGATORIO: Retorna ÚNICAMENTE la estructura JSON estricta (sin markdown, sin explicaciones fuera del JSON). Analiza la viabilidad en el campo 'clothingValidation'. Usa los valores matemáticos provistos sin inventar otros nuevos.\n");

        return sb.toString();
    }

    private BodyMeasurementResult calculateDeterministically(UserMeasurementMetadata metadata) {
        UserMeasurementMetadata.CustomFormFieldsDto fields = metadata.getCustomFormFields();
        if (fields == null || fields.getFrontMath() == null) {
            throw new BusinessLogicException("Faltan datos biométricos Edge AI en el payload");
        }

        UserMeasurementMetadata.MathMetricsDto front = fields.getFrontMath();
        UserMeasurementMetadata.MathMetricsDto side = fields.getSideMath();

        double frontScale = metadata.getHeightCm() / front.getBodyHeightPixels();
        double sideScale = side != null && side.getBodyHeightPixels() != null
                ? metadata.getHeightCm() / side.getBodyHeightPixels() : frontScale;

        double bmi = metadata.getWeightKg() / Math.pow(metadata.getHeightCm() / 100.0, 2);
        String gender = metadata.getGender() != null ? metadata.getGender().toUpperCase() : "UNISEX";

        double rawWaistWidthCm = safeDouble(front.getWaistWidthPixels()) * frontScale;
        double rawShoulderWidthCm = safeDouble(front.getShouldersWidthPixels()) * frontScale;

        boolean isWaistCoherent = true;
        if (rawWaistWidthCm >= rawShoulderWidthCm && bmi < 35.0) {
            isWaistCoherent = false;
            log.warn("Anomalía detectada (Ropa Holgada). Descartando máscara de segmentación frontal.");
        }

        double finalWaistWidthCm;
        if (isWaistCoherent && Boolean.TRUE.equals(front.getSegmentationUsed())) {
            finalWaistWidthCm = rawWaistWidthCm;
        } else {
            UserMeasurementMetadata.BodyLandmarkDto leftHip = extractLandmark(front.getLandmarks(), "point_23");
            UserMeasurementMetadata.BodyLandmarkDto rightHip = extractLandmark(front.getLandmarks(), "point_24");

            double boneDistanceRelative = Math.abs(safeDouble(leftHip.getX()) - safeDouble(rightHip.getX()));
            double boneWidthPixels = boneDistanceRelative * safeDouble(front.getImageWidth());
            double boneWidthCm = boneWidthPixels * frontScale;

            finalWaistWidthCm = boneWidthCm * calculateTissueMultiplier(bmi, gender);
        }

        double sideWaistDepthCm = side != null && side.getWaistDepthPixels() != null
                ? side.getWaistDepthPixels() * sideScale
                : finalWaistWidthCm * getDepthToWidthRatio("WAIST", gender, bmi);
        double waistPerimeter = calculateRamanujanPerimeter(finalWaistWidthCm, sideWaistDepthCm);

        double finalChestWidthCm = safeDouble(front.getChestWidthPixels()) * frontScale;
        double sideChestDepthCm = side != null && side.getChestDepthPixels() != null
                ? side.getChestDepthPixels() * sideScale
                : finalChestWidthCm * getDepthToWidthRatio("CHEST", gender, bmi);
        double chestPerimeter = calculateRamanujanPerimeter(finalChestWidthCm, sideChestDepthCm);

        double finalHipsWidthCm = safeDouble(front.getHipsWidthPixels()) * frontScale;
        double sideHipsDepthCm = side != null && side.getHipsDepthPixels() != null
                ? side.getHipsDepthPixels() * sideScale
                : finalHipsWidthCm * getDepthToWidthRatio("HIPS", gender, bmi);
        double hipsPerimeter = calculateRamanujanPerimeter(finalHipsWidthCm, sideHipsDepthCm);

        return new BodyMeasurementResult(
                chestPerimeter, waistPerimeter, hipsPerimeter,
                "Validación Edge AI exitosa (Modelo ISO-8559 Aplicado)", "ACCEPTED", rawShoulderWidthCm, 0.0);
    }

    private double getDepthToWidthRatio(String part, String gender, double bmi) {
        boolean isFemale = "FEMALE".equals(gender);
        boolean isObese = bmi >= 30.0;

        return switch (part) {
            case "CHEST" -> {
                if (isFemale) yield isObese ? 0.78 : 0.74;
                yield isObese ? 0.72 : 0.68;
            }
            case "WAIST" -> {
                if (isObese) yield 0.88;
                yield isFemale ? 0.73 : 0.76;
            }
            case "HIPS" -> {
                if (isFemale) yield isObese ? 0.85 : 0.78;
                yield isObese ? 0.82 : 0.80;
            }
            default -> 0.75;
        };
    }

    private double calculateTissueMultiplier(double bmi, String gender) {
        double baseMultiplier;
        if (bmi < 18.5) {
            baseMultiplier = 1.15;
        } else if (bmi >= 40.0) {
            baseMultiplier = 1.65;
        } else {
            baseMultiplier = 1.15 + ((bmi - 18.5) * (0.50 / 21.5));
        }

        if ("FEMALE".equalsIgnoreCase(gender)) {
            baseMultiplier += 0.05;
        }
        return baseMultiplier;
    }

    private double calculateRamanujanPerimeter(double widthCm, double depthCm) {
        double a = widthCm / 2.0;
        double b = depthCm / 2.0;
        double h = Math.pow((a - b), 2) / Math.pow((a + b), 2);
        return Math.PI * (a + b) * (1 + (3 * h) / (10 + Math.sqrt(4 - 3 * h)));
    }

    private UserMeasurementMetadata.BodyLandmarkDto extractLandmark(java.util.List<UserMeasurementMetadata.BodyLandmarkDto> landmarks, String part) {
        if (landmarks == null) return new UserMeasurementMetadata.BodyLandmarkDto(part, 0.0, 0.0, 0.0, 0.0);
        return landmarks.stream()
                .filter(l -> part.equalsIgnoreCase(l.getPart()))
                .findFirst()
                .orElse(new UserMeasurementMetadata.BodyLandmarkDto(part, 0.0, 0.0, 0.0, 0.0));
    }

    private double safeDouble(Double val) {
        return val == null ? 0.0 : val;
    }

    private double calculateAllowance(String fitPreference) {
        if (fitPreference == null) return 4.0;
        return switch (fitPreference.toUpperCase()) {
            case "SLIM", "AJUSTADA" -> 2.0;
            case "LOOSE", "HOLGADA", "OVERSIZED" -> 8.0;
            default -> 4.0;
        };
    }

    private String getFitLabel(String fitPreference) {
        if (fitPreference == null) return "Regular";
        return switch (fitPreference.toUpperCase()) {
            case "SLIM", "AJUSTADA" -> "Ajustado";
            case "LOOSE", "HOLGADA", "OVERSIZED" -> "Holgado";
            default -> "Regular";
        };
    }
}