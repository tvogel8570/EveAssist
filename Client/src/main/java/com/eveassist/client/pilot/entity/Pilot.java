package com.eveassist.client.pilot.entity;

import jakarta.persistence.*;
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
@Table(name = "pilot", uniqueConstraints = {@UniqueConstraint(columnNames = {"owner_hash", "pilot_id"})})
public class Pilot implements Serializable {
    @Serial
    private static final long serialVersionUID = 8223189717393563426L;
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pilot_seq")
    @SequenceGenerator(name = "pilot_seq", allocationSize = 1, initialValue = 100)
    @Column(name = "id", nullable = false)
    private Long id;
    @Version
    private Integer version;

    @Column(name = "owner_hash", nullable = false)
    String ownerHash;
    @Column(name = "pilot_id", nullable = false)
    Long pilotId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "portrait_url_tiny")
    private String portraitUrlTiny;
    @Column(name = "portrait_url_small")
    private String portraitUrlSmall;
    @Column(name = "portrait_url_medium")
    private String portraitUrlMedium;
    @Column(name = "portrait_url_large")
    private String portraitUrlLarge;

    @Column(name = "corporation_id")
    private String corporationId;
    @Column(name = "alliance_id")
    private String allianceId;
    @Column(name = "faction_id")
    private String factionId;

    @Column(name = "gender", length = 15)
    private String gender;
    @Column(name = "birthdate")
    private LocalDateTime birthdate;

    @Column(name = "modified")
    @LastModifiedDate
    private LocalDateTime modified;
    @Column(name = "created")
    @CreatedDate
    private LocalDateTime created;

    @OneToMany(mappedBy = "pilot", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Token> token = new LinkedHashSet<>();

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