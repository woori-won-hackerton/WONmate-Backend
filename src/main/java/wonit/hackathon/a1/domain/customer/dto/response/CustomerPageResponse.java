package wonit.hackathon.a1.domain.customer.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record CustomerPageResponse(
        List<CustomerResponse> customers,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
){
    public static CustomerPageResponse from(
            Page<CustomerResponse> result
    ){
        return new CustomerPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }
}
