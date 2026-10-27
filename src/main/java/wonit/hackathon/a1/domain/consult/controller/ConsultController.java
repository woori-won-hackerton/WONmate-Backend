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
import wonit.hackathon.a1.domain.consult.dto.request.ConsultCompleteRequest;
import wonit.hackathon.a1.domain.consult.dto.request.ConsultCreateRequest;
import wonit.hackathon.a1.domain.consult.dto.request.ConsultMemoUpdateRequest;
import wonit.hackathon.a1.domain.consult.dto.response.BriefingResponse;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultAiRecord;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultMemoResponse;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultResponse;
import wonit.hackathon.a1.domain.consult.enums.ConsultationStatus;
import wonit.hackathon.a1.domain.consult.service.*;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;
import wonit.hackathon.a1.global.security.CustomUserDetails;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Consult", description = "상담 일정 생성 및 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/consults")
public class ConsultController {

        private final ConsultService consultService;
        private final ConsultBriefingService consultBriefingService;
        private final ConsultMemoService consultMemoService;
        private final ConsultCompleteService consultCompleteService;
        private final ConsultRecordService consultRecordService;

        @PostMapping
        @Operation(summary = "상담 일정 생성", description = "고객, 상담 종류, 예약 일시를 입력하여 상담 일정을 생성합니다.")
        public ApiResponse<ConsultResponse> createConsult(
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,

                        @Valid @RequestBody ConsultCreateRequest request) {
                Long userId = Long.valueOf(jwt.getSubject());

                ConsultResponse response = consultService.createConsult(userId, request);

                return ApiResponse.onSuccess(
                                "상담 일정 생성에 성공했습니다.",
                                response);
        }

        @GetMapping
        @Operation(summary = "상담 일정 리스트 조회", description = "로그인한 FC의 상담 일정을 조회합니다. "
                        + "date(특정 날짜) 또는 from · to(기간, 양 끝 포함, 최대 92일) 중 하나를 입력합니다. "
                        + "status를 넣으면 해당 상태만 조회합니다.")
        public ApiResponse<List<ConsultResponse>> getConsults(
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,

                        @Parameter(description = "조회할 날짜", example = "2026-10-27") @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,

                        @Parameter(description = "기간 시작일", example = "2026-10-25") @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

                        @Parameter(description = "기간 종료일 (포함)", example = "2026-11-10") @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

                        @Parameter(description = "상담 상태 필터", example = "completed") @RequestParam(name = "status", required = false) ConsultationStatus status) {
                Long userId = Long.valueOf(jwt.getSubject());

                List<ConsultResponse> response = consultService.getConsults(userId, date, from, to, status);

                return ApiResponse.onSuccess(
                                "상담 일정 리스트 조회에 성공했습니다.",
                                response);
        }

        @GetMapping("/records")
        @Operation(summary = "완료된 상담 목록 조회", description = "완료된 상담을 최근 순으로 최대 200건 조회합니다. customerId를 넣으면 해당 고객만 조회합니다. "
                        + "aiRecordStatus로 AI 상담 기록 진행 상태(NONE · DRAFT · CONFIRMED)를 확인할 수 있습니다.")
        public ApiResponse<List<ConsultResponse>> getCompletedConsults(
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,

                        @Parameter(description = "고객 ID (생략하면 전체)", example = "1") @RequestParam(name = "customerId", required = false) Long customerId) {
                Long userId = Long.valueOf(jwt.getSubject());

                return ApiResponse.onSuccess(
                                "완료된 상담 목록 조회에 성공했습니다.",
                                consultService.getCompletedConsults(userId, customerId));
        }

        @GetMapping("/{consultId}")
        @Operation(summary = "상담 일정 단건 조회", description = "로그인한 FC의 상담 일정 상세 정보를 조회합니다.")
        public ApiResponse<ConsultResponse> getConsult(
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,

                        @Parameter(description = "상담 ID", example = "1", required = true) @PathVariable(name = "consultId") Long consultId) {
                Long userId = Long.valueOf(jwt.getSubject());

                ConsultResponse response = consultService.getConsult(
                                userId,
                                consultId);

                return ApiResponse.onSuccess(
                                "상담 일정 조회에 성공했습니다.",
                                response);
        }

        // 현재 유지중인 계약 조회

        // 상담 준비 브리핑 생성 - 이전 상담 바탕으로

        @Operation(summary = "상담 요약 브리핑 생성", description = "이전 상담 내용을 바탕으로 상담 요약 브리핑을 생성해줍니다.")
        @PostMapping("/{consultationId}/briefing")
        public BriefingAiResult generateBriefing(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable Long consultationId) {
                Long fcId = Long.valueOf(jwt.getSubject());

                return consultBriefingService.generateBriefing(
                                fcId,
                                consultationId);
        }

        @Operation(summary = "저장된 브리핑 조회", description = "마지막으로 생성한 브리핑을 조회합니다. 아직 생성하지 않았으면 404입니다.")
        @GetMapping("/{consultationId}/briefing")
        public ApiResponse<BriefingResponse> getBriefing(
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                        @PathVariable Long consultationId) {
                Long fcId = Long.valueOf(jwt.getSubject());

                return ApiResponse.onSuccess(
                                "브리핑 조회에 성공했습니다.",
                                consultBriefingService.getSavedBriefing(fcId, consultationId));
        }

        @Operation(summary = "상담 메모 조회", description = "작성한 상담 메모를 조회합니다.")
        @GetMapping("/{consultationId}/memo")
        public ApiResponse<ConsultMemoResponse> getMemo(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable Long consultationId) {
                Long userId = Long.valueOf(jwt.getSubject());

                return ApiResponse.onSuccess(
                                "상담 메모 조회에 성공했습니다.",
                                consultMemoService.getMemo(userId, consultationId));
        }

        @Operation(summary = "상담 메모 작성 및 수정", description = "상담 메모를 처음 저장하거나 기존 내용을 수정합니다.")
        @PatchMapping("/{consultationId}/memo")
        public ApiResponse<ConsultMemoResponse> updateMemo(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable Long consultationId,
                        @Valid @RequestBody ConsultMemoUpdateRequest request) {
                Long userId = Long.valueOf(jwt.getSubject());

                return ApiResponse.onSuccess(
                                "상담 메모 저장에 성공했습니다.",
                                consultMemoService.updateMemo(
                                                userId,
                                                consultationId,
                                                request));
        }

        @Operation(summary = "상담 완료", description = "최종 메모를 저장하고 상담 상태를 completed로 변경합니다.")
        @PostMapping("/{consultationId}/complete")
        public ApiResponse<Void> completeConsultation(
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,

                        @Parameter(description = "상담 ID", example = "1", required = true) @PathVariable("consultationId") Long consultationId,

                        @Valid @RequestBody ConsultCompleteRequest request) {
                Long fcId = Long.valueOf(jwt.getSubject());

                consultCompleteService.complete(fcId, consultationId, request);

                return ApiResponse.<Void>onSuccess(
                                "상담 완료 처리에 성공했습니다.",
                                null);
        }

        // AI

        @Operation(summary = "AI 상담 기록 초안 생성", description = "완료된 상담의 저장된 메모로 초안을 생성합니다. 기존 기록이 있으면 반환합니다.")
        @PostMapping("/{consultationId}/ai-record")
        public ApiResponse<ConsultAiRecord> generateAiRecord(
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                        @PathVariable("consultationId") Long consultationId) {
                Long fcId = Long.valueOf(jwt.getSubject());

                return ApiResponse.onSuccess(
                                "AI 상담 기록 초안이 준비되었습니다.",
                                consultRecordService.generate(fcId, consultationId));
        }

        @Operation(summary = "AI 상담 기록 조회")
        @GetMapping("/{consultationId}/ai-record")
        public ApiResponse<ConsultAiRecord> getAiRecord(
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                        @PathVariable("consultationId") Long consultationId) {
                Long fcId = Long.valueOf(jwt.getSubject());

                return ApiResponse.onSuccess(
                                "AI 상담 기록 조회에 성공했습니다.",
                                consultRecordService.get(fcId, consultationId));
        }

        @Operation(summary = "AI 상담 기록 수정", description = "FC가 고친 상담 기록을 저장합니다. 확정된 기록은 수정할 수 없습니다(409).")
        @PatchMapping("/{consultationId}/ai-record")
        public ApiResponse<ConsultAiRecord> updateAiRecord(
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                        @PathVariable("consultationId") Long consultationId,
                        @Valid @RequestBody ConsultAiRecord request) {
                Long fcId = Long.valueOf(jwt.getSubject());

                return ApiResponse.onSuccess(
                                "AI 상담 기록을 저장했습니다.",
                                consultRecordService.update(fcId, consultationId, request));
        }

        @Operation(summary = "AI 상담 기록 확정", description = "상담 기록을 확정합니다. 확정 후에는 수정할 수 없습니다. 이미 확정했으면 그대로 반환합니다.")
        @PostMapping("/{consultationId}/ai-record/confirm")
        public ApiResponse<ConsultAiRecord> confirmAiRecord(
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                        @PathVariable("consultationId") Long consultationId) {
                Long fcId = Long.valueOf(jwt.getSubject());

                return ApiResponse.onSuccess(
                                "AI 상담 기록을 확정했습니다.",
                                consultRecordService.confirm(fcId, consultationId));
        }
}