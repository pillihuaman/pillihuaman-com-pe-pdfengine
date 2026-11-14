package pillihuaman.com.pe.engine.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserMeasurementMetadata implements Serializable {
    private Double heightCm;
    private Double weightKg;
    private String gender;
    private String fitPreference;
    private CustomFormFieldsDto customFormFields;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomFormFieldsDto implements Serializable {
        private String selectedSize;
        private MathMetricsDto frontMath;
        private MathMetricsDto sideMath;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MathMetricsDto implements Serializable {
        private Double imageWidth;
        private Double imageHeight;
        private Double bodyHeightPixels;
        private Double shouldersWidthPixels;
        private Double chestWidthPixels;
        private Double waistWidthPixels;
        private Double hipsWidthPixels;
        private Double chestDepthPixels;
        private Double waistDepthPixels;
        private Double hipsDepthPixels;
        private Boolean segmentationUsed;
        private Double confidenceScore;
        private List<BodyLandmarkDto> landmarks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BodyLandmarkDto implements Serializable {
        private String part;
        private Double x;
        private Double y;
        private Double z;
        private Double visibility;
    }
}