package wonit.hackathon.a1.domain.user.service;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import wonit.hackathon.a1.domain.user.dto.response.UserResponse;
import wonit.hackathon.a1.domain.user.entity.User;
import wonit.hackathon.a1.domain.user.repository.UserRepository;
import wonit.hackathon.a1.global.code.GeneralErrorCode;
import wonit.hackathon.a1.global.exception.GeneralException;

@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** 로그인 ID로 조회 — 다른 사람의 정보는 '없음'으로 처리 */
    public UserResponse getUser(
            @NotBlank(message = "사번은 필수입니다.") String loginId,
            Long requesterId) {
        User user = findUser(loginId);

        if (!user.getId().equals(requesterId)) {
            throw new GeneralException(GeneralErrorCode.USER_NOT_FOUND);
        }

        return UserResponse.from(user);
    }

    /** 로그인한 본인 정보 */
    public UserResponse getMe(Long fcId) {
        User user = userRepository.findById(fcId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.USER_NOT_FOUND));

        return UserResponse.from(user);
    }

    private User findUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.USER_NOT_FOUND));
    }
}