package mptc.khay.domain.valueobject;

import java.util.UUID;

public record StreetAddress(
        UUID id,
        String postalcode,
        String street,
        String city
) {
}
