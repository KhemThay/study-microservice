package mptc.khay.domain.exception;

public class OrderDomainException extends DomainException{


    public OrderDomainException(String message, Throwable cause) {
        super(message, cause);
    }

    public OrderDomainException(String message) {
        super(message);
    }
}
