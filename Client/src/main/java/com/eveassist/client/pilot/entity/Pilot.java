package com.eveassist.client.pilot.entity;

import com.eveassist.client.user.entity.EveAssistUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "pilot", uniqueConstraints = {@UniqueConstraint(name = "pilot_business_key", columnNames = {"owner_hash"
        , "eve_pilot_id"})})
public class Pilot implements Serializable {
    @Serial
    private static final long serialVersionUID = 8223189717393563426L;
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pilot_id_seq")
    @SequenceGenerator(name = "pilot_id_seq", allocationSize = 1, initialValue = 100)
    @Column(name = "id", nullable = false)
    private Long id;
    @Version
    private Integer version;

    @Column(name = "owner_hash", nullable = false, unique = true)
    String ownerHash;
    @Column(name = "eve_pilot_id", nullable = false)
    Long evePilotId;

    @Column(name = "name", nullable = false, unique = true)
    private String name;
    @Column(name = "gender")
    private String gender;
    @Column(name = "birthdate")
    private LocalDateTime birthday;

    @Column(name = "portrait_url_tiny")
    private String portraitUrlTiny;
    @Column(name = "portrait_url_small")
    private String portraitUrlSmall;
    @Column(name = "portrait_url_medium")
    private String portraitUrlMedium;
    @Column(name = "portrait_url_large")
    private String portraitUrlLarge;

    @Min(-10)
    @Max(10)
    @Column(name = "security_status")
    private Double securityStatus;
    @Column(name = "title")
    private String title;
    @Column(name = "description")
    private String description;

    @Column(name = "alliance_id")
    private Integer allianceId;
    @Column(name = "alliance_desc")
    private String allianceDesc;
    @Column(name = "faction_id")
    private Integer factionId;
    @Column(name = "faction_desc")
    private String factionDesc;
    @Column(name = "corporation_id")
    private Integer corporationId;
    @Column(name = "corporation_desc")
    private String corporationDesc;
    @Column(name = "race_id")
    private Integer raceId;
    @Column(name = "race_desc")
    private String raceDesc;
    @Column(name = "bloodline_id")
    private Integer bloodlineId;
    @Column(name = "bloodline_desc")
    private String bloodlineDesc;

    @Column(name = "modified")
    @LastModifiedDate
    private LocalDateTime modified;
    @Column(name = "created")
    @CreatedDate
    private LocalDateTime created;

    @OneToMany(mappedBy = "pilot", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Token> token = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eve_assist_user_id")
    private EveAssistUser eveAssistUser;


    @SuppressWarnings("java:S2097")
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy otherProxy ?
                otherProxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy thisProxy ?
                thisProxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Pilot pilot = (Pilot) o;
        return getId() != null && Objects.equals(getId(), pilot.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}