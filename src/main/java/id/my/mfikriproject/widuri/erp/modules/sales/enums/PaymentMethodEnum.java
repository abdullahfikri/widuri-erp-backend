package id.my.mfikriproject.widuri.erp.modules.sales.enums;

public enum PaymentMethod {
    CASH("Cash"),
    QRIS("QRIS"),
    TRANSFER("Transfer"),;

    private final String dbValue;

    private PaymentMethod(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static PaymentMethod fromDbValue(String dbValue) {
        for (PaymentMethod paymentMethod : PaymentMethod.values()) {
            if (paymentMethod.getDbValue().equals(dbValue)) return paymentMethod;
        }

        throw new IllegalArgumentException("Unknown payment method: " + dbValue);
    }
}
