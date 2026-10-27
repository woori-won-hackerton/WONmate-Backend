package wonit.hackathon.a1.domain.customerwiki.entity;

import wonit.hackathon.a1.domain.customer.entity.Customer;
import wonit.hackathon.a1.global.common.BaseEntity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "customer_wikis")
public class CustomerWiki extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wiki_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    private CustomerWiki(
            Customer customer,
            String content
    ) {
        this.customer = Objects.requireNonNull(customer, "customer");
        this.content = validateAndNormalize(content);
    }

    public static CustomerWiki create(
            Customer customer,
            String content
    ) {
        return new CustomerWiki(
                customer,
                content
        );
    }

    public void appendContent(String additionalContent){
        String newContent = validateAndNormalize(additionalContent);

        this.content=this.content.isBlank()
                ? newContent
                : this.content + "\n\n" + newContent;
    }

    private static String validateAndNormalize(String content){
        Objects.requireNonNull(content, "content");

        String normalized = content.strip();

        if (normalized.isBlank()){
            throw new IllegalArgumentException(
                    "추가할 내용은 비어 있을 수 없습니다."
            );
        }

        return normalized;
    }
}
