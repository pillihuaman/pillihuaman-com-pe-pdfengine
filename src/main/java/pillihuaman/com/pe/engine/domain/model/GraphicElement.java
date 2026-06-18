package pillihuaman.com.pe.engine.domain.model;

import java.io.Serializable;

public record GraphicElement(
        String type,
        float x,
        float y,
        float width,
        float height,
        String strokeColor,
        String resourceReference


)
        implements Serializable {
}