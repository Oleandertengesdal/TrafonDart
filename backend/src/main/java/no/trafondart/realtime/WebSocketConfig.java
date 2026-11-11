package no.trafondart.realtime;

import no.trafondart.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * STOMP over plain WebSocket at {@code /ws}. The in-memory simple broker is plenty for one instance.
 *
 * <p>The socket is read-only for clients: they authenticate with a short-lived ticket in the CONNECT frame and
 * may only SUBSCRIBE to {@code /topic/**}. All writes go through the REST API (CSRF-protected, validated).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties props;
    private final StompAuthInterceptor authInterceptor;
    private final JsonMapper jsonMapper;

    public WebSocketConfig(AppProperties props, StompAuthInterceptor authInterceptor, JsonMapper jsonMapper) {
        this.props = props;
        this.authInterceptor = authInterceptor;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> converters) {
        converters.add(new JsonStompMessageConverter(jsonMapper));
        return false;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(props.websocket().allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeat = new ThreadPoolTaskScheduler();
        heartbeat.setPoolSize(1);
        heartbeat.setThreadNamePrefix("ws-heartbeat-");
        heartbeat.initialize();
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeat);
        // No application destinations: clients never SEND.
        registry.setApplicationDestinationPrefixes("/app-disabled");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
