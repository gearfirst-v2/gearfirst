package com.gearfirst.user_be.user.client;

import com.gearfirst.user_be.user.dto.CreateAccountRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// signup, delete는 서비스 토큰(scope internal)이 있어야 불린다 (GEA-13)
@FeignClient(name = "auth-service", url = "${auth-service.url}", configuration = AuthClientConfig.class)
public interface AuthClient {

    @PostMapping("/api/v1/auth/signup")
    String createAccount(@RequestBody CreateAccountRequest request);

    @PostMapping("/api/v1/auth/delete")
    void deleteUser(@RequestBody Long userId);
}
