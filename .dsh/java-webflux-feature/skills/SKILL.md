---
name: java-webflux-hexagonal-feature
description: Flujo de trabajo estandarizado para crear, modificar o auditar endpoints, casos de uso, adaptadores y modelos en el backend Java WebFlux.
whenToUse: Cuando el usuario solicite crear un nuevo endpoint REST, integrar una API externa (WebClient), refactorizar lógica de negocio o trabajar en persistencia reactiva.
---

# Flujo de Trabajo para Desarrollo Backend (Java WebFlux + Arquitectura Hexagonal)

## 1. Análisis de Impacto (Fase de Arquitectura)
Antes de escribir código, analiza:
- **Flujo Reactivo:** ¿La operación es I/O bound? Toda la cadena debe retornar `Mono` o `Flux`.
- **Arquitectura Hexagonal:** ¿Qué capa se ve afectada? Respeta la regla de dependencia: Infraestructura depende de Aplicación, Aplicación depende de Dominio. El Dominio no depende de NADA.
- **Manejo de Errores:** Define dónde y cómo capturar la excepción. Todas las respuestas HTTP de error deben mapearse al formato estándar `RespBase<T>` usando el `ReactiveGlobalExceptionHandler`.

## 2. Implementación de Código (Reglas Estrictas)
- **Dominio (`@domain`):** 
  - Usa `record` para todos los modelos inmutables de datos.
  - Las excepciones de negocio extienden de `BusinessLogicException`.
- **Aplicación (`@application`):**
  - **Puertos de Entrada:** Interfaces terminadas en `*UseCase`.
  - **Puertos de Salida:** Interfaces terminadas en `*Port` (ej: `*PersistencePort`, `*ExternalPort`).
  - **Servicios:** Implementan los UseCases. Usa `@Service`, `@RequiredArgsConstructor`, e inyecta los `Port` como variables `final`.
- **Infraestructura (`@infrastructure`):**
  - **Controladores Rest:** Usa `@RestController`, inyecta el `UseCase`. Las respuestas siempre deben ir envueltas en `RespBase<T>`. Extrae el `TenantId` usando `TenantWebFilter.TENANT_KEY`.
  - **Adaptadores Salida:** Implementan los `Port`. Utiliza `WebClient` para APIs externas y `ReactiveMongoRepository` para persistencia.
- **Concurrencia y Hilos:** NUNCA uses `.block()`. Si debes usar una librería bloqueante legacy (ej. PDFBox), envuélvelo en `Mono.fromCallable(...)` y usa `.subscribeOn(Schedulers.boundedElastic())`.

## 3. Verificación de Calidad (Checklist del Arquitecto)
Antes de entregar el código, verifica mentalmente:
- [ ] ¿Hay llamadas bloqueantes encubiertas en la cadena reactiva?
- [ ] ¿Los DTOs y Entities son `records` (donde aplique)?
- [ ] ¿Se están gestionando los valores nulos con `.defaultIfEmpty()` o `.switchIfEmpty()` en lugar de simples condicionales `if (x == null)`?
- [ ] ¿El adaptador REST consume y produce los MediaTypes correctos (ej: `APPLICATION_JSON_VALUE`)?
- [ ] ¿Se usa `@Slf4j` para trazas de log importantes en lugar de `System.out`?
- [ ] ¿Se respetaron las rutas de los paquetes (dominio, aplicación, infraestructura)?

## 4. Formato de Salida Estricto
Aplica el formato de `CHANGE START/END` (si es requerido por tu IDE/Agent). Entrega código de producción final, limpio y sin comentarios residuales (cero `// TODO: implementar`). Usa sintaxis de Java 25 (Pattern matching, Text Blocks).