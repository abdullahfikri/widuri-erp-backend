package id.my.mfikriproject.widuri.erp.modules.sales.converter;

import id.my.mfikriproject.widuri.erp.modules.sales.enums.PaymentMethodEnum;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PaymentMethodConverter implements AttributeConverter<PaymentMethodEnum, String> {
    @Override
    public String convertToDatabaseColumn(PaymentMethodEnum attribute) {
        if (attribute == null) return null;
        return attribute.getDbValue();
    }

    @Override
    public PaymentMethodEnum convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return PaymentMethodEnum.fromDbValue(dbData);
    }
}
