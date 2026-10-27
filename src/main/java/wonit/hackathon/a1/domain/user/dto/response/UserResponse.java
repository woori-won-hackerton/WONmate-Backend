package wonit.hackathon.a1.domain.user.dto.response;

import wonit.hackathon.a1.domain.user.entity.User;

public record UserResponse(
        long fcId,
        String loginId,
        String email,
        String fullName,
        Short joinedYear,
        String branchName
){
    public static UserResponse from(User user){
        return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getEmail(),
                user.getFullName(),
                user.getJoinedYear(),
                user.getBranchName()
        );
    }
}

