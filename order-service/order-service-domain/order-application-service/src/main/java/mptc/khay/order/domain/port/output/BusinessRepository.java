package mptc.khay.order.domain.port.output;

import mptc.khay.domain.entity.Business;

import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository {

    Optional<Business> findBusiness(UUID businessId);
}
