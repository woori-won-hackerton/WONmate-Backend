package wonit.hackathon.a1.domain.consult.controller;

import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingAiResult;
import wonit.hackathon.a1.domain.consult.dto.request.ConsultCreateRequest;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultResponse;
import wonit.hackathon.a1.domain.consult.service.ConsultBriefingService;
import wonit.hackathon.a1.domain.consult.service.ConsultService;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;
import wonit.hackathon.a1.global.security.CustomUserDetails;

import java.time.LocalDate;
import java.util.List;


@Tag(
        name = "Consult",
        description = "상담 일정 생성 및 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/consults")
public class ConsultController {


        private final ConsultService consultService;
        private final ConsultBriefingService consultBriefingService;

    @PostMapping
    @Operation(
            summary = "상담 일정 생성",
            description = "고객, 상담 종류, 예약 일시를 입력하여 상담 일정을 생성합니다."
    )
    public ApiResponse<ConsultResponse> createConsult(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Valid @RequestBody ConsultCreateRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        ConsultResponse response =
                consultService.createConsult(userId, request);

        return ApiResponse.onSuccess(
                "상담 일정 생성에 성공했습니다.",
                response
        );
    }
    @GetMapping
    @Operation(
            summary = "상담 일정 리스트 조회",
            description = "로그인한 FC의 특정 날짜 상담 일정을 조회합니다."
    )
    public ApiResponse<List<ConsultResponse>> getConsults(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(
                    description = "조회할 날짜",
                    example = "2026-10-27",
                    required = true
            )
            @RequestParam(name = "date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        List<ConsultResponse> response =
                consultService.getConsults(userId, date);

        return ApiResponse.onSuccess(
                "상담 일정 리스트 조회에 성공했습니다.",
                response
        );
    }

    @GetMapping("/{consultId}")
    @Operation(
            summary = "상담 일정 단건 조회",
            description = "로그인한 FC의 상담 일정 상세 정보를 조회합니다."
    )
    public ApiResponse<ConsultResponse> getConsult(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(
                    description = "상담 ID",
                    example = "1",
                    required = true
            )
            @PathVariable(name = "consultId")
            Long consultId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        ConsultResponse response =
                consultService.getConsult(
                        userId,
                        consultId
                );

        return ApiResponse.onSuccess(
                "상담 일정 조회에 성공했습니다.",
                response
        );
    }

            //현재 유지중인 계약 조회


            //상담 준비 브리핑 생성 - 이전 상담 바탕으로


    @Operation(
            summary = "상담 요약 브리핑 생성",
            description = "이전 상담 내용을 바탕으로 상담 요약 브리핑을 생성해줍니다."
    )
    @PostMapping("/{consultationId}/briefing")
    public BriefingAiResult generateBriefing(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long consultationId
    ) {
        Long fcId = Long.valueOf(jwt.getSubject());

        return consultBriefingService.generateBriefing(
                fcId,
                consultationId
        );
    }
        }



