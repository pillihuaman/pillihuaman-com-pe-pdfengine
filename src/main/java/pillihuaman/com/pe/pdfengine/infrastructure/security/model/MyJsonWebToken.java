package pillihuaman.com.pe.pdfengine.infrastructure.security.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyJsonWebToken implements Serializable {

    private static final long serialVersionUID = 1L;

    private ResponseUser user;
    private Application application;
    private String tenantId;
    private List<String> permissions;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Application {
        private ObjectId aplicationID;
        private String name;
        private String multiSession;
    }
}
