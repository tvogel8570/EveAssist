package com.eveassist.client.pilot.entity;

import com.eveassist.client.user.entity.EveAssistUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "pilot", uniqueConstraints = {@UniqueConstraint(
        name = "pilot_business_key", columnNames = {"owner_hash", "eve_pilot_id"})})
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
    private LocalDate birthday;

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
    private Instant modified;
    @Column(name = "created")
    @CreatedDate
    private Instant created;

    @OneToMany(mappedBy = "pilot", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Token> token = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eve_assist_user_id", nullable = false)
    private EveAssistUser eveAssistUser;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ?
                ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ?
                ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Pilot pilot = (Pilot) o;
        return getId() != null && Objects.equals(getId(), pilot.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ?
                ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() :
                getClass().hashCode();
    }

    @SuppressWarnings("unused")
    public static final class PilotBuilder {
        private Long id;
        private Integer version;
        private String ownerHash;
        private Long evePilotId;
        private String name;
        private String gender;
        private LocalDate birthday;
        private String portraitUrlTiny;
        private String portraitUrlSmall;
        private String portraitUrlMedium;
        private String portraitUrlLarge;
        private @Min(-10)
        @Max(10) Double securityStatus;
        private String title;
        private String description;
        private Integer allianceId;
        private String allianceDesc;
        private Integer factionId;
        private String factionDesc;
        private Integer corporationId;
        private String corporationDesc;
        private Integer raceId;
        private String raceDesc;
        private Integer bloodlineId;
        private String bloodlineDesc;
        private Instant modified;
        private Instant created;
        private Set<Token> token;
        private EveAssistUser eveAssistUser;

        private PilotBuilder() {
        }

        public static PilotBuilder aPilot() {
            return new PilotBuilder();
        }

        public PilotBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public PilotBuilder withVersion(Integer version) {
            this.version = version;
            return this;
        }

        public PilotBuilder withOwnerHash(String ownerHash) {
            this.ownerHash = ownerHash;
            return this;
        }

        public PilotBuilder withEvePilotId(Long evePilotId) {
            this.evePilotId = evePilotId;
            return this;
        }

        public PilotBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public PilotBuilder withGender(String gender) {
            this.gender = gender;
            return this;
        }

        public PilotBuilder withBirthday(LocalDate birthday) {
            this.birthday = birthday;
            return this;
        }

        public PilotBuilder withPortraitUrlTiny(String portraitUrlTiny) {
            this.portraitUrlTiny = portraitUrlTiny;
            return this;
        }

        public PilotBuilder withPortraitUrlSmall(String portraitUrlSmall) {
            this.portraitUrlSmall = portraitUrlSmall;
            return this;
        }

        public PilotBuilder withPortraitUrlMedium(String portraitUrlMedium) {
            this.portraitUrlMedium = portraitUrlMedium;
            return this;
        }

        public PilotBuilder withPortraitUrlLarge(String portraitUrlLarge) {
            this.portraitUrlLarge = portraitUrlLarge;
            return this;
        }

        public PilotBuilder withSecurityStatus(Double securityStatus) {
            this.securityStatus = securityStatus;
            return this;
        }

        public PilotBuilder withTitle(String title) {
            this.title = title;
            return this;
        }

        public PilotBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public PilotBuilder withAllianceId(Integer allianceId) {
            this.allianceId = allianceId;
            return this;
        }

        public PilotBuilder withAllianceDesc(String allianceDesc) {
            this.allianceDesc = allianceDesc;
            return this;
        }

        public PilotBuilder withFactionId(Integer factionId) {
            this.factionId = factionId;
            return this;
        }

        public PilotBuilder withFactionDesc(String factionDesc) {
            this.factionDesc = factionDesc;
            return this;
        }

        public PilotBuilder withCorporationId(Integer corporationId) {
            this.corporationId = corporationId;
            return this;
        }

        public PilotBuilder withCorporationDesc(String corporationDesc) {
            this.corporationDesc = corporationDesc;
            return this;
        }

        public PilotBuilder withRaceId(Integer raceId) {
            this.raceId = raceId;
            return this;
        }

        public PilotBuilder withRaceDesc(String raceDesc) {
            this.raceDesc = raceDesc;
            return this;
        }

        public PilotBuilder withBloodlineId(Integer bloodlineId) {
            this.bloodlineId = bloodlineId;
            return this;
        }

        public PilotBuilder withBloodlineDesc(String bloodlineDesc) {
            this.bloodlineDesc = bloodlineDesc;
            return this;
        }

        public PilotBuilder withModified(Instant modified) {
            this.modified = modified;
            return this;
        }

        public PilotBuilder withCreated(Instant created) {
            this.created = created;
            return this;
        }

        public PilotBuilder withToken(Set<Token> token) {
            this.token = token;
            return this;
        }

        public PilotBuilder withEveAssistUser(EveAssistUser eveAssistUser) {
            this.eveAssistUser = eveAssistUser;
            return this;
        }

        public Pilot build() {
            Pilot pilot = new Pilot();
            pilot.setId(id);
            pilot.setVersion(version);
            pilot.setOwnerHash(ownerHash);
            pilot.setEvePilotId(evePilotId);
            pilot.setName(name);
            pilot.setGender(gender);
            pilot.setBirthday(birthday);
            pilot.setPortraitUrlTiny(portraitUrlTiny);
            pilot.setPortraitUrlSmall(portraitUrlSmall);
            pilot.setPortraitUrlMedium(portraitUrlMedium);
            pilot.setPortraitUrlLarge(portraitUrlLarge);
            pilot.setSecurityStatus(securityStatus);
            pilot.setTitle(title);
            pilot.setDescription(description);
            pilot.setAllianceId(allianceId);
            pilot.setAllianceDesc(allianceDesc);
            pilot.setFactionId(factionId);
            pilot.setFactionDesc(factionDesc);
            pilot.setCorporationId(corporationId);
            pilot.setCorporationDesc(corporationDesc);
            pilot.setRaceId(raceId);
            pilot.setRaceDesc(raceDesc);
            pilot.setBloodlineId(bloodlineId);
            pilot.setBloodlineDesc(bloodlineDesc);
            pilot.setModified(modified);
            pilot.setCreated(created);
            pilot.setToken(token);
            pilot.setEveAssistUser(eveAssistUser);
            return pilot;
        }
    }
}