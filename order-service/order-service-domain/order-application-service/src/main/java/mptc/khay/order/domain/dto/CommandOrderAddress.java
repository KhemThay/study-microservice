package mptc.khay.order.domain.dto;

public record CommandOrderAddress (
        String street,
        String postalCode,
        String city
){
}
