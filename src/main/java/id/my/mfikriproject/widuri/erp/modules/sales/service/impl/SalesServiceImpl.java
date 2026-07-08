package id.my.mfikriproject.widuri.erp.modules.sales.service.impl;

import id.my.mfikriproject.widuri.erp.core.context.StoreContext;
import id.my.mfikriproject.widuri.erp.core.entity.StoreModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductCostSnapshot;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.StockAdjustRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.ProductService;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.StockAdjustmentService;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutDetailRequest;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutItemResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutRequest;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.entity.SalesDetailModel;
import id.my.mfikriproject.widuri.erp.modules.sales.entity.SalesModel;
import id.my.mfikriproject.widuri.erp.modules.sales.repository.SalesDetailRepository;
import id.my.mfikriproject.widuri.erp.modules.sales.repository.SalesRepository;
import id.my.mfikriproject.widuri.erp.modules.sales.service.InvoiceNumberGenerator;
import id.my.mfikriproject.widuri.erp.modules.sales.service.SalesService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SalesServiceImpl implements SalesService {
    private final ProductService productService;
    private final StockAdjustmentService stockAdjustmentService;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final SalesRepository salesRepository;
    private final SalesDetailRepository salesDetailRepository;
    private final EntityManager entityManager;

    public SalesServiceImpl(ProductService productService,
                            StockAdjustmentService stockAdjustmentService,
                            InvoiceNumberGenerator invoiceNumberGenerator,
                            SalesRepository salesRepository,
                            SalesDetailRepository salesDetailRepository,
                            EntityManager entityManager) {
        this.productService = productService;
        this.stockAdjustmentService = stockAdjustmentService;
        this.invoiceNumberGenerator = invoiceNumberGenerator;
        this.salesRepository = salesRepository;
        this.salesDetailRepository = salesDetailRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request) {
        StoreContext.assertBound();

        if (request.details() == null || request.details().isEmpty()) {
            throw new IllegalArgumentException("checkout requires at least one item");
        }

        Integer storeId = StoreContext.STORE_ID.get();
        OffsetDateTime transactionDate = OffsetDateTime.now();
        String invoiceNumber = invoiceNumberGenerator.generate(storeId, transactionDate.toLocalDate());

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PreparedDetail> preparedDetails = new ArrayList<>();
        List<CheckoutItemResponse> itemResponseList = new ArrayList<>();

        for (CheckoutDetailRequest detailRequest : request.details()) {
            // Defense-in-depth: Bean Validation (@Min(1)) catches this at controller,
            // but guard at service level before any DB call.
            if (detailRequest.quantity() <= 0) {
                throw new IllegalArgumentException(
                        "Quantity must be positive, got: " + detailRequest.quantity());
            }

            ProductCostSnapshot snapshot = productService.getCostSnapshot(detailRequest.productId());

            // Sales rule: sold price must never fall below the product's floor price.
            if (detailRequest.soldPrice().compareTo(snapshot.floorPrice()) < 0) {
                throw new IllegalArgumentException("Sold price " + detailRequest.soldPrice()
                        + " is below floor price " + snapshot.floorPrice());
            }

            // Decrease stock (adjustOut locks the product row with PESSIMISTIC_WRITE).
            stockAdjustmentService.adjustOut(detailRequest.productId(),
                    new StockAdjustRequest(detailRequest.quantity(), "sold"));

            BigDecimal subtotal = detailRequest.soldPrice()
                    .multiply(BigDecimal.valueOf(detailRequest.quantity()));
            totalAmount = totalAmount.add(subtotal);

            preparedDetails.add(new PreparedDetail(detailRequest, snapshot.basePrice(), subtotal));
            itemResponseList.add(new CheckoutItemResponse(
                    detailRequest.productId(),
                    detailRequest.quantity(),
                    detailRequest.soldPrice(),
                    subtotal));
        }

        SalesModel sales = salesRepository.save(SalesModel.builder()
                .storeModel(entityManager.getReference(StoreModel.class, storeId))
                .invoiceNumber(invoiceNumber)
                .totalAmount(totalAmount)
                .paymentMethod(request.paymentMethod())
                .transactionDate(transactionDate)
                .build());

        for (PreparedDetail prepared : preparedDetails) {
            CheckoutDetailRequest detailRequest = prepared.request();
            salesDetailRepository.save(SalesDetailModel.builder()
                    .salesModel(sales)
                    .productModel(entityManager.getReference(ProductModel.class, detailRequest.productId()))
                    .quantity(detailRequest.quantity())
                    .costPriceAtTime(prepared.costPrice())
                    .soldPriceAtTime(detailRequest.soldPrice())
                    .subtotal(prepared.subtotal())
                    .build());
        }

        return CheckoutResponse.builder()
                .invoiceNumber(invoiceNumber)
                .transactionDate(transactionDate)
                .paymentMethod(request.paymentMethod())
                .totalAmount(totalAmount)
                .items(itemResponseList)
                .build();
    }

    // Holds per-line data computed during validation so details are persisted only after
    // the parent SalesModel (with the final total) has an id.
    private record PreparedDetail(CheckoutDetailRequest request, BigDecimal costPrice, BigDecimal subtotal) {
    }
}
