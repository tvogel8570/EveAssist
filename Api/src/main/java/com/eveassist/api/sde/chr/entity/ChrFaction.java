package com.eveassist.api.sde.chr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serializable;
import java.util.Objects;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "\"chrFactions\"", schema = "evesde")
public class ChrFaction implements Serializable {
    private static final long serialVersionUID = -3862478133895991321L;
    @Id
    @Column(name = "\"factionID\"", nullable = false)
    private Integer id;

    @jakarta.validation.constraints.Size(max = 100)
    @Column(name = "\"factionName\"", length = 100)
    private String factionName;

    @jakarta.validation.constraints.Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "\"raceIDs\"")
    private Integer raceIDs;

    @Column(name = "\"solarSystemID\"")
    private Integer solarSystemID;

    @Column(name = "\"corporationID\"")
    private Integer corporationID;

    @Column(name = "\"sizeFactor\"")
    private Double sizeFactor;

    @Column(name = "\"stationCount\"")
    private Integer stationCount;

    @Column(name = "\"stationSystemCount\"")
    private Integer stationSystemCount;

    @Column(name = "\"militiaCorporationID\"")
    private Integer militiaCorporationID;

    @Column(name = "\"iconID\"")
    private Integer iconID;

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
        ChrFaction that = (ChrFaction) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}