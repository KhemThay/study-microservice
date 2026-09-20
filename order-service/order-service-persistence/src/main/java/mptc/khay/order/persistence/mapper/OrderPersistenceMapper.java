package mptc.khay.order.persistence.mapper;

import mptc.khay.domain.entity.Business;
import mptc.khay.domain.entity.Customer;
import mptc.khay.domain.entity.Product;
import mptc.khay.domain.valueobject.BusinessId;
import mptc.khay.order.persistence.entity.BusinessEntity;
import mptc.khay.order.persistence.entity.CustomerEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderPersistenceMapper {

    @Mapping(source = "id", target = "id.value")
    Customer customerEntityToCustomer(CustomerEntity customerEntity);

    @Mapping(source = "productId", target = "id.value")
    @Mapping(source = "productName", target = "name")
    @Mapping(source = "productPrice", target = "price.amount")
    Product businessEntityToProduct(BusinessEntity businessEntity);

    default Business businessEntitiesToBusiness(List<BusinessEntity> businessEntities) {
        BusinessEntity businessEntity = businessEntities.getFirst();
        return Business.builder()
                .id(new BusinessId(businessEntity.getBusinessId()))
                .active(Boolean.TRUE.equals(businessEntity.getBusinessActive()))
                .products(businessEntities.stream()
                        .map(this::businessEntityToProduct)
                        .toList())
                .build();
    }
}
