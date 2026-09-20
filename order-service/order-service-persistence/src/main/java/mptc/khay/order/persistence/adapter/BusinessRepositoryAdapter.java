package mptc.khay.order.persistence.adapter;

import lombok.RequiredArgsConstructor;
import mptc.khay.domain.entity.Business;
import mptc.khay.order.domain.port.output.BusinessRepository;
import mptc.khay.order.persistence.entity.BusinessEntity;
import mptc.khay.order.persistence.mapper.OrderPersistenceMapper;
import mptc.khay.order.persistence.repository.BusinessJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BusinessRepositoryAdapter implements BusinessRepository {

    private final BusinessJpaRepository businessJpaRepository;
    private final OrderPersistenceMapper orderPersistenceMapper;

    @Override
    public Optional<Business> findBusiness(UUID businessId, List<UUID> productIds) {
        List<BusinessEntity> businessEntities =
                businessJpaRepository.findByBusinessIdAndProductIdIn(businessId, productIds);

        if (businessEntities.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(orderPersistenceMapper.businessEntitiesToBusiness(businessEntities));
    }
}
