package com.eveassist.client.pilot.impl;

import java.util.Arrays;
import java.util.Objects;

public record PilotAuthPayload(
        String[] scp,
        String jti,
        String kid,
        String sub,
        String azp,
        String tenant,
        String tier,
        String region,
        String[] aud,
        String name,
        String owner,
        Long exp,
        Long iat,
        String iss
) {

    @Override
    public String toString() {
        return "PilotAuthPayload{" +
                "scp=" + Arrays.toString(scp) +
                ", jti='" + jti + '\'' +
                ", kid='" + kid + '\'' +
                ", sub='" + sub + '\'' +
                ", azp='" + azp + '\'' +
                ", tenant='" + tenant + '\'' +
                ", tier='" + tier + '\'' +
                ", region='" + region + '\'' +
                ", aud=" + Arrays.toString(aud) +
                ", name='" + name + '\'' +
                ", owner='" + owner + '\'' +
                ", exp=" + exp +
                ", iat=" + iat +
                ", iss='" + iss + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PilotAuthPayload that)) return false;
        return Objects.equals(exp, that.exp) &&
                Objects.equals(iat, that.iat) &&
                Objects.equals(jti, that.jti) &&
                Objects.equals(kid, that.kid) &&
                Objects.equals(sub, that.sub) &&
                Objects.equals(azp, that.azp) &&
                Objects.equals(iss, that.iss) &&
                Objects.equals(tier, that.tier) &&
                Objects.equals(name, that.name) &&
                Objects.deepEquals(scp, that.scp) &&
                Objects.deepEquals(aud, that.aud) &&
                Objects.equals(owner, that.owner) &&
                Objects.equals(tenant, that.tenant) &&
                Objects.equals(region, that.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Arrays.hashCode(scp),
                jti,
                kid,
                sub,
                azp,
                tenant,
                tier,
                region,
                Arrays.hashCode(aud),
                name,
                owner,
                exp,
                iat,
                iss);
    }

    // "sub":"CHARACTER:EVE:458639815"
    String pilotId() {
        String[] split = sub().split(":");
        return split[2];
    }
}
