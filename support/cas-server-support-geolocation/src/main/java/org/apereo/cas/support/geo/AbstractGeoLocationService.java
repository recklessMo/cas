package org.apereo.cas.support.geo;

import org.apereo.cas.authentication.adaptive.geo.GeoLocationRequest;
import org.apereo.cas.authentication.adaptive.geo.GeoLocationResponse;
import org.apereo.cas.authentication.adaptive.geo.GeoLocationService;
import org.apereo.cas.util.HttpUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.userinfo.client.UserInfo;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * This is {@link AbstractGeoLocationService}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Slf4j
@Setter
@Getter
public abstract class AbstractGeoLocationService implements GeoLocationService {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private String ipStackAccessKey;

    @Override
    public GeoLocationResponse locate(final String clientIp, final GeoLocationRequest location) {
        LOGGER.debug("Attempting to find geolocation for [{}]", clientIp);
        val loc = locate(clientIp);

        if (loc == null && location != null) {
            LOGGER.debug("Attempting to find geolocation for [{}]", location);
            if (StringUtils.isNotBlank(location.getLatitude()) && StringUtils.isNotBlank(location.getLongitude())) {
                return locate(Double.valueOf(location.getLatitude()), Double.valueOf(location.getLongitude()));
            }
        }
        return loc;
    }

    @Override
    @SneakyThrows
    public GeoLocationResponse locate(final String address) {
        try {
            val info = UserInfo.getInfo(address);
            if (info != null && info.getPosition() != null) {
                return locate(info.getPosition().getLatitude(), info.getPosition().getLongitude());
            }
            return null;
        } catch (final Exception e) {
            if (StringUtils.isNotBlank(ipStackAccessKey)) {
                val url = String.format("http://api.ipstack.com/%s?access_key=%s", address, ipStackAccessKey);
                HttpResponse response = null;
                try {
                    response = HttpUtils.executeGet(url);
                    if (response != null && response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        val result = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                        val infos = MAPPER.readValue(result, Map.class);
                        val geoResponse = new GeoLocationResponse();
                        geoResponse.setLatitude((double) infos.getOrDefault("latitude", 0D));
                        geoResponse.setLongitude((double) infos.getOrDefault("longitude", 0D));
                        geoResponse
                                .addAddress((String) infos.getOrDefault("city", StringUtils.EMPTY))
                                .addAddress((String) infos.getOrDefault("region_name", StringUtils.EMPTY))
                                .addAddress((String) infos.getOrDefault("region_code", StringUtils.EMPTY))
                                .addAddress((String) infos.getOrDefault("county_name", StringUtils.EMPTY));
                        return geoResponse;
                    }
                } finally {
                    HttpUtils.close(response);
                }
            }
        }
        return null;
    }
}
