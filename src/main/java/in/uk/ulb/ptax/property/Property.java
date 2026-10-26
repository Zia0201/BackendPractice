package in.uk.ulb.ptax.property;

import in.uk.ulb.ptax.ulb.Ulb;
import in.uk.ulb.ptax.ulb.Ward;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** state-district-ulb-ward-serial, e.g. UK-DDN-VKN-03-00002 */
    private String propertyNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ulb_id")
    private Ulb ulb;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id")
    private Ward ward;

    private int serialNo;
    private String khasraNo;

    @Enumerated(EnumType.STRING)
    private PropertyCategory category;

    private String ownerName;
    private String ownerMobile;
    private String ownerEmail;
    private String address;

    private BigDecimal plotArea;
    private BigDecimal coveredArea;
    private BigDecimal carpetArea;
    private Integer constructionYear;

    private LocalDate lastMutationDate;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public static String buildPropertyNo(Ulb ulb, Ward ward, int serial) {
        return String.format("UK-%s-%s-%02d-%05d", ulb.getDistrictCode(), ulb.getCode(), ward.getWardNo(), serial);
    }
}
