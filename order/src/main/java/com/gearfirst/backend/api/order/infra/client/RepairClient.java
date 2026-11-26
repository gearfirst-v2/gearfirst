package com.gearfirst.backend.api.order.infra.client;

import com.gearfirst.backend.api.order.infra.dto.ReceiptCarResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "gearfirst-repair", url = "http://localhost:8082")
public interface RepairClient {
    @GetMapping("/api/v1/repairs/{engineerId}")
    List<ReceiptCarResponse> getAllRepairsByEngineer(@PathVariable Long engineerId);

    @GetMapping("/api/v1/repairs/search/{engineerId}")
    List<ReceiptCarResponse> searchRepairsByEngineer(@PathVariable Long engineerId, @RequestParam String keyword);
}
