package com.gearfirst.backend.api.order.dto.request;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseOrderRequest {
    private String vehicleNumber;
    private String vehicleModel;
    private String receiptNum;
    private List<OrderItemRequest> items;
}
