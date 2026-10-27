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

    public UserResponse getUser(
            @NotBlank(message = "사번은 필수입니다.")
            String loginId
    ){
        User user = findUser(loginId);

        return UserResponse.from(user);
    }

    private User findUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.USER_NOT_FOUND
                ));
    }
}
