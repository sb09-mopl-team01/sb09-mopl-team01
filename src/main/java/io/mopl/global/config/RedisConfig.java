package io.mopl.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

@Configuration
public class RedisConfig {

  @Value("${spring.data.redis.host}")
  private String host;

  @Value("${spring.data.redis.port}")
  private int port;

  @Value("${spring.data.redis.password}")
  private String password;

  @Value("${spring.data.redis.ssl.enabled:false}")
  private boolean sslEnabled;

  @Bean
  public RedisConnectionFactory redisConnectionFactory() {
    var redisStandaloneConfiguration = new RedisStandaloneConfiguration(host, port);
    if (StringUtils.hasText(password)) {
      redisStandaloneConfiguration.setPassword(password);
    }

    var clientConfiguration = sslEnabled
        ? LettuceClientConfiguration.builder().useSsl().build()
        : LettuceClientConfiguration.builder().build();

    return new LettuceConnectionFactory(redisStandaloneConfiguration, clientConfiguration);
  }

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

    return template;
  }
}
