package pillihuaman.com.pe.pdfengine.infrastructure.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ServerWebInputException;
import pillihuaman.com.pe.pdfengine.domain.exception.BusinessLogicException;
import pillihuaman.com.pe.pdfengine.domain.exception.ResourceNotFoundException;
import pillihuaman.com.pe.pdfengine.infrastructure.common.RespBase;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Log4j2
@RestControllerAdvice
public class ReactiveGlobalExceptionHandler {

    private static final String VALIDATION_CODE = "VALIDATION_ERROR";
    private static final String INTERNAL_CODE = "INTERNAL_SERVER_ERROR";

    @ExceptionHandler({BusinessLogicException.class, ResourceNotFoundException.class})
    public ResponseEntity<RespBase<Object>> handleBusinessException(RuntimeException ex) {

        HttpStatus status = (ex instanceof ResourceNotFoundException) ? HttpStatus.NOT_FOUND : HttpStatus.UNPROCESSABLE_ENTITY;

        String code = (ex instanceof ResourceNotFoundException) ? "RESOURCE_NOT_FOUND" : "BUSINESS_RULE_VIOLATION";

        return buildErrorResponse(status, code, List.of(ex.getMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<RespBase<Object>> handleValidationException(WebExchangeBindException ex) {

        List<String> errors = ex.getBindingResult().getFieldErrors().stream().map(error -> "Field '" + error.getField() + "' " + error.getDefaultMessage()).collect(Collectors.toList());

        return buildErrorResponse(HttpStatus.BAD_REQUEST, VALIDATION_CODE, errors);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<RespBase<Object>> handleWebInputException(ServerWebInputException ex) {

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", List.of("JSON inválido o request incorrecto"));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespBase<Object>> handleGenericException(Exception ex) {

        String traceId = UUID.randomUUID().toString();

        log.error("[TraceID: {}] Unexpected System Error", traceId, ex);

        String message = "System error. TraceId: " + traceId;

        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_CODE, List.of(message), traceId);
    }

    private ResponseEntity<RespBase<Object>> buildErrorResponse(HttpStatus status, String code, List<String> messages) {
        return buildErrorResponse(status, code, messages, UUID.randomUUID().toString());
    }

    private ResponseEntity<RespBase<Object>> buildErrorResponse(HttpStatus status, String code, List<String> messages, String traceId) {

        RespBase<Object> response = new RespBase<>();

        RespBase.Status respStatus = new RespBase.Status();
        RespBase.Status.Error error = new RespBase.Status.Error();

        error.setCode(code);
        error.setHttpCode(String.valueOf(status.value()));
        error.setMessages(messages);

        respStatus.setSuccess(false);
        respStatus.setError(error);

        response.setStatus(respStatus);
        response.setTrace(new RespBase.Trace(traceId));
        response.setPayload(null);

        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(NotAcceptableStatusException.class)
    public ResponseEntity<RespBase<Object>> handleNotAcceptableException(NotAcceptableStatusException ex) {
        log.error("Content Negotiation Failure: Accept header not compatible.");
        return buildErrorResponse(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", List.of("Accept header must be application/pdf"));
    }
}