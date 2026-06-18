package pillihuaman.com.pe.engine.infrastructure.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
public class RespBase<T> {

    private Trace trace;
    private Status status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T payload;

    public RespBase(T payload) {
        this.payload = payload;
    }

    public Trace getTrace() {
        return trace;
    }

    public void setTrace(Trace trace) {
        this.trace = trace;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    public RespBase() {
        super();
        trace = new Trace();
        status = new Status();
    }

    public RespBase<T> ok(T payload) {
        RespBase<T> response = new RespBase<>();
        response.setPayload(payload);
        response.getStatus().setSuccess(Boolean.TRUE);
        return response;
    }

    /**
     * Subclase plantilla para trazabilidad
     */
    @Builder
    @AllArgsConstructor
    public static class Trace {
        private String traceId;

        public Trace() {
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }
    }

    /**
     * Subclase plantilla para status
     */
    @Builder
    @AllArgsConstructor
    public static class Status {
        private Boolean success;
        private Error error;

        public Status() {
        }

        public Boolean getSuccess() {
            return success;
        }

        public void setSuccess(Boolean success) {
            this.success = success;
        }

        public Error getError() {
            return error;
        }

        public void setError(Error error) {
            this.error = error;
        }

        /**
         * Subclase plantilla para error
         */
        @Builder
        @AllArgsConstructor
        @Data
        public static class Error {
            private String code;
            private String httpCode;
            private List<String> messages;

            public Error() {
                super();
                messages = new ArrayList<>();
            }
        }
    }
}
