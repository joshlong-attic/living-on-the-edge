package greetings;

import com.google.common.util.concurrent.RateLimiter;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

//import org.springframework.security.oauth2.client.OAuth2ClientContext;
//import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
// https://jfconavarrete.wordpress.com/2014/09/15/make-spring-security-context-available-inside-a-hystrix-command/


@EnableDiscoveryClient
@SpringBootApplication
public class GreetingsClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(GreetingsClientApplication.class, args);
    }
}

@Profile("secure")
@Configuration
@EnableResourceServer
@EnableOAuth2Client
class OAuthResourceConfiguration {
}

@EnableZuulProxy
@Configuration
class ZuulConfiguration {

    @Bean
    CommandLineRunner commandLineRunner(RouteLocator routeLocator) {
        return args -> routeLocator.getRoutes().forEach(r -> LogFactory.getLog(getClass()).info(r.toString()));
    }

    @Bean
    RateLimiter rateLimiter() {
        return RateLimiter.create(1.0D / 10.0D);
    }
}

@Component
class ThrottlingZuulFilter extends ZuulFilter {

    private final HttpStatus tooManyRequests = HttpStatus.TOO_MANY_REQUESTS;

    private final RateLimiter rateLimiter;

    @Autowired
    public ThrottlingZuulFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        try {
            RequestContext currentContext = RequestContext.getCurrentContext();
            HttpServletResponse response = currentContext.getResponse();
            if (!rateLimiter.tryAcquire()) {
                response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                response.setStatus(this.tooManyRequests.value());
                response.getWriter().append(this.tooManyRequests.getReasonPhrase());
                currentContext.setSendZuulResponse(false);
                throw new ZuulException(this.tooManyRequests.getReasonPhrase(),
                        this.tooManyRequests.value(),
                        this.tooManyRequests.getReasonPhrase());
            }
        } catch (Exception e) {
            ReflectionUtils.rethrowRuntimeException(e);
        }
        return null;
    }
}

@Configuration
@EnableFeignClients
class FeignConfiguration {

}

@RestController
class GreetingsClientApiGateway {

    private final GreetingsClient greetingsClient;
    private final RestTemplate restTemplate;

    @Autowired
    GreetingsClientApiGateway(GreetingsClient greetingsClient, RestTemplate restTemplate) {
        this.greetingsClient = greetingsClient;
        this.restTemplate = restTemplate;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/feign/{name}")
    Map<String, String> feign(@PathVariable String name) {
        return this.greetingsClient.greet(name);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/resttemplate/{name}")
    Map<String, String> restTemplate(@PathVariable String name) {

        ParameterizedTypeReference<Map<String, String>> type =
            new ParameterizedTypeReference<Map<String, String>>() { };

        return this.restTemplate.exchange(
                "http://greetings-service/greet/{name}", HttpMethod.GET, null, type , name)
                .getBody();
    }
}

@FeignClient(serviceId = "greetings-service")
interface GreetingsClient {

    @RequestMapping(method = RequestMethod.GET, value = "/greet/{name}")
    Map<String, String> greet(@PathVariable("name") String name);
}
