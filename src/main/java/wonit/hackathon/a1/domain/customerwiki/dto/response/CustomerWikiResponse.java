package wonit.hackathon.a1.domain.customerwiki.dto.response;

import wonit.hackathon.a1.domain.customerwiki.entity.CustomerWiki;


public record CustomerWikiResponse(
        Long wikiId,
        Long customerId,
        String content
) {

    public static CustomerWikiResponse from(CustomerWiki wiki) {
        return new CustomerWikiResponse(
                wiki.getId(),
                wiki.getCustomer().getId(),
                wiki.getContent()
        );
    }

    public static CustomerWikiResponse empty(Long customerId) {
        return new CustomerWikiResponse(
                null,
                customerId,
                null
        );
    }
}