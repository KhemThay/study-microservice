package mptc.khay.domain.entity;

import mptc.khay.domain.valueobject.CustomerId;

public class Customer extends AggregateRoot<CustomerId> {

    private final String userName;
    private final String givenName;
    private final String familyName;

    public String getUserName() {
        return userName;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    private Customer(Builder builder) {
        super.setId(builder.id);
        userName = builder.userName;
        givenName = builder.givenName;
        familyName = builder.familyName;
    }


    public static final class Builder {
        private CustomerId id;
        private String userName;
        private String givenName;
        private String familyName;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder id(CustomerId val) {
            id = val;
            return this;
        }

        public Builder userName(String val) {
            userName = val;
            return this;
        }

        public Builder givenName(String val) {
            givenName = val;
            return this;
        }

        public Builder familyName(String val) {
            familyName = val;
            return this;
        }

        public Customer build() {
            return new Customer(this);
        }
    }
}
