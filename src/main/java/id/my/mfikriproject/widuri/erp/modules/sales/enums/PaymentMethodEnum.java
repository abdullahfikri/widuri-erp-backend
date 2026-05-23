package id.my.mfikriproject.widuri.erp.modules.sales.enums;

public enum PaymentMethodEnum {
    CASH("Cash"),
    QRIS("QRIS"),
    TRANSFER("Transfer");

    private final String dbValue;

    private PaymentMethodEnum(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static PaymentMethodEnum fromDbValue(String dbValue) {
        for (PaymentMethodEnum paymentMethod : PaymentMethodEnum.values()) {
            if (paymentMethod.getDbValue().equals(dbValue)) return paymentMethod;
        }

        throw new IllegalArgumentException("Unknown payment method stored in database");
    }
}
