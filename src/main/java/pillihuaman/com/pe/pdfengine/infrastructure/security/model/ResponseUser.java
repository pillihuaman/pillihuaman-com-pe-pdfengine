package pillihuaman.com.pe.pdfengine.infrastructure.security.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.util.List;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResponseUser {

    private ObjectId id;
    private String alias;
    private ObjectId idSystem;
    private String mail;
    private String mobilPhone;
    private String user;
    private String username;
    private int enabled;
    private List<String> roles;
    private String tenantId;
}
