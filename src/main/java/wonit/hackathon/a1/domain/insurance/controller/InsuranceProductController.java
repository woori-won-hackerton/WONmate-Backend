package wonit.hackathon.a1.domain.insurance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wonit.hackathon.a1.domain.insurance.dto.response.InsuranceProductResponse;
import wonit.hackathon.a1.domain.insurance.service.InsuranceProductService;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
@Tag(name = "보험 상품 API")
public class InsuranceProductController {
    private final InsuranceProductService insuranceProductService;

    @GetMapping
    @Operation(summary = "상품 선택 목록 조회", description = "DB에 저장된 모든 상품의 ID, 상품명, 대분류 코드와 이름을 상품명, ID 오름차순으로 반환합니다. 대분류는 WHOLE_LIFE(종신보험), CANCER(암보험), CHILD(어린이보험), UNKNOWN(미분류)입니다. 상품이 없으면 빈 목록을 반환합니다.")
    public ApiResponse<List<InsuranceProductResponse>> getProducts() {
        return ApiResponse.onSuccess("상품 목록 조회에 성공했습니다.", insuranceProductService.getProducts());
    }
}
