package com.eveassist.api.sde.chr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
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
@Table(name = "\"chrAttributes\"", schema = "evesde")
public class ChrAttribute implements Serializable {
    private static final long serialVersionUID = -4714571540812408300L;
    @Id
    @Column(name = "\"attributeID\"", nullable = false)
    private Integer id;

    @Size(max = 100)
    @Column(name = "\"attributeName\"", length = 100)
    private String attributeName;

    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "\"iconID\"")
    private Integer iconID;

    @Size(max = 500)
    @Column(name = "\"shortDescription\"", length = 500)
    private String shortDescription;

    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;

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
        ChrAttribute that = (ChrAttribute) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}