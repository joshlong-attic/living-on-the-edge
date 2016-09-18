package relay;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoRestTemplateFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.filter.OAuth2ClientContextFilter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;

// todo: https://jfconavarrete.wordpress.com/2014/09/15/make-spring-security-context-available-inside-a-hystrix-command/

// this works because we added @EnableOAuth2Client to the the services
@Configuration
@Profile("secure")
@ConditionalOnWebApplication
@ConditionalOnBean(OAuth2ClientContextFilter.class)
@ConditionalOnClass(EnableResourceServer.class)
public class TokenRelayAutoConfiguration {

    @Bean
    @LoadBalanced
    OAuth2RestTemplate restTemplate(UserInfoRestTemplateFactory factory) {
        return factory.getUserInfoRestTemplate();
    }

    @Configuration
    @ConditionalOnClass(RequestInterceptor.class)
    public static class FeignAutoConfiguration {

        @Bean
        RequestInterceptor requestInterceptor(OAuth2ClientContext clientContext) {
            return requestTemplate ->
                    requestTemplate.header(HttpHeaders.AUTHORIZATION,
                            clientContext.getAccessToken().getTokenType() +
                                    ' ' + clientContext.getAccessToken().getValue());
        }
    }
}
