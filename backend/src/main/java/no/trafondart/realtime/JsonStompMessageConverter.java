package no.trafondart.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeType;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Serialises STOMP payloads with the same Jackson {@link JsonMapper} Spring Boot uses for the REST API,
 * so dates, enums and nulls look identical on both channels.
 */
public class JsonStompMessageConverter extends AbstractMessageConverter {

    private final JsonMapper mapper;

    public JsonStompMessageConverter(JsonMapper mapper) {
        super(new MimeType("application", "json", StandardCharsets.UTF_8));
        this.mapper = mapper;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    protected boolean canConvertFrom(Message<?> message, Class<?> targetClass) {
        return false; // the socket is read-only; we never deserialise client payloads
    }

    @Override
    protected Object convertToInternal(Object payload, MessageHeaders headers, Object conversionHint) {
        return mapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
    }
}
