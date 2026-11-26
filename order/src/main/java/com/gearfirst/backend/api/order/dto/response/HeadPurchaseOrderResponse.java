package com.gearfirst.backend.api.order.dto.response;

import com.gearfirst.backend.api.order.entity.PurchaseOrder;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeadPurchaseOrderResponse {
    private Long orderId;
    private String orderNumber;
    private String orderStatus;
    private String branchCode;
    private String engineerName;
    private String engineerRole;
    private LocalDateTime requestDate;
    private LocalDateTime processedDate;

    public static HeadPurchaseOrderResponse from(PurchaseOrder order) {
        return HeadPurchaseOrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus().name())
                .branchCode(order.getRequesterCode())
                .engineerName(order.getRequesterName())
                .engineerRole(order.getRequesterRole())
                .requestDate(order.getRequestDate())
                .processedDate(order.getProcessedDate())
                .build();
    }
}
