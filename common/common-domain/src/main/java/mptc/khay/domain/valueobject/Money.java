package mptc.khay.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount) {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public boolean isGreaterThanZero(){
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isGreaterThan(Money money){
        return amount.compareTo(money.amount) >0;
    }

    public Money add(Money money){
        return new Money(setScale(this.amount.add(money.amount)));
    }

    public Money subtract(Money money){
        return new Money(setScale(this.amount.subtract(money.amount)));
    }

    public Money multiply(int multiplier){
        return new Money(setScale(this.amount.multiply(BigDecimal.valueOf(multiplier))));
    }

    private BigDecimal setScale(BigDecimal inputAmount){
        return inputAmount.setScale(2, RoundingMode.HALF_EVEN);
    }

}


