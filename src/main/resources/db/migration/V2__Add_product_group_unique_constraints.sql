ALTER TABLE m_product_group
    ADD CONSTRAINT uq_product_group_name_brand UNIQUE (name, brand);

CREATE UNIQUE INDEX uq_product_group_name_null_brand
    ON m_product_group (name)
    WHERE brand IS NULL;
