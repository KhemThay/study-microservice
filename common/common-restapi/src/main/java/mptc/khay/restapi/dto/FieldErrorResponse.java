package mptc.khay.restapi.dto;

public record FieldErrorResponse(
        String field,
        String code,
        String reason
) {
}
